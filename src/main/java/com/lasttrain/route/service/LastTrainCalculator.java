package com.lasttrain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasttrain.route.dto.RouteResponse;
import com.lasttrain.route.dto.TransferDto;
import com.lasttrain.route.external.OdsayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * ODsay 경로 JSON을 파싱해서 각 경로의 막차 탑승 마감 시각(departureDeadline)을 계산합니다.
 *
 * ── 핵심 알고리즘 ────────────────────────────────────────────────────────────
 * 경로 예시: [도보 5분] → [지하철 30분, 강남역 탑승] → [버스 15분]
 *
 *   지하철 막차 시각 (강남역): 23:11
 *   강남역까지 걸리는 시간:    5분 (도보)
 *   departureDeadline:        23:11 - 5분 = 23:06  ← 집에서 나서야 하는 시각
 *   canCatch:                 현재 시각이 23:06 이전이면 true
 * ────────────────────────────────────────────────────────────────────────────
 *
 * ODsay trafficType 값:
 *   1 = 지하철
 *   2 = 버스
 *   3 = 도보
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LastTrainCalculator {

    private final OdsayClient odsayClient;

    // Spring Boot가 자동 구성하는 ObjectMapper를 주입받습니다.
    // JSON 문자열을 JsonNode 트리로 파싱할 때 사용합니다.
    private final ObjectMapper objectMapper;

    /**
     * ODsay 경로 조회 JSON을 파싱해서 각 경로의 RouteItem을 반환합니다.
     *
     * 파싱 실패하거나 지하철 구간이 없는 경로는 결과에서 제외됩니다.
     *
     * @param routeJson ODsay searchPubTransPathT API 응답 JSON
     * @param now       현재 시각 (Asia/Seoul 기준)
     * @return 계산 완료된 경로 목록 (막차 정보를 얻지 못한 경로는 포함되지 않음)
     */
    public List<RouteResponse.RouteItem> calculate(String routeJson, LocalDateTime now) {
        List<RouteResponse.RouteItem> results = new ArrayList<>();

        try {
            // JSON 문자열을 트리 구조로 파싱합니다.
            // result.path[] : ODsay가 제안하는 경로 목록 (보통 최대 5개)
            JsonNode paths = objectMapper.readTree(routeJson)
                                        .path("result")
                                        .path("path");

            // 요일에 따라 ODsay dayType 코드를 결정합니다. (평일=1, 토=2, 일=3)
            String dayType = resolveDayType(now.getDayOfWeek());

            for (JsonNode path : paths) {
                try {
                    RouteResponse.RouteItem item = processPath(path, now, dayType);
                    if (item != null) {
                        results.add(item);
                    }
                } catch (Exception e) {
                    // 한 경로 파싱 실패가 전체 계산을 중단시키면 안 됩니다.
                    log.warn("[LastTrainCalculator] 경로 1건 파싱 실패, 스킵: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[LastTrainCalculator] routeJson 파싱 실패: {}", e.getMessage(), e);
        }

        return results;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private 처리 메서드
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * path 1건을 처리해서 RouteItem을 반환합니다.
     * 지하철 구간이 없거나 막차 시각을 얻지 못하면 null을 반환합니다.
     */
    private RouteResponse.RouteItem processPath(JsonNode path, LocalDateTime now, String dayType)
            throws Exception {

        JsonNode subPaths = path.path("subPath");

        // ── 1단계: subPath 순회해서 첫 번째 지하철 구간 탐색 ─────────────────────────
        //
        // priorSectionTime: 지하철 탑승 전까지 걸리는 누적 시간 (분)
        //   예) [도보 5분] → [버스 10분] → [지하철] → priorSectionTime = 15분
        int priorSectionTime = 0;
        boolean foundSubway  = false;
        LocalDateTime lastTrainTime = null;
        String lastTrainTimeStr     = null;
        List<TransferDto> transfers = new ArrayList<>();

        for (JsonNode subPath : subPaths) {
            int trafficType = subPath.path("trafficType").asInt();
            int sectionTime = subPath.path("sectionTime").asInt(0);

            // 도보(3): 이동 시간만 누적하고 환승 목록에는 포함하지 않습니다.
            if (trafficType == 3) {
                if (!foundSubway) {
                    priorSectionTime += sectionTime;
                }
                continue;
            }

            String startName = subPath.path("startName").asText();
            String endName   = subPath.path("endName").asText();
            String lineName  = extractLineName(subPath);

            // ── 지하철(1) ──────────────────────────────────────────────────────────────
            if (trafficType == 1) {
                if (!foundSubway) {
                    // 첫 번째 지하철 구간의 탑승역 ID로 막차 시간표를 조회합니다.
                    String startId = subPath.path("startID").asText();
                    String scheduleJson = odsayClient.searchSubwaySchedule(startId, dayType);
                    lastTrainTime = extractLastTrainTime(scheduleJson, now.toLocalDate());

                    if (lastTrainTime == null) {
                        log.warn("[LastTrainCalculator] 막차 시각 추출 실패, stationId={}", startId);
                        return null;
                    }

                    lastTrainTimeStr = formatTime(lastTrainTime);
                    foundSubway = true;
                }

                transfers.add(new TransferDto("SUBWAY", lineName, startName, endName, lastTrainTimeStr));
            }

            // ── 버스(2) ────────────────────────────────────────────────────────────────
            // 버스 막차 조회(searchBusLane)는 이 메서드 범위 밖입니다.
            // lastBoardTime은 null로 처리합니다.
            if (trafficType == 2) {
                if (!foundSubway) {
                    // 지하철 탑승 전 버스 구간도 이동 시간 누적
                    priorSectionTime += sectionTime;
                }
                transfers.add(new TransferDto("BUS", lineName, startName, endName, null));
            }
        }

        // 지하철 구간이 없는 경로(버스만인 경우 등)는 처리하지 않습니다.
        if (!foundSubway || lastTrainTime == null) {
            return null;
        }

        // ── 2단계: departureDeadline 계산 ─────────────────────────────────────────────
        //
        // 막차 탑승 마감 시각 = 지하철 막차 시각 - 집에서 지하철 탑승역까지 걸리는 시간
        LocalDateTime departureDeadline = lastTrainTime.minusMinutes(priorSectionTime);
        boolean canCatch  = departureDeadline.isAfter(now);
        int minutesLeft   = (int) Math.max(0, ChronoUnit.MINUTES.between(now, departureDeadline));

        String message = canCatch
                ? "막차까지 " + minutesLeft + "분 남았어요!"
                : "이미 막차가 지났어요.";

        RouteResponse.CurrentStatus currentStatus =
                new RouteResponse.CurrentStatus(canCatch, minutesLeft, message);

        return new RouteResponse.RouteItem(formatTime(departureDeadline), currentStatus, transfers);
    }

    /**
     * ODsay subwayTimeTable(firstLastFlag=2) 응답에서 막차 시각을 추출합니다.
     *
     * ODsay 응답 구조 (lastRow 배열):
     *   result.lastRow[0].trainY = "2311"  (HHmm 형식, 24시간 초과 가능)
     *
     * @param scheduleJson ODsay subwayTimeTable 응답 JSON
     * @param baseDate     계산 기준 날짜 (자정 넘김 처리용)
     * @return 막차 LocalDateTime, 파싱 실패 시 null
     */
    private LocalDateTime extractLastTrainTime(String scheduleJson, LocalDate baseDate)
            throws Exception {

        JsonNode lastRow = objectMapper.readTree(scheduleJson)
                                       .path("result")
                                       .path("lastRow");

        if (!lastRow.isArray() || lastRow.isEmpty()) {
            return null;
        }

        // trainY: "HHmm" 형식의 시각 문자열 (예: "2311", "2410")
        String trainY = lastRow.get(0).path("trainY").asText();
        if (trainY.isBlank() || trainY.length() < 4) {
            return null;
        }

        return parseTrainY(trainY, baseDate);
    }

    /**
     * ODsay "HHmm" 형식의 시각 문자열을 LocalDateTime으로 변환합니다.
     *
     * 자정 넘김 처리:
     *   ODsay는 자정 이후 열차를 24시간 이상 표기합니다.
     *   예) "2410" → 다음날 00:10
     *       "2503" → 다음날 01:03
     */
    private LocalDateTime parseTrainY(String trainY, LocalDate baseDate) {
        int hour = Integer.parseInt(trainY.substring(0, 2));
        int min  = Integer.parseInt(trainY.substring(2, 4));

        if (hour >= 24) {
            // 자정 넘긴 막차: 기준 날짜의 다음날로 계산합니다.
            return baseDate.plusDays(1).atTime(hour - 24, min);
        }
        return baseDate.atTime(hour, min);
    }

    /**
     * subPath 노드에서 노선명을 추출합니다.
     *
     * ODsay 응답 구조: subPath.lane[0].name = "2호선"
     * lane 정보가 없으면 "알 수 없음"을 반환합니다.
     */
    private String extractLineName(JsonNode subPath) {
        JsonNode lane = subPath.path("lane");
        if (lane.isArray() && !lane.isEmpty()) {
            return lane.get(0).path("name").asText("알 수 없음");
        }
        return "알 수 없음";
    }

    /**
     * LocalDateTime을 "HH:mm" 형식의 문자열로 변환합니다.
     * 예) 2026-06-10T23:11 → "23:11"
     */
    private String formatTime(LocalDateTime dateTime) {
        return String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute());
    }

    /**
     * 요일을 ODsay dayType 코드로 변환합니다.
     *   평일     → "1"
     *   토요일   → "2"
     *   일요일   → "3"
     */
    private String resolveDayType(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SATURDAY -> "2";
            case SUNDAY   -> "3";
            default       -> "1";
        };
    }
}

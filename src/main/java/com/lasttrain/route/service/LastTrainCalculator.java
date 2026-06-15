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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

            log.debug("[DEBUG] paths 개수: {}", paths.size());

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

        log.debug("[DEBUG] path 처리 시작: pathType={}", path.path("pathType").asInt());

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
                    lastTrainTime = extractLastTrainTime(scheduleJson, now.toLocalDate(), dayType);

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
     * ODsay subwayTimeTable 응답에서 막차 시각을 추출합니다.
     *
     * ── ODsay 응답 구조 ────────────────────────────────────────────────────────
     * 요일 유형에 따라 아래 3개 목록 중 하나를 사용합니다.
     *   평일   → result.OrdList.down.time[]
     *   토요일 → result.SatList.down.time[]
     *   일요일 → result.SunList.down.time[]
     *
     * time[] 배열의 각 항목 구조 (시간대별 묶음):
     *   {"Idx": 23, "list": "10(온수) 18(석남) 28(온수) 38(석남) 47(온수) 57(석남)"}
     *     - Idx  : 시(hour) 값
     *     - list : 그 시간대에 출발하는 열차들의 "분(목적지)" 목록을 공백으로 나열한 문자열
     *
     * 막차 시각 = time[] 배열의 마지막 항목 (가장 늦은 시간대)의
     *            Idx(시) + list의 마지막 "분" 값
     * ────────────────────────────────────────────────────────────────────────────
     *
     * @param scheduleJson ODsay subwayTimeTable 응답 JSON
     * @param baseDate     계산 기준 날짜 (자정 넘김 처리용)
     * @param dayType      요일 구분 ("1"=평일, "2"=토요일, "3"=일요일)
     * @return 막차 LocalDateTime, 파싱 실패 시 null
     */
    private LocalDateTime extractLastTrainTime(String scheduleJson, LocalDate baseDate, String dayType)
            throws Exception {

        // dayType에 맞는 목록(OrdList/SatList/SunList)의 이름을 결정합니다.
        String listName = switch (dayType) {
            case "2" -> "SatList";
            case "3" -> "SunList";
            default  -> "OrdList";
        };

        JsonNode timeList = objectMapper.readTree(scheduleJson)
                                        .path("result")
                                        .path(listName)
                                        .path("down")
                                        .path("time");

        if (!timeList.isArray() || timeList.isEmpty()) {
            return null;
        }

        // 배열의 마지막 항목이 가장 늦은 시간대 = 막차가 포함된 시간대입니다.
        JsonNode lastTimeGroup = timeList.get(timeList.size() - 1);

        int hour = lastTimeGroup.path("Idx").asInt();
        String list = lastTimeGroup.path("list").asText();

        // list 문자열에서 마지막 "분" 값을 추출합니다.
        Integer minute = extractLastMinute(list);
        if (minute == null) {
            return null;
        }

        return buildLastTrainDateTime(hour, minute, baseDate);
    }

    /**
     * "분(역명) 분(역명) ..." 형식의 문자열에서 마지막 "분" 값을 추출합니다.
     *
     * 예) "10(온수) 18(석남) 28(온수) 38(석남) 47(온수) 57(석남)" → 57
     *
     * 정규식 "(\\d+)\\(" 으로 "숫자(" 패턴을 모두 찾고, 가장 마지막으로 찾은 숫자를 반환합니다.
     *
     * @return 마지막 "분" 값, list가 비어있거나 패턴이 없으면 null
     */
    private Integer extractLastMinute(String list) {
        if (list == null || list.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(\\d+)\\(").matcher(list);

        Integer lastMinute = null;
        while (matcher.find()) {
            lastMinute = Integer.parseInt(matcher.group(1));
        }

        return lastMinute;
    }

    /**
     * 막차의 시(hour)와 분(minute)을 LocalDateTime으로 변환합니다.
     *
     * 자정 넘김 처리:
     *   ODsay는 자정 이후 열차의 시(Idx)를 24 이상으로 표기합니다.
     *   예) Idx=24, minute=10 → 다음날 00:10
     *       Idx=25, minute=3  → 다음날 01:03
     */
    private LocalDateTime buildLastTrainDateTime(int hour, int minute, LocalDate baseDate) {
        if (hour >= 24) {
            // 자정 넘긴 막차: 기준 날짜의 다음날로 계산합니다.
            return baseDate.plusDays(1).atTime(hour - 24, minute);
        }
        return baseDate.atTime(hour, minute);
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

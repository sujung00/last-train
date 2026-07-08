package com.lasttrain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasttrain.route.dto.RouteResponse;
import com.lasttrain.route.dto.TransferDto;
import com.lasttrain.transit.service.TransitCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // 막차 시각을 조회하고 캐싱하는 서비스
    // 외부 API 호출 → DB 저장 → 다음 조회 시 캐시 사용
    private final TransitCacheService transitCacheService;

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
            // ── 1단계: ODsay 응답 JSON 원본 로깅 ───────────────────────────────────
            int jsonPreviewLength = Math.min(500, routeJson.length());
            String jsonPreview = routeJson.substring(0, jsonPreviewLength);
            log.info("[ODsay 응답] JSON 원본 ({}자 중 {}자): {}",
                    routeJson.length(), jsonPreviewLength, jsonPreview);

            // JSON 문자열을 트리 구조로 파싱합니다.
            // result.path[] : ODsay가 제안하는 경로 목록 (보통 최대 5개)
            JsonNode paths = objectMapper.readTree(routeJson)
                                        .path("result")
                                        .path("path");

            // ── 2단계: 파싱된 paths 개수 로깅 ─────────────────────────────────────
            log.info("[LastTrainCalculator] 파싱된 경로 개수: {}", paths.size());

            // 요일에 따라 ODsay dayType 코드를 결정합니다. (평일=1, 토=2, 일=3)
            String dayType = resolveDayType(now.getDayOfWeek());

            // ── 3단계: 각 경로 처리 및 예외 로깅 ────────────────────────────────────
            for (JsonNode path : paths) {
                try {
                    RouteResponse.RouteItem item = processPath(path, now, dayType);
                    if (item != null) {
                        results.add(item);
                    }
                } catch (Exception e) {
                    // 한 경로 파싱 실패가 전체 계산을 중단시키면 안 됩니다.
                    log.info("[LastTrainCalculator] 경로 처리 중 예외 발생: {}",
                            e.getClass().getSimpleName());
                    log.info("[LastTrainCalculator] 예외 메시지: {}", e.getMessage());
                    log.info("[LastTrainCalculator] 스택트레이스:", e);  // ← 스택트레이스 포함
                }
            }

        } catch (Exception e) {
            log.info("[LastTrainCalculator] routeJson 전체 파싱 실패: {}",
                    e.getClass().getSimpleName());
            log.info("[LastTrainCalculator] 예외 메시지: {}", e.getMessage());
            log.info("[LastTrainCalculator] 스택트레이스:", e);  // ← 스택트레이스 포함
        }

        return results;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private 처리 메서드
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * path 1건을 처리해서 RouteItem을 반환합니다.
     *
     * 알고리즘:
     *   1) 모든 대중교통(지하철/버스) 구간의 막차 시각을 candidateList에 수집
     *   2) candidateList에서 "가장 이른 막차"를 가진 구간을 병목으로 선택
     *      (경로 중 하나의 구간이라도 막차 시각이 빠르면 그것이 제약)
     *   3) departureDeadline = 병목의 막차 시각 - 병목 탑승 전까지의 시간
     *   4) transfers에는 모든 대중교통 구간 표시 (각각의 막차 시각)
     *
     * 막차 정보를 얻지 못하면 null을 반환합니다.
     */
    private RouteResponse.RouteItem processPath(JsonNode path, LocalDateTime now, String dayType)
            throws Exception {

        log.debug("[DEBUG] path 처리 시작: pathType={}", path.path("pathType").asInt());

        JsonNode subPaths = path.path("subPath");

        // ── 1단계: 모든 대중교통 구간의 막차 시각 수집 ────────────────────────────────
        //
        // 경로의 각 구간을 순회하면서 "지하철"과 "버스" 구간의 막차를 조회합니다.
        // 도보(trafficType=3)는 누적 이동 시간에만 영향을 미칩니다.
        //
        // candidateList: 막차 정보를 정상 조회한 구간들의 목록
        // candidateMap: subPathIndex → Candidate 매핑 (transfers 구성용)
        List<Candidate> candidateList = new ArrayList<>();
        Map<Integer, Candidate> candidateMap = new HashMap<>();
        int priorSectionTime = 0; // 누적 이동 시간(분)

        int subPathIndex = 0;
        for (JsonNode subPath : subPaths) {
            int trafficType = subPath.path("trafficType").asInt();
            int sectionTime = subPath.path("sectionTime").asInt(0);

            // 도보(3): 이동 시간만 누적합니다.
            if (trafficType == 3) {
                priorSectionTime += sectionTime;
                subPathIndex++;
                continue;
            }

            // 대중교통 구간: 막차 조회
            String type = null;
            if (trafficType == 1) {
                type = "SUBWAY";
            } else if (trafficType == 2) {
                type = "BUS";
            }

            log.debug("[ODsay subPath 분석] trafficType={}, type={}, startName={}, endName={}",
                    trafficType, type, subPath.path("startName").asText(), subPath.path("endName").asText());

            String lineName = extractLineName(subPath, trafficType);
            String startName = subPath.path("startName").asText();
            String endName = subPath.path("endName").asText();

            LocalDateTime lastTransitTime = null;

            if (trafficType == 1) {
                // ── 지하철(1) ──────────────────────────────────────────────────────────
                String startId = subPath.path("startID").asText();
                // TransitCacheService를 통해 막차 조회 (캐시 적용)
                // API 호출 + DB 저장 또는 DB에서 직접 조회
                String lastTimeStr = transitCacheService.getSubwayLastTime(startId, dayType);
                lastTransitTime = parseLastTime(lastTimeStr, now.toLocalDate());

            } else if (trafficType == 2) {
                // ── 버스(2) ────────────────────────────────────────────────────────────
                // busCityCode를 lane[0].busCityCode에서 추출 (ODsay 응답 구조)
                JsonNode lane = subPath.path("lane");
                int busCityCode = 0;
                if (lane.isArray() && !lane.isEmpty()) {
                    busCityCode = lane.get(0).path("busCityCode").asInt(0);
                }

                // busCityCode가 없으면 subPath 직접 경로에서 시도
                if (busCityCode == 0) {
                    busCityCode = subPath.path("busCityCode").asInt(0);
                }

                String busIdForLog = (lane.isArray() && !lane.isEmpty())
                    ? lane.get(0).path("busID").asText("")
                    : subPath.path("busID").asText("");
                log.debug("[LastTrainCalculator] 버스 구간: line={}, busCityCode={}, busID={}",
                    lineName, busCityCode, busIdForLog);

                // ⚠️  임시 로깅: ODsay 응답 구조 확인용 (busID 필드명 검증)
                // 실제 필드명이 busID, routeId, laneId 중 어느 것인지 확인하기 위함
                log.debug("[LastTrainCalculator] subPath 전체 JSON: {}", subPath.toString());

                if (busCityCode == 1000) {
                    // 서울시 버스 - TransitCacheService를 통해 조회 (캐시 적용)
                    // lane[0] 하위에서 필드 읽기 (null 체크 포함)
                    String stId = (lane.isArray() && !lane.isEmpty())
                        ? lane.get(0).path("localStationID").asText("")
                        : subPath.path("localStationID").asText("");
                    String busRouteId = (lane.isArray() && !lane.isEmpty())
                        ? lane.get(0).path("busLocalBlID").asText("")
                        : subPath.path("busLocalBlID").asText("");
                    String ord = (lane.isArray() && !lane.isEmpty())
                        ? lane.get(0).path("staOrder").asText("")
                        : subPath.path("staOrder").asText("");
                    if (!stId.isEmpty() && !busRouteId.isEmpty() && !ord.isEmpty()) {
                        String lastTimeStr = transitCacheService.getSeoulBusLastTime(stId, busRouteId, ord, dayType);
                        lastTransitTime = parseLastTime(lastTimeStr, now.toLocalDate());
                    }
                } else if (busCityCode == 1030 || busCityCode == 1040 || busCityCode == 1050 || busCityCode == 1140 || busCityCode == 1160 || busCityCode == 2000 || busCityCode == 3000) {
                    // 경기도/인천 버스 (1030: 마을, 1040: 마을, 1050: 시내, 1140: 직행좌석, 1160: 시내, 2000: 직행좌석, 3000: 인천) - TransitCacheService를 통해 조회 (캐시 적용)
                    // lane[0] 하위에서 필드 읽기 (null 체크 포함)
                    // 경기버스 API는 경기버스 로컬 ID(busLocalBlID)를 파라미터로 사용
                    String routeId = (lane.isArray() && !lane.isEmpty())
                        ? lane.get(0).path("busLocalBlID").asText("")
                        : subPath.path("busLocalBlID").asText("");
                    if (!routeId.isEmpty()) {
                        String lastTimeStr = transitCacheService.getGyeonggiBusLastTime(routeId, dayType);
                        lastTransitTime = parseLastTime(lastTimeStr, now.toLocalDate());
                    }
                } else if (busCityCode != 0) {
                    // 지원되지 않는 busCityCode는 경고 로그
                    String unsupportedBusId = (lane.isArray() && !lane.isEmpty())
                        ? lane.get(0).path("busID").asText("")
                        : subPath.path("busID").asText("");
                    log.warn("[LastTrainCalculator] 지원되지 않는 busCityCode: {} (line={}, busID={})",
                            busCityCode, lineName, unsupportedBusId);
                }
            }

            // 막차 조회 성공 시 candidateList에 추가
            if (lastTransitTime != null) {
                Candidate candidate = new Candidate(
                        subPathIndex,
                        priorSectionTime,
                        lastTransitTime,
                        lineName,
                        startName,
                        endName,
                        type
                );
                candidateList.add(candidate);
                candidateMap.put(subPathIndex, candidate);

                log.debug("[LastTrainCalculator] 막차 조회 성공: type={}, line={}, time={}, priorTime={}min",
                        type, lineName, formatTime(lastTransitTime), priorSectionTime);
            } else {
                // 막차 조회 실패: 해당 구간은 candidates에서 제외됩니다.
                log.warn("[LastTrainCalculator] 막차 시각 추출 실패: type={}, line={}", type, lineName);
            }

            // 다음 구간을 위해 현재 구간의 이동 시간 누적
            priorSectionTime += sectionTime;
            subPathIndex++;
        }

        // candidateList가 비어있으면 막차 정보를 얻을 수 없는 경로입니다.
        if (candidateList.isEmpty()) {
            log.warn("[LastTrainCalculator] 막차 정보를 얻을 수 없는 경로입니다.");
            return null;
        }

        // ── 2단계: candidateList에서 병목 찾기 ──────────────────────────────────────
        //
        // 병목: 가장 이른 막차 시각을 가진 구간
        //
        // 예시:
        //   구간1 지하철 23:30 (탑승전 10분)
        //   구간2 버스 23:15 (탑승전 0분)
        //   → 버스 23:15가 병목 (가장 먼저 떠나야 함)
        //
        Candidate bottleneck = candidateList.stream()
                .min((a, b) -> a.lastTransitTime.compareTo(b.lastTransitTime))
                .orElse(null);

        if (bottleneck == null) {
            return null;
        }

        log.debug("[LastTrainCalculator] 병목 구간: type={}, time={}, priorTime={}min",
                bottleneck.type, formatTime(bottleneck.lastTransitTime), bottleneck.priorSectionTime);

        // ── 3단계: departureDeadline 계산 ──────────────────────────────────────────
        //
        // 막차 탑승 마감 시각 = 병목의 막차 시각 - 병목 탑승 전까지 걸리는 시간
        //
        // 의미: 이 시각까지 출발해야만 병목 구간의 막차를 탈 수 있습니다.
        //
        LocalDateTime departureDeadline = bottleneck.lastTransitTime.minusMinutes(bottleneck.priorSectionTime);
        boolean canCatch = departureDeadline.isAfter(now);
        int minutesLeft = (int) Math.max(0, ChronoUnit.MINUTES.between(now, departureDeadline));

        String message = canCatch
                ? "막차까지 " + minutesLeft + "분 남았어요!"
                : "이미 막차가 지났어요.";

        RouteResponse.CurrentStatus currentStatus =
                new RouteResponse.CurrentStatus(canCatch, minutesLeft, message);

        // ── 4단계: transfers 구성 ──────────────────────────────────────────────────
        //
        // 전체 subPath를 순회하면서 모든 대중교통 구간을 transfers에 추가합니다.
        // 각 구간의 막차 시각은 위에서 조회한 값을 사용합니다.
        //
        List<TransferDto> transfers = new ArrayList<>();
        subPathIndex = 0;

        for (JsonNode subPath : subPaths) {
            int trafficType = subPath.path("trafficType").asInt();

            // 도보(3)는 transfers에 포함하지 않습니다.
            if (trafficType == 3) {
                subPathIndex++;
                continue;
            }

            String lineName = extractLineName(subPath, trafficType);
            String startName = subPath.path("startName").asText();
            String endName = subPath.path("endName").asText();
            String type = trafficType == 1 ? "SUBWAY" : "BUS";

            // candidateMap에서 해당 구간의 막차 시각 찾기
            // (막차 조회 실패한 구간은 null이 됨)
            String lastTransitTimeStr = null;
            Candidate candidate = candidateMap.get(subPathIndex);
            if (candidate != null) {
                lastTransitTimeStr = formatTime(candidate.lastTransitTime);
            }

            transfers.add(new TransferDto(type, lineName, startName, endName, lastTransitTimeStr));
            subPathIndex++;
        }

        return new RouteResponse.RouteItem(formatTime(departureDeadline), currentStatus, transfers);
    }

    /**
     * "HH:mm" 형식의 막차 시각 문자열을 LocalDateTime으로 변환합니다.
     *
     * 용도:
     *   - TransitCacheService에서 받은 "HH:mm" 문자열을 LocalDateTime으로 변환
     *   - 자정 넘김 처리: 00:xx ~ 04:59는 다음날로 판단
     *
     * 예시:
     *   parseLastTime("23:45", 2026-07-01) → 2026-07-01 23:45
     *   parseLastTime("00:30", 2026-07-01) → 2026-07-02 00:30 (자정 넘김)
     *   parseLastTime("03:15", 2026-07-01) → 2026-07-02 03:15 (자정 넘김)
     *   parseLastTime(null, 2026-07-01) → null
     *
     * @param lastTimeStr "HH:mm" 형식의 막차 시각 (예: "23:45"), null 가능
     * @param baseDate    계산 기준 날짜
     * @return 변환된 LocalDateTime, 입력이 null이면 null
     */
    private LocalDateTime parseLastTime(String lastTimeStr, LocalDate baseDate) {
        // null 또는 빈 문자열이면 null 반환
        if (lastTimeStr == null || lastTimeStr.isBlank()) {
            return null;
        }

        try {
            // "HH:mm" 문자열에서 시(hour)와 분(minute) 추출
            // 예) "23:45" → hour=23, minute=45
            String[] parts = lastTimeStr.split(":");
            if (parts.length != 2) {
                log.warn("시간 형식이 올바르지 않음: {}", lastTimeStr);
                return null;
            }

            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());

            // 자정 넘김 처리 (00:00 ~ 04:59 범위)
            // 이 범위는 전날 자정 이후의 시간으로 판단
            // 예: 00:30 = 자정 이후 30분 → 다음날
            //     04:59 = 자정 이후 4시간 59분 → 다음날
            //     05:00 = 새벽 5시 → 같은 날 (막차가 새벽 5시 이후는 일반적이지 않음)
            if (hour < 5) {
                return baseDate.plusDays(1).atTime(hour, minute);
            }

            return baseDate.atTime(hour, minute);

        } catch (NumberFormatException e) {
            log.warn("막차 시각 파싱 실패: {}", lastTimeStr, e);
            return null;
        }
    }

    /**
     * subPath 노드에서 노선명을 추출합니다.
     *
     * 지하철(trafficType=1): subPath.lane[0].name = "2호선"
     * 버스(trafficType=2): subPath.lane[0].busNo = "5002"
     *
     * @param subPath subPath 노드
     * @param trafficType 교통 수단 (1=지하철, 2=버스)
     * @return 노선명, 없으면 "알 수 없음"
     */
    private String extractLineName(JsonNode subPath, int trafficType) {
        JsonNode lane = subPath.path("lane");
        String transitType = trafficType == 1 ? "지하철" : "버스";

        // ── lane 배열 구조 로깅 ────────────────────────────────────────────────
        if (lane.isArray() && !lane.isEmpty()) {
            JsonNode laneItem = lane.get(0);

            log.debug("[ODsay lane[0] 분석] 교통 수단: {}", transitType);

            // 버스(2): lane[0].busNo 사용
            if (trafficType == 2) {
                String busNo = laneItem.path("busNo").asText(null);
                if (busNo != null && !busNo.isBlank()) {
                    log.debug("[ODsay] 버스 노선명 추출: busNo={}", busNo);
                    return busNo;
                }

                // busNo가 없으면 name 시도
                String name = laneItem.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    log.debug("[ODsay] 버스 노선명 대체: name={}", name);
                    return name;
                }

                log.warn("[ODsay] 버스 노선명 추출 실패 - lane[0] 필드:");
                laneItem.fields().forEachRemaining(entry ->
                    log.warn("  - {}: {}", entry.getKey(), entry.getValue().asText())
                );
                return "알 수 없음";
            }

            // 지하철(1): lane[0].name 사용
            String name = laneItem.path("name").asText(null);
            if (name != null && !name.isBlank()) {
                log.debug("[ODsay] 지하철 노선명 추출: name={}", name);
                return name;
            }

            log.warn("[ODsay] 지하철 노선명 추출 실패 - name 필드 없음");
            return "알 수 없음";
        }

        log.warn("[ODsay] lane 배열 없음 또는 비어있음 (trafficType={})", transitType);
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

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 클래스
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 대중교통 구간의 막차 정보를 저장하는 클래스입니다.
     *
     * 각 구간마다:
     *   - subPathIndex: 원본 subPath 배열에서의 위치
     *   - priorSectionTime: 해당 구간 탑승 전까지 걸리는 누적 시간(분)
     *   - lastTransitTime: 해당 구간의 막차 시각
     *   - lineName, startName, endName: 노선 정보
     *   - type: "SUBWAY" 또는 "BUS"
     */
    private static class Candidate {
        final int subPathIndex;
        final int priorSectionTime;
        final LocalDateTime lastTransitTime;
        final String lineName;
        final String startName;
        final String endName;
        final String type;

        Candidate(int subPathIndex, int priorSectionTime, LocalDateTime lastTransitTime,
                  String lineName, String startName, String endName, String type) {
            this.subPathIndex = subPathIndex;
            this.priorSectionTime = priorSectionTime;
            this.lastTransitTime = lastTransitTime;
            this.lineName = lineName;
            this.startName = startName;
            this.endName = endName;
            this.type = type;
        }
    }
}

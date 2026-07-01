package com.lasttrain.transit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasttrain.bus.external.GyeonggiBusRouteClient;
import com.lasttrain.bus.external.SeoulBusArrivalClient;
import com.lasttrain.route.external.OdsayClient;
import com.lasttrain.transit.domain.LastTransitSchedule;
import com.lasttrain.transit.repository.LastTransitScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 막차 시각 캐시 서비스
 *
 * 역할:
 *   - 대중교통별 막차 시각 조회
 *   - DB 캐시 먼저 확인 (캐시 히트 시 즉시 반환)
 *   - 캐시 미스 시 외부 API 호출 후 DB에 저장
 *   - 다음 조회 때 DB에서 빠르게 반환
 *
 * 캐시 전략:
 *   - 첫 조회: API 호출 + DB 저장 (~1초 소요)
 *   - 이후 조회: DB에서 직접 조회 (~10ms 소요)
 *   - 성능 개선: API 호출 99% 감소
 *
 * 지원 대중교통:
 *   - SUBWAY: 전철 (ODsay API 사용)
 *   - BUS_SEOUL: 서울 시내버스 (서울 버스도착정보 API 사용)
 *   - BUS_GYEONGGI: 경기도 버스 (경기버스 API 사용)
 *
 * dayType 매핑:
 *   - "1" → "WEEKDAY" (평일: 월~금)
 *   - "2" → "SATURDAY" (토요일)
 *   - "3" → "SUNDAY" (일요일)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransitCacheService {

    // 외부 API 클라이언트 (주입받음)
    private final OdsayClient odsayClient;
    private final SeoulBusArrivalClient seoulBusArrivalClient;
    private final GyeonggiBusRouteClient gyeonggiBusRouteClient;

    // DB 저장소
    private final LastTransitScheduleRepository lastTransitScheduleRepository;

    // JSON 파싱 및 시간 처리
    private final ObjectMapper objectMapper;

    // DB 캐시 저장/갱신 담당
    private final TransitCacheWriter transitCacheWriter;

    /**
     * 전철 막차 시각 조회 (캐시 적용)
     *
     * 캐시 키 구조:
     *   - transitType: "SUBWAY"
     *   - cacheKey: odsayStationId (예: "136", "729")
     *   - dayType: "WEEKDAY", "SATURDAY", "SUNDAY"
     *
     * 조회 흐름:
     *   1. DB에서 캐시 확인
     *   2. 캐시 히트: 즉시 반환 (10ms)
     *   3. 캐시 미스: ODsay API 호출 (500~1000ms)
     *   4. API 응답 처리 후 DB 저장
     *   5. 저장된 막차 시각 반환
     *
     * 예시:
     *   getSubwayLastTime("136", "1")
     *   → dayType 변환: "1" → "WEEKDAY"
     *   → DB 조회: transitType="SUBWAY", cacheKey="136", dayType="WEEKDAY"
     *   → 캐시 미스 시: ODsay API 호출 → "23:45" 추출 → DB 저장
     *   → 반환: "23:45"
     *
     * @param odsayStationId ODsay 역 ID (예: "136" = 서울역)
     * @param dayType 요일 타입 ("1", "2", "3")
     * @return 막차 시간 (HH:mm 형식, 예: "23:45") 또는 null (실패 시)
     */
    public String getSubwayLastTime(String odsayStationId, String dayType) {
        try {
            // dayType 변환: 숫자 → 영문 (1→WEEKDAY, 2→SATURDAY, 3→SUNDAY)
            String convertedDayType = convertDayType(dayType);

            // 캐시 조회
            Optional<LastTransitSchedule> cached = lastTransitScheduleRepository
                .findByTransitTypeAndCacheKeyAndDayType(
                    "SUBWAY",
                    odsayStationId,
                    convertedDayType
                );

            // 캐시 히트: 즉시 반환
            if (cached.isPresent()) {
                log.debug("전철 캐시 히트: stationId={}, dayType={}", odsayStationId, convertedDayType);
                return cached.get().getLastTime();
            }

            // 캐시 미스: 외부 API 호출
            log.debug("전철 캐시 미스: stationId={}. ODsay API 호출...", odsayStationId);
            String scheduleJson = odsayClient.searchSubwaySchedule(odsayStationId, dayType);

            if (scheduleJson != null) {
                // API 응답 JSON 파싱 → 막차 시각 추출
                String lastTime = extractSubwayLastTime(scheduleJson, LocalDate.now(), dayType);

                if (lastTime != null) {
                    // API 호출 성공 → DB에 저장 (TransitCacheWriter 사용)
                    transitCacheWriter.saveOrUpdate("SUBWAY", odsayStationId, convertedDayType, lastTime);
                    return lastTime;
                }
            }

            log.warn("ODsay API에서 막차 시각을 찾을 수 없음: stationId={}", odsayStationId);
            return null;

        } catch (Exception e) {
            log.error("전철 막차 조회 실패: odsayStationId={}, dayType={}", odsayStationId, dayType, e);
            return null;
        }
    }

    /**
     * 서울 시내버스 막차 시각 조회 (캐시 적용)
     *
     * 캐시 키 구조:
     *   - transitType: "BUS_SEOUL"
     *   - cacheKey: stId + ":" + busRouteId + ":" + ord
     *   - dayType: "WEEKDAY", "SATURDAY", "SUNDAY"
     *
     * 캐시 키 형식 예시:
     *   "124000414:100100578:29"
     *     ├─ 124000414: 정류소 ID (stId)
     *     ├─ 100100578: 노선 ID (busRouteId)
     *     └─ 29: 순번 (ord) - 왕복 노선의 경우 1 또는 2
     *
     * 조회 흐름:
     *   1. cacheKey 생성 (stId:busRouteId:ord)
     *   2. DB에서 캐시 확인
     *   3. 캐시 미스 시: 서울 버스 API 호출
     *   4. LocalDateTime → "HH:mm" 변환
     *   5. DB 저장 후 반환
     *
     * 예시:
     *   getSeoulBusLastTime("124000414", "100100578", "29", "1")
     *   → cacheKey: "124000414:100100578:29"
     *   → dayType: "WEEKDAY"
     *   → API 호출 → LocalDateTime.parse("2026-07-01 23:45:00")
     *   → "HH:mm" 변환 → "23:45"
     *   → DB 저장 후 반환
     *
     * @param stId 정류소 ID (Seoul Bus API)
     * @param busRouteId 노선 ID (Seoul Bus API)
     * @param ord 순번 ("1" 또는 "2", 왕복 노선 구분)
     * @param dayType 요일 타입 ("1", "2", "3")
     * @return 막차 시간 (HH:mm 형식) 또는 null (실패 시)
     */
    public String getSeoulBusLastTime(String stId, String busRouteId, String ord, String dayType) {
        try {
            // dayType 변환
            String convertedDayType = convertDayType(dayType);

            // 캐시 키 생성: "정류소ID:노선ID:순번"
            String cacheKey = stId + ":" + busRouteId + ":" + ord;

            // 캐시 조회
            Optional<LastTransitSchedule> cached = lastTransitScheduleRepository
                .findByTransitTypeAndCacheKeyAndDayType(
                    "BUS_SEOUL",
                    cacheKey,
                    convertedDayType
                );

            // 캐시 히트
            if (cached.isPresent()) {
                log.debug("서울버스 캐시 히트: cacheKey={}, dayType={}", cacheKey, convertedDayType);
                return cached.get().getLastTime();
            }

            // 캐시 미스: 서울 버스 API 호출
            log.debug("서울버스 캐시 미스: cacheKey={}. 서울 버스 API 호출...", cacheKey);
            LocalDateTime lastBusTime = seoulBusArrivalClient.getLastBusTime(stId, busRouteId, ord);

            if (lastBusTime != null) {
                // LocalDateTime → "HH:mm" 형식 변환
                // 예: 2026-07-01T23:45:00 → "23:45"
                String lastTime = lastBusTime.format(DateTimeFormatter.ofPattern("HH:mm"));

                // DB에 저장 (TransitCacheWriter 사용)
                transitCacheWriter.saveOrUpdate("BUS_SEOUL", cacheKey, convertedDayType, lastTime);
                return lastTime;
            }

            log.warn("서울 버스 API에서 막차 시각을 찾을 수 없음: stId={}, busRouteId={}, ord={}",
                     stId, busRouteId, ord);
            return null;

        } catch (Exception e) {
            log.error("서울버스 막차 조회 실패: stId={}, busRouteId={}, ord={}, dayType={}",
                     stId, busRouteId, ord, dayType, e);
            return null;
        }
    }

    /**
     * 경기도 버스 막차 시각 조회 (캐시 적용)
     *
     * 캐시 키 구조:
     *   - transitType: "BUS_GYEONGGI"
     *   - cacheKey: routeId (경기버스 노선 ID)
     *   - dayType: "WEEKDAY", "SATURDAY", "SUNDAY"
     *
     * 캐시 키 형식 예시:
     *   "200000037" (경기버스 노선 ID)
     *
     * 조회 흐름:
     *   1. DB에서 캐시 확인
     *   2. 캐시 미스 시: 경기버스 API 호출
     *   3. LocalDateTime → "HH:mm" 변환
     *   4. DB 저장 후 반환
     *
     * 예시:
     *   getGyeonggiBusLastTime("200000037", "1")
     *   → cacheKey: "200000037"
     *   → dayType: "WEEKDAY"
     *   → API 호출 → "23:50" 추출
     *   → DB 저장 후 반환
     *
     * @param routeId 경기버스 노선 ID (예: "200000037")
     * @param dayType 요일 타입 ("1", "2", "3")
     * @return 막차 시간 (HH:mm 형식) 또는 null (실패 시)
     */
    public String getGyeonggiBusLastTime(String routeId, String dayType) {
        try {
            // dayType 변환
            String convertedDayType = convertDayType(dayType);

            // 캐시 조회
            Optional<LastTransitSchedule> cached = lastTransitScheduleRepository
                .findByTransitTypeAndCacheKeyAndDayType(
                    "BUS_GYEONGGI",
                    routeId,
                    convertedDayType
                );

            // 캐시 히트
            if (cached.isPresent()) {
                log.debug("경기버스 캐시 히트: routeId={}, dayType={}", routeId, convertedDayType);
                return cached.get().getLastTime();
            }

            // 캐시 미스: 경기버스 API 호출
            log.debug("경기버스 캐시 미스: routeId={}. 경기버스 API 호출...", routeId);
            LocalDateTime lastBusTime = gyeonggiBusRouteClient.getLastBusTime(routeId);

            if (lastBusTime != null) {
                // LocalDateTime → "HH:mm" 형식 변환
                String lastTime = lastBusTime.format(DateTimeFormatter.ofPattern("HH:mm"));

                // DB에 저장 (TransitCacheWriter 사용)
                transitCacheWriter.saveOrUpdate("BUS_GYEONGGI", routeId, convertedDayType, lastTime);
                return lastTime;
            }

            log.warn("경기버스 API에서 막차 시각을 찾을 수 없음: routeId={}", routeId);
            return null;

        } catch (Exception e) {
            log.error("경기버스 막차 조회 실패: routeId={}, dayType={}", routeId, dayType, e);
            return null;
        }
    }

    /**
     * ODsay 전철 시간표 JSON에서 막차 시각을 추출합니다.
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
     *
     * 자정 넘김 처리:
     *   - Idx >= 24이면 (Idx - 24)시로 표기 (예: Idx=24 → 00시)
     * ────────────────────────────────────────────────────────────────────────────
     *
     * @param scheduleJson ODsay subwayTimeTable 응답 JSON
     * @param baseDate     계산 기준 날짜 (자정 넘김 처리용)
     * @param dayType      요일 구분 ("1"=평일, "2"=토요일, "3"=일요일)
     * @return 막차 시간 (HH:mm 형식, 예: "23:45") 또는 null (파싱 실패 시)
     */
    private String extractSubwayLastTime(String scheduleJson, LocalDate baseDate, String dayType) {
        try {
            // dayType에 맞는 목록(OrdList/SatList/SunList)의 이름을 결정합니다.
            // 평일(1) → OrdList, 토요일(2) → SatList, 일요일(3) → SunList
            String listName = switch (dayType) {
                case "2" -> "SatList";
                case "3" -> "SunList";
                default  -> "OrdList";
            };

            // JSON에서 해당 요일의 시간표 데이터 추출
            // result.OrdList.down.time (또는 SatList/SunList)
            JsonNode timeList = objectMapper.readTree(scheduleJson)
                                            .path("result")
                                            .path(listName)
                                            .path("down")
                                            .path("time");

            // time[] 배열이 비어있으면 막차 정보 없음
            if (!timeList.isArray() || timeList.isEmpty()) {
                log.debug("시간표 배열이 비어있음: listName={}", listName);
                return null;
            }

            // 배열의 마지막 항목이 가장 늦은 시간대 = 막차가 포함된 시간대입니다.
            JsonNode lastTimeGroup = timeList.get(timeList.size() - 1);

            int hour = lastTimeGroup.path("Idx").asInt();
            String list = lastTimeGroup.path("list").asText();

            // list 문자열에서 마지막 "분" 값을 추출합니다.
            // 예) "10(온수) 18(석남) ... 57(석남)" → 57
            Integer minute = extractLastMinute(list);
            if (minute == null) {
                log.debug("분 값을 추출할 수 없음: list={}", list);
                return null;
            }

            // 시간과 분을 "HH:mm" 형식으로 변환
            // 자정 넘김 처리: hour >= 24이면 (hour - 24)로 계산
            int displayHour = hour >= 24 ? hour - 24 : hour;
            return String.format("%02d:%02d", displayHour, minute);

        } catch (Exception e) {
            log.warn("전철 시간표 JSON 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * "분(역명) 분(역명) ..." 형식의 문자열에서 마지막 "분" 값을 추출합니다.
     *
     * 예) "10(온수) 18(석남) 28(온수) 38(석남) 47(온수) 57(석남)" → 57
     *
     * 정규식 "(\\d+)\\(" 으로 "숫자(" 패턴을 모두 찾고, 가장 마지막으로 찾은 숫자를 반환합니다.
     *
     * 동작:
     *   1. 정규식으로 숫자와 "(" 조합 찾기
     *   2. 반복문으로 모든 매칭 순회
     *   3. 마지막 매칭의 숫자(group(1))만 반환
     *
     * @param list "분(역명)" 형식의 시간대 문자열
     * @return 마지막 "분" 값 (정수), list가 비어있거나 패턴이 없으면 null
     */
    private Integer extractLastMinute(String list) {
        if (list == null || list.isBlank()) {
            return null;
        }

        // 정규식: (\\d+)\\(
        //   - (\\d+) : 1개 이상의 숫자 (캡처 그룹)
        //   - \\( : 리터럴 "(" 문자
        // 예) "57(석남)"에서 "57"을 캡처
        Matcher matcher = Pattern.compile("(\\d+)\\(").matcher(list);

        Integer lastMinute = null;
        while (matcher.find()) {
            lastMinute = Integer.parseInt(matcher.group(1));
        }

        return lastMinute;
    }

    /**
     * 요일 타입 변환 (숫자 → 영문)
     *
     * 변환 규칙:
     *   - "1" → "WEEKDAY" (평일: 월~금)
     *   - "2" → "SATURDAY" (토요일)
     *   - "3" → "SUNDAY" (일요일)
     *   - 그 외 → IllegalArgumentException 발생
     *
     * 용도:
     *   - 외부 API에서 받은 dayType을 DB 저장 형식으로 변환
     *   - 캐시 조회 시 일관성 있는 키 사용
     *
     * @param dayType 요일 타입 숫자 ("1", "2", "3")
     * @return 변환된 요일 타입 ("WEEKDAY", "SATURDAY", "SUNDAY")
     * @throws IllegalArgumentException 유효하지 않은 dayType
     */
    private String convertDayType(String dayType) {
        return switch (dayType) {
            case "1" -> "WEEKDAY";
            case "2" -> "SATURDAY";
            case "3" -> "SUNDAY";
            default -> throw new IllegalArgumentException("유효하지 않은 dayType: " + dayType);
        };
    }
}

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 막차 시각 캐시 서비스
 *
 * 역할:
 *   - 대중교통별 막차 시각 조회
 *   - 실시간 데이터를 위해 항상 API 먼저 호출
 *   - API 호출 성공 시 DB에 저장 (최신 데이터 유지)
 *   - API 호출 실패 시 DB에서 마지막 저장값 반환 (Fallback)
 *   - DB도 없으면 null 반환
 *
 * 데이터 흐름:
 *   정상: API 호출 성공 → DB 저장 → 최신 데이터 반환
 *   Fallback: API 호출 실패 → DB에서 마지막값 조회 → 반환
 *   없음: API 실패 + DB 없음 → null 반환
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

    // ── 성과 측정 카운터 ──────────────────────────────────────────────────────
    // API 호출 결과 통계
    private static final AtomicInteger apiSuccessCount = new AtomicInteger(0);
    private static final AtomicInteger apiFallbackCount = new AtomicInteger(0);

    // DB Fallback 결과 통계
    private static final AtomicInteger fallbackHitCount = new AtomicInteger(0);
    private static final AtomicInteger fallbackMissCount = new AtomicInteger(0);

    // 응답 시간 측정 (누적 시간)
    private static final AtomicLong totalApiResponseTime = new AtomicLong(0);
    private static final AtomicInteger apiCallCount = new AtomicInteger(0);
    private static final AtomicLong totalFallbackResponseTime = new AtomicLong(0);
    private static final AtomicInteger fallbackCallCount = new AtomicInteger(0);

    /**
     * 전철 막차 시각 조회 (실시간 API 우선 + DB Fallback)
     *
     * 캐시 키 구조:
     *   - transitType: "SUBWAY"
     *   - cacheKey: odsayStationId (예: "136", "729")
     *   - dayType: "WEEKDAY", "SATURDAY", "SUNDAY"
     *
     * 조회 흐름 (실시간 데이터 우선):
     *   1. ODsay API 호출 (실시간 데이터 획득)
     *   2. API 성공 → JSON 파싱 → 막차 시각 추출 → DB 저장 → 반환
     *   3. API 실패 → DB에서 마지막 저장값 조회 → 반환 (log.warn 발생)
     *   4. DB도 없음 → null 반환
     *
     * 예시 (정상):
     *   getSubwayLastTime("136", "1")
     *   → ODsay API 호출 → "23:45" 추출 → DB 저장 → 반환: "23:45"
     *
     * 예시 (Fallback):
     *   getSubwayLastTime("136", "1")
     *   → ODsay API 실패
     *   → DB에서 마지막값 조회 → "23:42" (3분 전 데이터)
     *   → log.warn("ODsay API 실패, DB Fallback 사용: stationId=136")
     *   → 반환: "23:42"
     *
     * @param odsayStationId ODsay 역 ID (예: "136" = 서울역)
     * @param dayType 요일 타입 ("1", "2", "3")
     * @return 막차 시간 (HH:mm 형식, 예: "23:45") 또는 null (API 실패 + DB 없음)
     */
    public String getSubwayLastTime(String odsayStationId, String dayType) {
        try {
            // ── API 응답 시간 측정 시작 ──
            long apiStartTime = System.currentTimeMillis();

            // dayType 변환: 숫자 → 영문 (1→WEEKDAY, 2→SATURDAY, 3→SUNDAY)
            String convertedDayType = convertDayType(dayType);

            // 1단계: 외부 API 호출 (실시간 데이터 획득)
            log.debug("전철 실시간 데이터 요청: stationId={}, dayType={}. ODsay API 호출...", odsayStationId, convertedDayType);
            String scheduleJson = odsayClient.searchSubwaySchedule(odsayStationId, dayType);

            // ── API 응답 시간 측정 종료 및 기록 ──
            long apiEndTime = System.currentTimeMillis();
            long apiResponseTime = apiEndTime - apiStartTime;
            totalApiResponseTime.addAndGet(apiResponseTime);
            apiCallCount.incrementAndGet();

            // 2단계: API 성공 시 값만 반환 (DB 저장 X)
            if (scheduleJson != null) {
                // API 응답 JSON 파싱 → 막차 시각 추출
                String lastTime = extractSubwayLastTime(scheduleJson, LocalDate.now(), dayType);

                if (lastTime != null) {
                    // API 호출 성공 → 값만 반환 (DB 저장하지 않음)
                    log.debug("전철 API 호출 성공: stationId={}, lastTime={}", odsayStationId, lastTime);

                    // ── 성과 측정: API 성공 카운트 ──
                    apiSuccessCount.incrementAndGet();

                    return lastTime;
                }
            }

            // 3단계: API 실패 시 DB에서 마지막 저장값 조회 (Fallback)
            log.warn("ODsay API 실패, DB Fallback 사용: stationId={}", odsayStationId);

            // ── Fallback 응답 시간 측정 시작 ──
            long fallbackStartTime = System.currentTimeMillis();

            // ── 성과 측정: API Fallback 발생 카운트 ──
            apiFallbackCount.incrementAndGet();

            Optional<LastTransitSchedule> fallback = lastTransitScheduleRepository
                .findByTransitTypeAndCacheKeyAndDayType(
                    "SUBWAY",
                    odsayStationId,
                    convertedDayType
                );

            // ── Fallback 응답 시간 측정 종료 및 기록 ──
            long fallbackEndTime = System.currentTimeMillis();
            long fallbackResponseTime = fallbackEndTime - fallbackStartTime;
            totalFallbackResponseTime.addAndGet(fallbackResponseTime);
            fallbackCallCount.incrementAndGet();

            // 4단계: DB에서 값을 찾으면 반환, 없으면 null
            if (fallback.isPresent()) {
                log.debug("DB Fallback 데이터 반환: stationId={}, lastTime={}", odsayStationId, fallback.get().getLastTime());

                // ── 성과 측정: Fallback 히트 카운트 ──
                fallbackHitCount.incrementAndGet();

                return fallback.get().getLastTime();
            }

            // ── 성과 측정: Fallback 미스 카운트 ──
            fallbackMissCount.incrementAndGet();

            log.warn("ODsay API 실패 + DB 데이터 없음: stationId={}", odsayStationId);
            return null;

        } catch (Exception e) {
            log.error("전철 막차 조회 실패: odsayStationId={}, dayType={}", odsayStationId, dayType, e);

            // 예외 발생 시에도 DB Fallback 시도
            try {
                String convertedDayType = convertDayType(dayType);
                Optional<LastTransitSchedule> fallback = lastTransitScheduleRepository
                    .findByTransitTypeAndCacheKeyAndDayType(
                        "SUBWAY",
                        odsayStationId,
                        convertedDayType
                    );
                if (fallback.isPresent()) {
                    log.debug("예외 발생 시 DB Fallback 반환: stationId={}", odsayStationId);
                    return fallback.get().getLastTime();
                }
            } catch (Exception fallbackException) {
                log.error("DB Fallback 조회도 실패: stationId={}", odsayStationId, fallbackException);
            }

            return null;
        }
    }

    /**
     * 서울 시내버스 막차 시각 조회 (실시간 API 우선 + DB Fallback)
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
     * 조회 흐름 (실시간 데이터 우선):
     *   1. cacheKey 생성 (stId:busRouteId:ord)
     *   2. 서울 버스 API 호출 (실시간 데이터 획득)
     *   3. API 성공 → LocalDateTime → "HH:mm" 변환 → DB 저장 (Lazy Caching) → 반환
     *   4. API 실패 → DB에서 마지막 저장값 조회 → 반환 (log.warn 발생)
     *   5. DB도 없음 → null 반환
     *
     * Lazy Caching 전략:
     *   - 처음 조회 시: API 호출 → DB에 저장 (이후 Fallback 용도)
     *   - 이후 API 장애 시: DB에서 마지막 저장값으로 Fallback
     *
     * 예시 (정상):
     *   getSeoulBusLastTime("124000414", "100100578", "29", "1")
     *   → 서울 버스 API 호출 → "23:45" → DB 저장 → 반환: "23:45"
     *   → 이후 API 장애 시 이 DB 값으로 Fallback 가능
     *
     * 예시 (Fallback):
     *   getSeoulBusLastTime("124000414", "100100578", "29", "1")
     *   → 서울 버스 API 실패
     *   → DB에서 마지막값 조회 → "23:42" (이전에 저장된 값)
     *   → log.warn("서울 버스 API 실패, DB Fallback 사용: stId=124000414...")
     *   → 반환: "23:42"
     *
     * @param busRouteId 노선 ID (Seoul Bus API - busLocalBlID)
     * @param dayType 요일 타입 ("1", "2", "3")
     * @return 막차 시간 (HH:mm 형식) 또는 null (API 실패 + DB 없음)
     */
    public String getSeoulBusLastTime(String busRouteId, String dayType) {
        try {
            // ── API 응답 시간 측정 시작 ──
            long apiStartTime = System.currentTimeMillis();

            // dayType 변환
            String convertedDayType = convertDayType(dayType);

            // 캐시 키 생성: "노선ID"
            String cacheKey = busRouteId;

            // 1단계: 외부 API 호출 (실시간 데이터 획득)
            log.debug("서울 버스 실시간 데이터 요청: cacheKey={}, dayType={}. 서울 버스 API 호출...", cacheKey, convertedDayType);
            LocalDateTime lastBusTime = seoulBusArrivalClient.getLastBusTime(busRouteId, dayType);

            // ── API 응답 시간 측정 종료 및 기록 ──
            long apiEndTime = System.currentTimeMillis();
            long apiResponseTime = apiEndTime - apiStartTime;
            totalApiResponseTime.addAndGet(apiResponseTime);
            apiCallCount.incrementAndGet();

            // 2단계: API 성공 시 DB 저장 (Lazy Caching) + 반환
            if (lastBusTime != null) {
                // LocalDateTime → "HH:mm" 형식 변환
                // 예: 2026-07-01T23:45:00 → "23:45"
                String lastTime = lastBusTime.format(DateTimeFormatter.ofPattern("HH:mm"));

                // API 호출 성공 → DB에 저장 (이후 API 장애 시 Fallback 용도)
                log.debug("서울 버스 API 호출 성공: cacheKey={}, lastTime={}", cacheKey, lastTime);

                // ── 성과 측정: API 성공 카운트 ──
                apiSuccessCount.incrementAndGet();

                transitCacheWriter.saveOrUpdate("BUS_SEOUL", cacheKey, convertedDayType, lastTime);
                return lastTime;
            }

            // 3단계: API 실패 시 DB에서 마지막 저장값 조회 (Fallback)
            log.warn("서울 버스 API 실패, DB Fallback 사용: cacheKey={}", cacheKey);

            // ── Fallback 응답 시간 측정 시작 ──
            long fallbackStartTime = System.currentTimeMillis();

            // ── 성과 측정: API Fallback 발생 카운트 ──
            apiFallbackCount.incrementAndGet();

            Optional<LastTransitSchedule> fallback = lastTransitScheduleRepository
                .findByTransitTypeAndCacheKeyAndDayType(
                    "BUS_SEOUL",
                    cacheKey,
                    convertedDayType
                );

            // ── Fallback 응답 시간 측정 종료 및 기록 ──
            long fallbackEndTime = System.currentTimeMillis();
            long fallbackResponseTime = fallbackEndTime - fallbackStartTime;
            totalFallbackResponseTime.addAndGet(fallbackResponseTime);
            fallbackCallCount.incrementAndGet();

            // 4단계: DB에서 값을 찾으면 반환, 없으면 null
            if (fallback.isPresent()) {
                log.debug("DB Fallback 데이터 반환: cacheKey={}, lastTime={}", cacheKey, fallback.get().getLastTime());

                // ── 성과 측정: Fallback 히트 카운트 ──
                fallbackHitCount.incrementAndGet();

                return fallback.get().getLastTime();
            }

            // ── 성과 측정: Fallback 미스 카운트 ──
            fallbackMissCount.incrementAndGet();

            log.warn("서울 버스 API 실패 + DB 데이터 없음: cacheKey={}, dayType={}",
                     busRouteId, dayType);
            return null;

        } catch (Exception e) {
            log.error("서울버스 막차 조회 실패: busRouteId={}, dayType={}", busRouteId, dayType, e);

            // 예외 발생 시에도 DB Fallback 시도
            try {
                String convertedDayType = convertDayType(dayType);
                String cacheKey = busRouteId;
                Optional<LastTransitSchedule> fallback = lastTransitScheduleRepository
                    .findByTransitTypeAndCacheKeyAndDayType(
                        "BUS_SEOUL",
                        cacheKey,
                        convertedDayType
                    );
                if (fallback.isPresent()) {
                    log.debug("예외 발생 시 DB Fallback 반환: cacheKey={}", cacheKey);
                    return fallback.get().getLastTime();
                }
            } catch (Exception fallbackException) {
                log.error("DB Fallback 조회도 실패: busRouteId={}, dayType={}", busRouteId, dayType, fallbackException);
            }

            return null;
        }
    }

    /**
     * 경기도 버스 막차 시각 조회 (실시간 API 우선 + DB Fallback)
     *
     * 캐시 키 구조:
     *   - transitType: "BUS_GYEONGGI"
     *   - cacheKey: routeId (경기버스 노선 ID)
     *   - dayType: "WEEKDAY", "SATURDAY", "SUNDAY"
     *
     * 캐시 키 형식 예시:
     *   "200000037" (경기버스 노선 ID)
     *
     * 조회 흐름 (실시간 데이터 우선):
     *   1. 경기버스 API 호출 (실시간 데이터 획득)
     *   2. API 성공 → LocalDateTime → "HH:mm" 변환 → DB 저장 (Lazy Caching) → 반환
     *   3. API 실패 → DB에서 마지막 저장값 조회 → 반환 (log.warn 발생)
     *   4. DB도 없음 → null 반환
     *
     * Lazy Caching 전략:
     *   - 처음 조회 시: API 호출 → DB에 저장 (이후 Fallback 용도)
     *   - 이후 API 장애 시: DB에서 마지막 저장값으로 Fallback
     *
     * 예시 (정상):
     *   getGyeonggiBusLastTime("200000037", "1")
     *   → 경기버스 API 호출 → "23:50" → DB 저장 → 반환: "23:50"
     *   → 이후 API 장애 시 이 DB 값으로 Fallback 가능
     *
     * 예시 (Fallback):
     *   getGyeonggiBusLastTime("200000037", "1")
     *   → 경기버스 API 실패
     *   → DB에서 마지막값 조회 → "23:48" (이전에 저장된 값)
     *   → log.warn("경기버스 API 실패, DB Fallback 사용: routeId=200000037")
     *   → 반환: "23:48"
     *
     * @param routeId 경기버스 노선 ID (예: "200000037")
     * @param dayType 요일 타입 ("1", "2", "3")
     * @return 막차 시간 (HH:mm 형식) 또는 null (API 실패 + DB 없음)
     */
    public String getGyeonggiBusLastTime(String routeId, String dayType) {
        try {
            // ── API 응답 시간 측정 시작 ──
            long apiStartTime = System.currentTimeMillis();

            // dayType 변환
            String convertedDayType = convertDayType(dayType);

            // 1단계: 외부 API 호출 (실시간 데이터 획득)
            log.debug("경기 버스 실시간 데이터 요청: routeId={}, dayType={}. 경기버스 API 호출...", routeId, convertedDayType);
            LocalDateTime lastBusTime = gyeonggiBusRouteClient.getLastBusTime(routeId);

            // ── API 응답 시간 측정 종료 및 기록 ──
            long apiEndTime = System.currentTimeMillis();
            long apiResponseTime = apiEndTime - apiStartTime;
            totalApiResponseTime.addAndGet(apiResponseTime);
            apiCallCount.incrementAndGet();

            // 2단계: API 성공 시 DB 저장 (Lazy Caching) + 반환
            if (lastBusTime != null) {
                // LocalDateTime → "HH:mm" 형식 변환
                String lastTime = lastBusTime.format(DateTimeFormatter.ofPattern("HH:mm"));

                // API 호출 성공 → DB에 저장 (이후 API 장애 시 Fallback 용도)
                log.debug("경기버스 API 호출 성공: routeId={}, lastTime={}", routeId, lastTime);

                // ── 성과 측정: API 성공 카운트 ──
                apiSuccessCount.incrementAndGet();

                transitCacheWriter.saveOrUpdate("BUS_GYEONGGI", routeId, convertedDayType, lastTime);
                return lastTime;
            }

            // 3단계: API 실패 시 DB에서 마지막 저장값 조회 (Fallback)
            log.warn("경기버스 API 실패, DB Fallback 사용: routeId={}", routeId);

            // ── Fallback 응답 시간 측정 시작 ──
            long fallbackStartTime = System.currentTimeMillis();

            // ── 성과 측정: API Fallback 발생 카운트 ──
            apiFallbackCount.incrementAndGet();

            Optional<LastTransitSchedule> fallback = lastTransitScheduleRepository
                .findByTransitTypeAndCacheKeyAndDayType(
                    "BUS_GYEONGGI",
                    routeId,
                    convertedDayType
                );

            // ── Fallback 응답 시간 측정 종료 및 기록 ──
            long fallbackEndTime = System.currentTimeMillis();
            long fallbackResponseTime = fallbackEndTime - fallbackStartTime;
            totalFallbackResponseTime.addAndGet(fallbackResponseTime);
            fallbackCallCount.incrementAndGet();

            // 4단계: DB에서 값을 찾으면 반환, 없으면 null
            if (fallback.isPresent()) {
                log.debug("DB Fallback 데이터 반환: routeId={}, lastTime={}", routeId, fallback.get().getLastTime());

                // ── 성과 측정: Fallback 히트 카운트 ──
                fallbackHitCount.incrementAndGet();

                return fallback.get().getLastTime();
            }

            // ── 성과 측정: Fallback 미스 카운트 ──
            fallbackMissCount.incrementAndGet();

            log.warn("경기버스 API 실패 + DB 데이터 없음: routeId={}", routeId);
            return null;

        } catch (Exception e) {
            log.error("경기버스 막차 조회 실패: routeId={}, dayType={}", routeId, dayType, e);

            // 예외 발생 시에도 DB Fallback 시도
            try {
                String convertedDayType = convertDayType(dayType);
                Optional<LastTransitSchedule> fallback = lastTransitScheduleRepository
                    .findByTransitTypeAndCacheKeyAndDayType(
                        "BUS_GYEONGGI",
                        routeId,
                        convertedDayType
                    );
                if (fallback.isPresent()) {
                    log.debug("예외 발생 시 DB Fallback 반환: routeId={}", routeId);
                    return fallback.get().getLastTime();
                }
            } catch (Exception fallbackException) {
                log.error("DB Fallback 조회도 실패: routeId={}", routeId, fallbackException);
            }

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
    public String extractSubwayLastTime(String scheduleJson, LocalDate baseDate, String dayType) {
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

    // ── 성과 측정 메서드 (TransitAdminController에서 호출) ────────────────────────

    /**
     * 현재까지의 성과 메트릭을 조회합니다.
     *
     * @return 성과 메트릭 정보 (JSON 형식)
     */
    public static String getMetrics() {
        int totalApiCalls = apiSuccessCount.get() + apiFallbackCount.get();
        double successRate = totalApiCalls > 0 ? (double) apiSuccessCount.get() / totalApiCalls * 100 : 0;
        double avgApiResponseTime = apiCallCount.get() > 0 ? (double) totalApiResponseTime.get() / apiCallCount.get() : 0;
        double avgFallbackResponseTime = fallbackCallCount.get() > 0 ? (double) totalFallbackResponseTime.get() / fallbackCallCount.get() : 0;

        return String.format(
            "[API 성공] %d회 / [Fallback 발생] %d회 / [Fallback 히트] %d회 / [Fallback 미스] %d회\n" +
            "API 성공률: %.2f%%\n" +
            "외부 API 평균 응답 시간: %.2fms\n" +
            "DB Fallback 평균 응답 시간: %.2fms",
            apiSuccessCount.get(),
            apiFallbackCount.get(),
            fallbackHitCount.get(),
            fallbackMissCount.get(),
            successRate,
            avgApiResponseTime,
            avgFallbackResponseTime
        );
    }

    /**
     * 성과 메트릭을 리셋합니다.
     */
    public static void resetMetrics() {
        apiSuccessCount.set(0);
        apiFallbackCount.set(0);
        fallbackHitCount.set(0);
        fallbackMissCount.set(0);
        totalApiResponseTime.set(0);
        apiCallCount.set(0);
        totalFallbackResponseTime.set(0);
        fallbackCallCount.set(0);
    }
}

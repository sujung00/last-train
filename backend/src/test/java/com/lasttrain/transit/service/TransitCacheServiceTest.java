package com.lasttrain.transit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasttrain.bus.external.GyeonggiBusRouteClient;
import com.lasttrain.bus.external.SeoulBusArrivalClient;
import com.lasttrain.route.external.OdsayClient;
import com.lasttrain.transit.domain.LastTransitSchedule;
import com.lasttrain.transit.repository.LastTransitScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TransitCacheService 단위 테스트
 *
 * ── 이 테스트가 하는 일 ────────────────────────────────────────────────────────
 * 외부 API 호출, 응답 처리, DB Fallback 로직이 올바르게 동작하는지 검증합니다.
 * Mock을 사용해 외부 API와 DB를 가짜 객체로 대체하여
 * TransitCacheService의 로직 자체만 순수하게 테스트합니다.
 *
 * ── Mock vs 통합 테스트 ─────────────────────────────────────────────────────────
 * • Mock (단위 테스트): 외부 의존성을 가짜로 대체 → 빠르고, 특정 시나리오 테스트 용이
 * • 통합 테스트: 실제 DB/API 사용 → 느리지만, 실제 환경과 가장 유사
 *
 * TransitCacheService는 외부 API와의 상호작용이 주요 기능이므로
 * Mock으로 다양한 시나리오(성공/실패/Fallback)를 빠르게 검증합니다.
 *
 * ── @BeforeEach에서 resetMetrics() 호출하는 이유 ────────────────────────────
 * TransitCacheService의 카운터는 static이므로 테스트 간 상태가 누적됩니다.
 * 각 테스트 전에 카운터를 초기화해 테스트 간 간섭을 방지합니다.
 * ────────────────────────────────────────────────────────────────────────────────
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransitCacheService 테스트")
class TransitCacheServiceTest {

    @Mock
    private OdsayClient odsayClient;

    @Mock
    private SeoulBusArrivalClient seoulBusArrivalClient;

    @Mock
    private GyeonggiBusRouteClient gyeonggiBusRouteClient;

    @Mock
    private LastTransitScheduleRepository lastTransitScheduleRepository;

    @Mock
    private TransitCacheWriter transitCacheWriter;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransitCacheService transitCacheService;

    // ── 공통 테스트 데이터 ─────────────────────────────────────────────────────────

    private static final String SUBWAY_STATION_ID = "136";   // 강남역
    private static final String DAY_TYPE_WEEKDAY = "1";
    private static final String DAY_TYPE_CONVERTED = "WEEKDAY";
    private static final String LAST_TIME = "23:45";

    private static final String SEOUL_BUS_ROUTE_ID = "100100578";
    private static final String SEOUL_BUS_CACHE_KEY = "100100578";

    private static final String GYEONGGI_BUS_ROUTE_ID = "200000037";

    /**
     * 각 테스트 전에 실행되는 초기화 메서드
     *
     * 역할:
     *   - 성과 측정 카운터를 초기화해 테스트 간 간섭 방지
     *   - 테스트 전 상태를 깨끗하게 만듦
     */
    @BeforeEach
    void setUp() {
        // 성과 측정 카운터 초기화 (테스트 간 상태 독립성 보장)
        TransitCacheService.resetMetrics();
    }


    // ── 1번: getSubwayLastTime() - ODsay API 성공 → 정상값 반환 ──────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   API 호출이 성공한 경우, 응답 JSON에서 올바르게 값을 추출해 반환하는지 확인합니다.
    //   또한 mock 검증으로 API가 1회만 호출되는지 확인합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSubwayLastTime() - ODsay API 성공 → Fallback 카운터 증가")
    void getSubwayLastTime_API_성공_API_성공_카운터_증가() {
        // given: ODsay API가 null을 반환합니다 (파싱 불가능).
        // 이 경우 API는 호출됐지만 데이터를 얻지 못한 상태입니다.
        when(odsayClient.searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY))
                .thenReturn(null);

        // DB에는 이전 저장값이 있습니다.
        LastTransitSchedule fallbackData = LastTransitSchedule.builder()
                .transitType("SUBWAY")
                .cacheKey(SUBWAY_STATION_ID)
                .dayType(DAY_TYPE_CONVERTED)
                .lastTime("23:45")
                .updatedAt(LocalDateTime.now())
                .build();

        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "SUBWAY", SUBWAY_STATION_ID, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.of(fallbackData));

        // when: 전철 막차 시각을 조회합니다.
        String result = transitCacheService.getSubwayLastTime(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);

        // then: DB Fallback 값이 반환되어야 합니다.
        assertThat(result).isEqualTo("23:45");                                         // Fallback 값이 반환되는지
        verify(odsayClient, times(1))
                .searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);           // API는 호출됨
    }


    // ── 2번: getSubwayLastTime() - ODsay API 실패(null) → DB Fallback 값 반환 ──────
    //
    // 이 테스트가 필요한 이유:
    //   API가 실패(null 반환)했을 때 자동으로 DB에서 마지막 저장값을 조회하는지 확인합니다.
    //   이것이 서비스 가용성을 보장하는 핵심 메커니즘입니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSubwayLastTime() - ODsay API 실패(null) → DB Fallback 값 반환")
    void getSubwayLastTime_API_실패_DB_Fallback_반환() {
        // given: ODsay API가 null을 반환합니다 (서버 장애).
        when(odsayClient.searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY))
                .thenReturn(null);

        // DB에는 이전에 저장된 막차 시각이 있습니다.
        LastTransitSchedule fallbackData = LastTransitSchedule.builder()
                .transitType("SUBWAY")
                .cacheKey(SUBWAY_STATION_ID)
                .dayType(DAY_TYPE_CONVERTED)
                .lastTime("23:42")  // 3분 전 저장값
                .updatedAt(LocalDateTime.now().minusMinutes(3))
                .build();

        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "SUBWAY", SUBWAY_STATION_ID, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.of(fallbackData));

        // when: 전철 막차 시각을 조회합니다.
        String result = transitCacheService.getSubwayLastTime(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);

        // then: DB Fallback 값이 반환되어야 합니다.
        assertThat(result).isEqualTo("23:42");                                         // Fallback 값이 반환되는지
        verify(odsayClient, times(1))
                .searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);           // API는 여전히 호출됨
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "SUBWAY", SUBWAY_STATION_ID, DAY_TYPE_CONVERTED);            // DB 조회 1회
    }


    // ── 3번: getSubwayLastTime() - API 실패 + DB도 없음 → null 반환 ─────────────────
    //
    // 이 테스트가 필요한 이유:
    //   API 장애이면서 DB에 저장된 데이터도 없는 최악의 경우,
    //   null을 반환해 클라이언트가 에러 처리할 수 있도록 해야 합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSubwayLastTime() - API 실패 + DB도 없음 → null 반환")
    void getSubwayLastTime_API_실패_DB도_없음_null_반환() {
        // given: API가 null을 반환하고, DB에도 데이터가 없습니다.
        when(odsayClient.searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY))
                .thenReturn(null);

        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "SUBWAY", SUBWAY_STATION_ID, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.empty());  // DB에 데이터 없음

        // when: 전철 막차 시각을 조회합니다.
        String result = transitCacheService.getSubwayLastTime(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);

        // then: null이 반환되어야 합니다.
        assertThat(result).isNull();                                                   // null이 반환되는지
        verify(odsayClient, times(1))
                .searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);           // API 호출 시도
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "SUBWAY", SUBWAY_STATION_ID, DAY_TYPE_CONVERTED);            // DB 조회 시도
    }


    // ── 4번: getSeoulBusLastTime() - API 성공 → transitCacheWriter.saveOrUpdate() 호출 ────
    //
    // 이 테스트가 필요한 이유:
    //   API 성공 시 즉시 DB에 저장(Lazy Caching)하는지 확인합니다.
    //   이를 통해 다음 장애 발생 시 Fallback 데이터가 준비되도록 합니다.
    //   Mock verify를 사용해 saveOrUpdate()가 정확히 1회 호출되는지 검증합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSeoulBusLastTime() - API 성공 → transitCacheWriter.saveOrUpdate() 1회 호출")
    void getSeoulBusLastTime_API_성공_DB_저장() {
        // given: 서울버스 API가 LocalDateTime을 반환합니다.
        LocalDateTime mockTime = LocalDateTime.parse("2026-07-02T23:45:00");
        when(seoulBusArrivalClient.getLastBusTime(SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY))
                .thenReturn(mockTime);

        // when: 서울버스 막차 시각을 조회합니다.
        String result = transitCacheService.getSeoulBusLastTime(
                SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);

        // then: 정상값이 반환되고, DB 저장이 1회 호출되어야 합니다.
        assertThat(result).isEqualTo("23:45");                                         // 응답 값이 정상인지
        verify(seoulBusArrivalClient, times(1))
                .getLastBusTime(SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);               // API 호출 1회
        verify(transitCacheWriter, times(1))
                .saveOrUpdate("BUS_SEOUL", SEOUL_BUS_CACHE_KEY, DAY_TYPE_CONVERTED, "23:45"); // DB 저장 1회
    }


    // ── 5번: getSeoulBusLastTime() - API 실패 → DB Fallback 값 반환 ──────────────────
    //
    // 이 테스트가 필요한 이유:
    //   API가 null을 반환했을 때(장애) DB Fallback이 정상 작동하는지 확인합니다.
    //   이 경우 transitCacheWriter.saveOrUpdate()는 호출되면 안 됩니다.
    //   (API가 실패했는데 DB에 저장할 수 없음)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSeoulBusLastTime() - DB 히트 → 바로 반환 (API 호출 없음)")
    void getSeoulBusLastTime_DB_히트_바로반환() {
        // given: DB에는 이전 저장값이 있습니다 (배치 데이터).
        LastTransitSchedule fallbackData = LastTransitSchedule.builder()
                .transitType("BUS_SEOUL")
                .cacheKey(SEOUL_BUS_CACHE_KEY)
                .dayType(DAY_TYPE_CONVERTED)
                .lastTime("23:40")
                .updatedAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "BUS_SEOUL", SEOUL_BUS_CACHE_KEY, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.of(fallbackData));

        // when: 서울버스 막차 시각을 조회합니다.
        String result = transitCacheService.getSeoulBusLastTime(
                SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);

        // then: DB 값이 바로 반환되고, API 호출은 발생하지 않아야 합니다.
        assertThat(result).isEqualTo("23:40");                                         // DB 값이 반환되는지
        verify(seoulBusArrivalClient, never())
                .getLastBusTime(SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);               // API 호출 안 함 (배치 처리)
        verify(transitCacheWriter, never())
                .saveOrUpdate(anyString(), anyString(), anyString(), anyString());   // DB 저장은 호출 안 됨 (이미 있음)
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "BUS_SEOUL", SEOUL_BUS_CACHE_KEY, DAY_TYPE_CONVERTED);       // DB 조회 1회
    }


    // ── 6번: getGyeonggiBusLastTime() - API 성공 → transitCacheWriter.saveOrUpdate() 호출 ────
    //
    // 이 테스트가 필요한 이유:
    //   경기버스도 서울버스와 동일한 Lazy Caching 패턴을 따르는지 확인합니다.
    //   Mock verify를 사용해 saveOrUpdate()가 정확히 1회 호출되는지 검증합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getGyeonggiBusLastTime() - API 성공 → transitCacheWriter.saveOrUpdate() 1회 호출")
    void getGyeonggiBusLastTime_API_성공_DB_저장() {
        // given: 경기버스 API가 LocalDateTime을 반환합니다.
        LocalDateTime mockTime = LocalDateTime.parse("2026-07-02T23:50:00");
        when(gyeonggiBusRouteClient.getLastBusTime(GYEONGGI_BUS_ROUTE_ID))
                .thenReturn(mockTime);

        // when: 경기버스 막차 시각을 조회합니다.
        String result = transitCacheService.getGyeonggiBusLastTime(GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);

        // then: 정상값이 반환되고, DB 저장이 1회 호출되어야 합니다.
        assertThat(result).isEqualTo("23:50");                                         // 응답 값이 정상인지
        verify(gyeonggiBusRouteClient, times(1))
                .getLastBusTime(GYEONGGI_BUS_ROUTE_ID);                              // API 호출 1회
        verify(transitCacheWriter, times(1))
                .saveOrUpdate("BUS_GYEONGGI", GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_CONVERTED, "23:50"); // DB 저장 1회
    }


    // ── 7번: getGyeonggiBusLastTime() - API 실패(null) → DB Fallback 값 반환 ─────────────
    //
    // 이 테스트가 필요한 이유:
    //   경기버스도 API 실패 시 DB Fallback이 정상 작동하는지 확인합니다.
    //   서울버스와 동일한 패턴의 장애 복구 메커니즘을 검증합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getGyeonggiBusLastTime() - DB 히트 → 바로 반환 (API 호출 없음)")
    void getGyeonggiBusLastTime_DB_히트_바로반환() {
        // given: DB에는 이전 저장값이 있습니다 (배치 데이터).
        LastTransitSchedule fallbackData = LastTransitSchedule.builder()
                .transitType("BUS_GYEONGGI")
                .cacheKey(GYEONGGI_BUS_ROUTE_ID)
                .dayType(DAY_TYPE_CONVERTED)
                .lastTime("23:48")
                .updatedAt(LocalDateTime.now().minusMinutes(2))
                .build();

        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "BUS_GYEONGGI", GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.of(fallbackData));

        // when: 경기버스 막차 시각을 조회합니다.
        String result = transitCacheService.getGyeonggiBusLastTime(GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);

        // then: DB 값이 바로 반환되고, API 호출은 발생하지 않아야 합니다.
        assertThat(result).isEqualTo("23:48");                                         // DB 값이 반환되는지
        verify(gyeonggiBusRouteClient, never())
                .getLastBusTime(GYEONGGI_BUS_ROUTE_ID);                              // API 호출 안 함 (배치 처리)
        verify(transitCacheWriter, never())
                .saveOrUpdate(anyString(), anyString(), anyString(), anyString());   // DB 저장은 호출 안 됨 (이미 있음)
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "BUS_GYEONGGI", GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_CONVERTED);   // DB 조회 1회
    }


    // ── 8번: getGyeonggiBusLastTime() - API 실패 + DB도 없음 → null 반환 ──────────────────
    //
    // 이 테스트가 필요한 이유:
    //   경기버스의 최악의 경우(API 장애 + DB 데이터 없음)에서도 안전하게 null을 반환하는지 확인합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getGyeonggiBusLastTime() - API 실패 + DB도 없음 → null 반환")
    void getGyeonggiBusLastTime_API_실패_DB도_없음_null_반환() {
        // given: API가 null을 반환하고, DB에도 데이터가 없습니다.
        when(gyeonggiBusRouteClient.getLastBusTime(GYEONGGI_BUS_ROUTE_ID))
                .thenReturn(null);

        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "BUS_GYEONGGI", GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.empty());  // DB에 데이터 없음

        // when: 경기버스 막차 시각을 조회합니다.
        String result = transitCacheService.getGyeonggiBusLastTime(GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);

        // then: null이 반환되어야 합니다.
        assertThat(result).isNull();                                                   // null이 반환되는지
        verify(gyeonggiBusRouteClient, times(1))
                .getLastBusTime(GYEONGGI_BUS_ROUTE_ID);                              // API 호출 시도
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "BUS_GYEONGGI", GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_CONVERTED);   // DB 조회 시도
    }


    // ── 9번: getSubwayLastTime() - 예외 발생(RuntimeException) → DB Fallback 시도 ────────
    //
    // 이 테스트가 필요한 이유:
    //   API 호출 중 예외가 발생했을 때(타임아웃, 네트워크 오류 등)
    //   정상적으로 DB Fallback으로 복구되는지 확인합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSubwayLastTime() - 예외 발생(RuntimeException) → DB Fallback 시도")
    void getSubwayLastTime_예외_발생_DB_Fallback() {
        // given: API가 예외를 던집니다.
        when(odsayClient.searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY))
                .thenThrow(new RuntimeException("API 타임아웃"));

        // DB에는 이전 저장값이 있습니다.
        LastTransitSchedule fallbackData = LastTransitSchedule.builder()
                .transitType("SUBWAY")
                .cacheKey(SUBWAY_STATION_ID)
                .dayType(DAY_TYPE_CONVERTED)
                .lastTime("23:41")
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "SUBWAY", SUBWAY_STATION_ID, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.of(fallbackData));

        // when: 전철 막차 시각을 조회합니다.
        String result = transitCacheService.getSubwayLastTime(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);

        // then: DB Fallback 값이 반환되어야 합니다.
        assertThat(result).isEqualTo("23:41");                                         // Fallback 값이 반환되는지
        verify(odsayClient, times(1))
                .searchSubwaySchedule(SUBWAY_STATION_ID, DAY_TYPE_WEEKDAY);           // API 호출 시도
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "SUBWAY", SUBWAY_STATION_ID, DAY_TYPE_CONVERTED);            // DB 조회 1회
    }


    // ── 10번: getSeoulBusLastTime() - 예외 발생(RuntimeException) → DB Fallback 시도 ──────
    //
    // 이 테스트가 필요한 이유:
    //   서울버스 API에서 예외 발생 시에도 안정적으로 DB Fallback으로 복구됨을 검증합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSeoulBusLastTime() - DB 미스 + API 예외 → null 반환 (예외 전파 없음)")
    void getSeoulBusLastTime_DB_미스_API_예외() {
        // given: DB에는 데이터가 없습니다 (배치 미실행 또는 새로운 경로).
        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "BUS_SEOUL", SEOUL_BUS_CACHE_KEY, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.empty());

        // API가 예외를 던집니다.
        when(seoulBusArrivalClient.getLastBusTime(SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY))
                .thenThrow(new RuntimeException("API 연결 오류"));

        // when: 서울버스 막차 시각을 조회합니다.
        String result = transitCacheService.getSeoulBusLastTime(
                SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);

        // then: null이 반환되어야 합니다 (예외 처리됨).
        assertThat(result).isNull();                                                   // null 반환 (예외 처리)
        verify(seoulBusArrivalClient, times(1))
                .getLastBusTime(SEOUL_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);               // API 호출 1회 (DB 미스)
        verify(transitCacheWriter, never())
                .saveOrUpdate(anyString(), anyString(), anyString(), anyString());   // DB 저장은 호출 안 됨
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "BUS_SEOUL", SEOUL_BUS_CACHE_KEY, DAY_TYPE_CONVERTED);       // DB 조회 1회 (초기 미스)
    }


    // ── 11번: getGyeonggiBusLastTime() - 예외 발생(RuntimeException) → DB Fallback 시도 ───
    //
    // 이 테스트가 필요한 이유:
    //   경기버스 API에서 예외 발생 시에도 안정적으로 DB Fallback으로 복구됨을 검증합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getGyeonggiBusLastTime() - DB 미스 + API 예외 → null 반환 (예외 전파 없음)")
    void getGyeonggiBusLastTime_DB_미스_API_예외() {
        // given: DB에는 데이터가 없습니다 (배치 미실행 또는 새로운 경로).
        when(lastTransitScheduleRepository.findByTransitTypeAndCacheKeyAndDayType(
                "BUS_GYEONGGI", GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_CONVERTED))
                .thenReturn(Optional.empty());

        // API가 예외를 던집니다.
        when(gyeonggiBusRouteClient.getLastBusTime(GYEONGGI_BUS_ROUTE_ID))
                .thenThrow(new RuntimeException("API 서버 오류"));

        // when: 경기버스 막차 시각을 조회합니다.
        String result = transitCacheService.getGyeonggiBusLastTime(GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);

        // then: null이 반환되어야 합니다 (예외 처리됨).
        assertThat(result).isNull();                                                   // null 반환 (예외 처리)
        verify(gyeonggiBusRouteClient, times(1))
                .getLastBusTime(GYEONGGI_BUS_ROUTE_ID);                              // API 호출 1회 (DB 미스)
        verify(transitCacheWriter, never())
                .saveOrUpdate(anyString(), anyString(), anyString(), anyString());   // DB 저장은 호출 안 됨
        verify(lastTransitScheduleRepository, times(1))
                .findByTransitTypeAndCacheKeyAndDayType(
                        "BUS_GYEONGGI", GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_CONVERTED);   // DB 조회 1회 (초기 미스)
    }


    // ── 12번: getMetrics() - API 성공 1회 후 메트릭 문자열에 "API 성공" 포함 확인 ──────────
    //
    // 이 테스트가 필요한 이유:
    //   성과 측정 시스템이 정상적으로 작동하는지 확인합니다.
    //   API 성공 시 카운터가 증가하고, 메트릭 문자열에 포함되는지 검증합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMetrics() - API 성공 1회 후 메트릭 문자열에 'API 성공' 포함 확인")
    void getMetrics_API_성공_후_메트릭_문자열_확인() {
        // given: 경기버스 API가 성공합니다.
        LocalDateTime mockTime = LocalDateTime.parse("2026-07-02T23:50:00");
        when(gyeonggiBusRouteClient.getLastBusTime(GYEONGGI_BUS_ROUTE_ID))
                .thenReturn(mockTime);

        // when: 경기버스 조회 후 메트릭을 확인합니다.
        transitCacheService.getGyeonggiBusLastTime(GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);
        String metrics = TransitCacheService.getMetrics();

        // then: 메트릭 문자열에 "API 성공" 관련 정보가 포함되어야 합니다.
        assertThat(metrics)
                .isNotEmpty()                                                          // 메트릭이 비어있지 않은지
                .contains("성공");                                                     // "성공"이라는 단어 포함
    }


    // ── 13번: resetMetrics() - 호출 후 getMetrics() 결과가 0회로 초기화 확인 ────────────────
    //
    // 이 테스트가 필요한 이유:
    //   resetMetrics()가 제대로 카운터를 초기화하는지 확인합니다.
    //   테스트 간 상태 독립성을 보장하기 위해 필수적인 기능입니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetMetrics() - 호출 후 getMetrics() 결과가 0회로 초기화 확인")
    void resetMetrics_메트릭_초기화() {
        // given: 경기버스 API가 성공합니다.
        LocalDateTime mockTime = LocalDateTime.parse("2026-07-02T23:50:00");
        when(gyeonggiBusRouteClient.getLastBusTime(GYEONGGI_BUS_ROUTE_ID))
                .thenReturn(mockTime);

        // 첫 번째 호출: 메트릭 누적
        transitCacheService.getGyeonggiBusLastTime(GYEONGGI_BUS_ROUTE_ID, DAY_TYPE_WEEKDAY);
        String metricsAfterCall = TransitCacheService.getMetrics();

        // when: 메트릭을 초기화합니다.
        TransitCacheService.resetMetrics();
        String metricsAfterReset = TransitCacheService.getMetrics();

        // then: 초기화 전에는 데이터가 있고, 초기화 후에는 초기값으로 돌아가야 합니다.
        assertThat(metricsAfterCall).isNotEmpty();                                     // 초기화 전: 메트릭 존재
        assertThat(metricsAfterReset)
                .isNotEmpty()                                                          // 초기화 후: 메트릭 문자열 존재
                .contains("0");                                                        // 모든 카운터가 0으로 초기화
    }
}

package com.lasttrain.route.service;

import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import com.lasttrain.route.dto.RouteResponse;
import com.lasttrain.route.external.OdsayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 출발지/목적지 좌표로 막차 경로를 조회하는 서비스입니다.
 *
 * ── 전체 처리 흐름 ────────────────────────────────────────────────────────────
 *   1. Redis 캐시 키 생성 (좌표 + 요일 유형 기준)
 *   2. 캐시에 ODsay 경로 JSON이 있으면 그대로 사용, 없으면 ODsay API 호출 후 캐시 저장
 *   3. LastTrainCalculator로 막차 탑승 마감 시각 계산
 *   4. 계산된 경로가 없으면 NO_ROUTE_FOUND 예외 발생
 *   5. departureDeadline 기준 내림차순으로 정렬해서 응답 조립
 * ────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    // 한국 시간대 기준으로 "오늘"과 "현재 시각"을 계산하기 위해 사용합니다.
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // ODsay 경로 조회 결과를 캐시에 보관하는 시간(TTL)입니다.
    // 1시간으로 설정한 이유: 대중교통 노선 정보는 자주 바뀌지 않으면서도
    // 운행 정보 갱신을 어느 정도 반영할 수 있는 적절한 기간입니다.
    private static final long CACHE_TTL_HOURS = 1;

    private final OdsayClient odsayClient;
    private final LastTrainCalculator lastTrainCalculator;
    private final StringRedisTemplate redisTemplate;

    /**
     * 출발지에서 목적지까지의 막차 경로를 조회합니다.
     *
     * @param originLat  출발지 위도
     * @param originLng  출발지 경도
     * @param originName 출발지 명칭 (응답에 그대로 사용)
     * @param destLat    목적지 위도
     * @param destLng    목적지 경도
     * @param destName   목적지 명칭 (응답에 그대로 사용)
     * @return 막차 경로 조회 결과
     */
    @Transactional(readOnly = true)
    public RouteResponse findLastTrainRoutes(double originLat, double originLng, String originName,
                                              double destLat, double destLng, String destName) {

        // 현재 시각은 Asia/Seoul 기준으로 한 번만 계산해서 이후 로직에서 그대로 사용합니다.
        LocalDateTime now = LocalDateTime.now(SEOUL);
        String dayType = resolveDayType(now.getDayOfWeek());

        // ── 1단계: 캐시 키 생성 ────────────────────────────────────────────────
        // 같은 출발지/목적지/요일 유형이면 같은 캐시 키를 사용하게 됩니다.
        String cacheKey = buildCacheKey(originLng, originLat, destLng, destLat, dayType);

        // ── 2단계: 캐시 확인 ──────────────────────────────────────────────────
        String routeJson = redisTemplate.opsForValue().get(cacheKey);

        if (routeJson == null) {
            // 캐시 미스: ODsay API를 직접 호출하고 결과를 캐시에 저장합니다.
            log.debug("[RouteService] 캐시 미스, ODsay API 호출: key={}", cacheKey);
            routeJson = odsayClient.searchRoute(originLng, originLat, destLng, destLat);
            redisTemplate.opsForValue().set(cacheKey, routeJson, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } else {
            // 캐시 히트: ODsay API를 호출하지 않고 캐시된 JSON을 그대로 사용합니다.
            log.debug("[RouteService] 캐시 히트: key={}", cacheKey);
        }

        // ── 3단계: 막차 탑승 마감 시각 계산 ───────────────────────────────────────
        // 캐시 히트/미스 여부와 관계없이, 막차 마감 시각은 "현재 시각" 기준으로
        // 매 요청마다 새로 계산해야 합니다. (경로 정보는 캐시해도, 시간 계산은 항상 최신이어야 함)
        List<RouteResponse.RouteItem> routes = lastTrainCalculator.calculate(routeJson, now);

        // ── 4단계: 결과 없음 처리 ─────────────────────────────────────────────
        if (routes.isEmpty()) {
            throw new AppException(ErrorCode.NO_ROUTE_FOUND);
        }

        // ── 5단계: departureDeadline 내림차순 정렬 후 응답 조립 ────────────────────
        // "늦은 막차 우선"으로 보여주기 위해 마감 시각이 늦은 경로부터 정렬합니다.
        List<RouteResponse.RouteItem> sortedRoutes = routes.stream()
                .sorted(Comparator.comparing(RouteResponse.RouteItem::departureDeadline).reversed())
                .toList();

        return new RouteResponse(originName, destName, now.toLocalDate(), dayType, sortedRoutes);
    }

    /**
     * Redis 캐시 키를 생성합니다.
     * 형식: "odsay:route:{originLng}:{originLat}:{destLng}:{destLat}:{dayType}"
     */
    private String buildCacheKey(double originLng, double originLat,
                                  double destLng, double destLat, String dayType) {
        return "odsay:route:" + originLng + ":" + originLat + ":" + destLng + ":" + destLat + ":" + dayType;
    }

    /**
     * 요일을 응답용 dayType 문자열로 변환합니다.
     *   평일(월~금) → "WEEKDAY"
     *   주말(토, 일) → "WEEKEND"
     */
    private String resolveDayType(DayOfWeek dayOfWeek) {
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return "WEEKEND";
        }
        return "WEEKDAY";
    }
}

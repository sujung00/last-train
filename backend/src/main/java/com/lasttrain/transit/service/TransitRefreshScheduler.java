package com.lasttrain.transit.service;

import com.lasttrain.bus.external.GyeonggiBusRouteClient;
import com.lasttrain.bus.external.SeoulBusArrivalClient;
import com.lasttrain.route.external.OdsayClient;
import com.lasttrain.transit.domain.LastTransitSchedule;
import com.lasttrain.transit.domain.SubwayStationMaster;
import com.lasttrain.transit.repository.LastTransitScheduleRepository;
import com.lasttrain.transit.repository.SubwayStationMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 막차 시각 캐시 갱신 스케줄러
 *
 * 역할:
 *   1. 지하철: 수동 트리거로 모든 역의 막차 시각 갱신
 *   2. 버스: 매일 새벽 3시 자동 실행으로 버스 캐시 갱신
 *
 * 특징:
 *   - 캐시 갱신을 위해 직접 외부 API 호출 (TransitCacheService 우회)
 *   - TransitCacheWriter를 통해 DB에 저장
 *   - 별도 트랜잭션(REQUIRES_NEW)으로 안전하게 저장
 *
 * 사용:
 *   POST /admin/transit/refresh/subway → refreshSubway() 실행
 *   매일 새벽 3시 → refreshBus() 자동 실행
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransitRefreshScheduler {

    // DB 저장소
    private final SubwayStationMasterRepository subwayStationMasterRepository;
    private final LastTransitScheduleRepository lastTransitScheduleRepository;

    // 외부 API 클라이언트
    private final OdsayClient odsayClient;
    private final SeoulBusArrivalClient seoulBusArrivalClient;
    private final GyeonggiBusRouteClient gyeonggiBusRouteClient;

    // 캐시 저장 담당
    private final TransitCacheWriter transitCacheWriter;

    // 캐시 조회 및 JSON 파싱
    private final TransitCacheService transitCacheService;

    /**
     * 모든 전철역의 막차 시각을 갱신합니다.
     *
     * 동작:
     *   1. subway_station_master에서 모든 역 조회
     *   2. 각 역의 odsayStationId로 dayType 3가지(평일/토/일) 모두 갱신
     *   3. ODsay API를 직접 호출해서 최신 막차 시각 조회
     *   4. TransitCacheWriter를 통해 DB에 저장 (자동 갱신)
     *   5. 완료 로그 출력
     *
     * 호출 타이밍:
     *   - 수동 트리거: POST /admin/transit/refresh/subway
     *   - 용도: 관리자가 필요할 때 즉시 갱신
     *
     * 예시 로그:
     *   [INFO] 지하철 막차 시각 갱신 시작... (123개 역)
     *   [INFO] 지하철 막차 시각 갱신 완료: 123개 역
     */
    public void refreshSubway() {
        log.info("[TransitRefreshScheduler] 지하철 막차 시각 갱신 시작...");

        try {
            // 1단계: 모든 전철역 조회
            List<SubwayStationMaster> stations = subwayStationMasterRepository.findAll();
            log.debug("조회된 전철역: {}개", stations.size());

            // 2단계: 각 역에 대해 dayType(1=평일, 2=토, 3=일) 3가지 모두 갱신
            int successCount = 0;
            for (SubwayStationMaster station : stations) {
                String odsayStationId = station.getOdsayStationId();
                String stationName = station.getStationName();

                // dayType 1(평일), 2(토요일), 3(일요일) 모두 갱신
                for (String dayType : new String[]{"1", "2", "3"}) {
                    try {
                        // ODsay API 직접 호출 (TransitCacheService 우회)
                        // 캐시를 무시하고 항상 최신 데이터 조회
                        String scheduleJson = odsayClient.searchSubwaySchedule(odsayStationId, dayType);

                        if (scheduleJson != null) {
                            // JSON 파싱하여 막차 시각 추출 (TransitCacheService 사용)
                            String lastTime = transitCacheService.extractSubwayLastTime(scheduleJson, java.time.LocalDate.now(), dayType);

                            if (lastTime != null) {
                                // dayType을 영문으로 변환
                                String convertedDayType = convertDayType(dayType);
                                // DB에 저장 (새 트랜잭션으로 실행)
                                transitCacheWriter.saveOrUpdate("SUBWAY", odsayStationId, convertedDayType, lastTime);
                                successCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[TransitRefreshScheduler] 역 갱신 실패: stationName={}, odsayStationId={}, dayType={}, error={}",
                                stationName, odsayStationId, dayType, e.getMessage());
                    }
                }
            }

            log.info("[TransitRefreshScheduler] 지하철 막차 시각 갱신 완료: {}개 역 (dayType당 {}개 갱신)",
                     stations.size(), successCount);

        } catch (Exception e) {
            log.error("[TransitRefreshScheduler] 지하철 전체 갱신 실패", e);
        }
    }

    /**
     * 버스 캐시를 갱신합니다.
     *
     * 동작:
     *   1. last_transit_schedule에서 BUS_SEOUL, BUS_GYEONGGI 캐시만 조회
     *   2. 각 캐시의 cacheKey를 파싱하여 API 호출에 필요한 정보 추출
     *   3. 외부 API 호출해서 최신 막차 시각 조회
     *   4. TransitCacheWriter를 통해 DB에 저장 (자동 갱신)
     *   5. 완료 로그 출력
     *
     * 실행 타이밍:
     *   - @Scheduled(cron = "0 0 3 * * *"): 매일 새벽 3시
     *   - 용도: 야간에 자동으로 최신 버스 정보 갱신
     *
     * cacheKey 형식:
     *   - BUS_SEOUL: "정류소ID:노선ID:순번" (예: "136:100100578:29")
     *   - BUS_GYEONGGI: "routeId" (예: "200000037")
     *
     * 예시 로그:
     *   [INFO] 버스 막차 시각 갱신 시작... (BUS_SEOUL: 50개, BUS_GYEONGGI: 30개)
     *   [INFO] 버스 막차 시각 갱신 완료: 80개 노선
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void refreshBus() {
        log.info("[TransitRefreshScheduler] 버스 막차 시각 갱신 시작... (매일 새벽 3시)");

        try {
            // 1단계: 모든 버스 캐시 조회 (BUS_SEOUL, BUS_GYEONGGI)
            List<LastTransitSchedule> allBusCache = lastTransitScheduleRepository.findAll();
            List<LastTransitSchedule> busCache = allBusCache.stream()
                    .filter(cache -> cache.getTransitType().startsWith("BUS_"))
                    .toList();

            log.debug("조회된 버스 캐시: {}개", busCache.size());

            int successCount = 0;

            // 2단계: 각 버스 캐시마다 API 호출하여 갱신
            for (LastTransitSchedule cache : busCache) {
                String transitType = cache.getTransitType();
                String cacheKey = cache.getCacheKey();
                String dayType = cache.getDayType();

                try {
                    LocalDateTime lastBusTime = null;

                    if ("BUS_SEOUL".equals(transitType)) {
                        // ── 서울 버스 ────────────────────────────────────────────────────────
                        // cacheKey = "정류소ID:노선ID:순번" 형식
                        // 예) "136:100100578:29"
                        String[] parts = cacheKey.split(":");
                        if (parts.length == 3) {
                            String stId = parts[0];
                            String busRouteId = parts[1];
                            String ord = parts[2];

                            lastBusTime = seoulBusArrivalClient.getLastBusTime(stId, busRouteId, ord);
                        } else {
                            log.warn("[TransitRefreshScheduler] 서울버스 cacheKey 형식 오류: {}", cacheKey);
                        }

                    } else if ("BUS_GYEONGGI".equals(transitType)) {
                        // ── 경기 버스 ────────────────────────────────────────────────────────
                        // cacheKey = routeId 그대로
                        // 예) "200000037"
                        String routeId = cacheKey;
                        lastBusTime = gyeonggiBusRouteClient.getLastBusTime(routeId);
                    }

                    // API 호출 성공 시 DB에 저장
                    if (lastBusTime != null) {
                        String lastTime = lastBusTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                        transitCacheWriter.saveOrUpdate(transitType, cacheKey, dayType, lastTime);
                        successCount++;
                    }

                } catch (Exception e) {
                    log.warn("[TransitRefreshScheduler] 버스 갱신 실패: transitType={}, cacheKey={}, dayType={}, error={}",
                             transitType, cacheKey, dayType, e.getMessage());
                }
            }

            log.info("[TransitRefreshScheduler] 버스 막차 시각 갱신 완료: {}개 노선", successCount);

        } catch (Exception e) {
            log.error("[TransitRefreshScheduler] 버스 전체 갱신 실패", e);
        }
    }

    /**
     * dayType을 숫자에서 영문으로 변환합니다.
     *
     * @param dayType 숫자 형식 ("1", "2", "3")
     * @return 영문 형식 ("WEEKDAY", "SATURDAY", "SUNDAY")
     */
    private String convertDayType(String dayType) {
        return switch (dayType) {
            case "1" -> "WEEKDAY";
            case "2" -> "SATURDAY";
            case "3" -> "SUNDAY";
            default -> "UNKNOWN";
        };
    }
}

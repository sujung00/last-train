package com.lasttrain.transit.service;

import com.lasttrain.bus.domain.BusRouteMaster;
import com.lasttrain.bus.external.GyeonggiBusRouteClient;
import com.lasttrain.bus.external.SeoulBusArrivalClient;
import com.lasttrain.bus.repository.BusRouteMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 버스 막차 시각 배치 갱신 스케줄러
 *
 * 역할:
 *   - 매일 자정에 모든 활성 버스 노선의 막차 시각을 미리 계산하여 DB에 저장
 *   - 3개의 dayType (평일/토/일)별로 API 호출
 *   - 새로운 노선도 마스터 데이터에 추가되면 자동으로 배치에 포함
 *
 * 효과:
 *   - 사용자 요청 시 DB에서 즉시 조회 (매우 빠름)
 *   - API 호출은 배치에서만 수행 (부하 분산)
 *   - API 장애 시에도 마지막 저장값으로 대응
 *
 * 동작 흐름:
 *   1. DB에서 모든 활성 버스 노선 조회 (BusRouteMaster)
 *   2. 각 노선별 dayType 3가지(평일/토/일) 처리
 *   3. 외부 API 호출하여 막차 시각 조회
 *   4. LastTransitSchedule 테이블에 저장/갱신
 *   5. 완료 로그 및 메트릭 출력
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusBatchScheduler {

    // DB에서 마스터 데이터 조회
    private final BusRouteMasterRepository busRouteMasterRepository;

    // 외부 API 클라이언트
    private final SeoulBusArrivalClient seoulBusArrivalClient;
    private final GyeonggiBusRouteClient gyeonggiBusRouteClient;

    // DB 저장 담당
    private final TransitCacheWriter transitCacheWriter;

    /**
     * 매일 자정(00:00)에 실행
     *
     * 일정:
     *   - "0 0 0 * * *" = 매일 00:00:00
     *   - 예: 2026-07-01 00:00:00에 첫 실행, 이후 매일 반복
     *
     * 실행 시간:
     *   - 버스 노선 개수에 따라 다름
     *   - 예: 1000개 노선 × 3 dayType = 3000번 API 호출
     *   - 약 30~60분 소요 예상
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void batchUpdateBusSchedules() {
        long startTime = System.currentTimeMillis();
        log.info("[BusBatchScheduler] 버스 막차 배치 갱신 시작 (매일 자정)");

        try {
            // ── 1단계: 모든 활성 버스 노선 조회 ────────────────────────────────────────
            List<BusRouteMaster> seoulRoutes = busRouteMasterRepository
                .findByTransitTypeAndStatus("BUS_SEOUL", "ACTIVE");
            List<BusRouteMaster> gyeonggiRoutes = busRouteMasterRepository
                .findByTransitTypeAndStatus("BUS_GYEONGGI", "ACTIVE");

            List<BusRouteMaster> allRoutes = new java.util.ArrayList<>();
            allRoutes.addAll(seoulRoutes);
            allRoutes.addAll(gyeonggiRoutes);

            int totalRoutes = allRoutes.size();
            log.debug("[BusBatchScheduler] 조회된 활성 노선: {}개 (서울: {}개, 경기: {}개)",
                totalRoutes, seoulRoutes.size(), gyeonggiRoutes.size());

            // ── 2단계: 각 노선별 3개 dayType 갱신 ──────────────────────────────────────
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (BusRouteMaster route : allRoutes) {
                // dayType 1(평일), 2(토요일), 3(일요일) 처리
                for (String dayType : new String[]{"1", "2", "3"}) {
                    try {
                        String convertedDayType = convertDayType(dayType);
                        LocalDateTime lastBusTime = null;

                        // ── API 호출 ──────────────────────────────────────────────────
                        if ("BUS_SEOUL".equals(route.getTransitType())) {
                            // 서울 버스: SeoulBusArrivalClient 호출
                            lastBusTime = seoulBusArrivalClient
                                .getLastBusTime(route.getRouteId(), dayType);

                        } else if ("BUS_GYEONGGI".equals(route.getTransitType())) {
                            // 경기 버스: GyeonggiBusRouteClient 호출
                            lastBusTime = gyeonggiBusRouteClient
                                .getLastBusTime(route.getRouteId());
                        }

                        // ── DB 저장 ───────────────────────────────────────────────────
                        if (lastBusTime != null) {
                            String lastTime = lastBusTime
                                .format(DateTimeFormatter.ofPattern("HH:mm"));
                            transitCacheWriter.saveOrUpdate(
                                route.getTransitType(),
                                route.getRouteId(),
                                convertedDayType,
                                lastTime
                            );
                            successCount.incrementAndGet();

                            log.debug(
                                "[BusBatchScheduler] 저장 성공: type={}, routeId={}, dayType={}, time={}",
                                route.getTransitType(), route.getRouteId(),
                                convertedDayType, lastTime);

                        } else {
                            failureCount.incrementAndGet();
                            log.warn(
                                "[BusBatchScheduler] API 응답 없음: type={}, routeId={}, dayType={}",
                                route.getTransitType(), route.getRouteId(), dayType);
                        }

                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.warn(
                            "[BusBatchScheduler] 갱신 실패: type={}, routeId={}, dayType={}, error={}",
                            route.getTransitType(), route.getRouteId(), dayType,
                            e.getMessage());
                    }
                }
            }

            // ── 3단계: 완료 로그 ───────────────────────────────────────────────────────
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            log.info(
                "[BusBatchScheduler] 버스 막차 배치 갱신 완료\n" +
                "  총 노선: {}개\n" +
                "  성공: {}회 (dayType별)\n" +
                "  실패: {}회\n" +
                "  소요 시간: {}ms ({:.2f}초)",
                totalRoutes, successCount.get(), failureCount.get(),
                elapsedTime, elapsedTime / 1000.0);

        } catch (Exception e) {
            log.error("[BusBatchScheduler] 배치 갱신 중 전체 오류 발생", e);
        }
    }

    /**
     * dayType을 숫자에서 영문으로 변환
     *
     * @param dayType 요일 타입 숫자 ("1", "2", "3")
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

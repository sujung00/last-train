package com.lasttrain.transit.service;

import com.lasttrain.transit.domain.LastTransitSchedule;
import com.lasttrain.transit.repository.LastTransitScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 스케줄러 전용 DB 저장/갱신 서비스
 *
 * 역할:
 *   - DB에 캐시를 저장하거나 갱신하는 책임만 담당
 *   - 스케줄러(SubwayStationLoader, TransitRefreshScheduler)에서만 호출됨
 *   - 사용자 요청 흐름에서는 호출되지 않음 (실시간 API → Fallback 구조이므로)
 *   - @Transactional(propagation = REQUIRES_NEW)로 독립적인 트랜잭션 실행
 *
 * 데이터 적재 시점:
 *   - SubwayStationLoader: 앱 시작 시 마스터 데이터 초기 적재
 *   - TransitRefreshScheduler: 정기적으로 스케줄러 실행하여 DB 갱신
 *
 * 책임 분리 (Single Responsibility Principle):
 *   - TransitCacheService: 실시간 API 호출 + Fallback 조회 로직
 *   - TransitCacheWriter: DB 저장/갱신만 (쓰기 책임)
 *
 * 사용 예시 (스케줄러에서만 호출):
 *   // 캐시 저장 또는 갱신
 *   transitCacheWriter.saveOrUpdate("SUBWAY", "136", "WEEKDAY", "23:45");
 *   transitCacheWriter.saveOrUpdate("BUS_SEOUL", "136:100100578:29", "WEEKDAY", "23:50");
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransitCacheWriter {

    private final LastTransitScheduleRepository lastTransitScheduleRepository;

    /**
     * 막차 캐시를 DB에 저장하거나 갱신합니다.
     *
     * 동작:
     *   1. DB에서 해당 캐시 레코드 조회 (transitType + cacheKey + dayType 조합)
     *   2. 있으면: 막차 시각이 변경된 경우에만 UPDATE (불필요한 DB write 방지)
     *   3. 없으면: 새로운 레코드 생성 및 저장
     *
     * 트랜잭션:
     *   - @Transactional(propagation = REQUIRES_NEW)
     *   - 부모 트랜잭션과 분리된 새 트랜잭션에서 실행
     *   - INSERT/UPDATE 작업을 안전하게 수행
     *   - 트랜잭션 완료 후 자동 커밋
     *
     * 예시:
     *   // 1. 새 캐시 저장
     *   saveOrUpdate("SUBWAY", "136", "WEEKDAY", "23:45")
     *   → DB에 INSERT (첫 조회 시)
     *
     *   // 2. 기존 캐시 갱신 (막차 시각 변경 시)
     *   saveOrUpdate("SUBWAY", "136", "WEEKDAY", "23:50")
     *   → DB의 기존 레코드를 23:50으로 UPDATE
     *   → updatedAt = LocalDateTime.now() 자동 갱신
     *
     *   // 3. 기존 캐시 동일 (갱신 스킵)
     *   saveOrUpdate("SUBWAY", "136", "WEEKDAY", "23:45")
     *   → 기존 값이 이미 "23:45"이면 UPDATE 스킵
     *   → 불필요한 DB write 방지
     *
     * @param transitType 대중교통 타입 ("SUBWAY", "BUS_SEOUL", "BUS_GYEONGGI")
     * @param cacheKey 캐시 키
     *                 - SUBWAY: ODsay stationId (예: "136", "729")
     *                 - BUS_SEOUL: "정류소ID:노선ID:순번" (예: "136:100100578:29")
     *                 - BUS_GYEONGGI: 경기버스 routeId (예: "200000037")
     * @param dayType 요일 타입 ("WEEKDAY", "SATURDAY", "SUNDAY")
     * @param lastTime 막차 시간 (HH:mm 형식, 예: "23:45")
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOrUpdate(String transitType, String cacheKey, String dayType, String lastTime) {
        // DB에서 기존 캐시 레코드 조회
        // 같은 (transitType, cacheKey, dayType) 조합의 레코드가 있는지 확인
        Optional<LastTransitSchedule> existing = lastTransitScheduleRepository
            .findByTransitTypeAndCacheKeyAndDayType(transitType, cacheKey, dayType);

        if (existing.isPresent()) {
            // ── 기존 레코드 있음: 막차 시각이 다를 경우에만 갱신 ──────────────────────
            if (!existing.get().getLastTime().equals(lastTime)) {
                // 막차 시각이 변경된 경우에만 UPDATE
                log.debug("캐시 업데이트: transitType={}, cacheKey={}, dayType={}, lastTime={} → {}",
                         transitType, cacheKey, dayType, existing.get().getLastTime(), lastTime);
                existing.get().updateLastTime(lastTime);
                lastTransitScheduleRepository.save(existing.get());
            } else {
                // 막차 시각이 동일하면 스킵 (불필요한 DB write 방지)
                log.debug("캐시 동일, 업데이트 스킵: transitType={}, cacheKey={}, dayType={}, lastTime={}",
                         transitType, cacheKey, dayType, lastTime);
            }
        } else {
            // ── 새로운 레코드: 신규 저장 ──────────────────────────────────────────────
            // 처음 조회한 막차 정보를 DB에 저장
            log.debug("캐시 신규 저장: transitType={}, cacheKey={}, dayType={}, lastTime={}",
                     transitType, cacheKey, dayType, lastTime);
            LastTransitSchedule newSchedule = LastTransitSchedule.builder()
                .transitType(transitType)
                .cacheKey(cacheKey)
                .dayType(dayType)
                .lastTime(lastTime)
                .updatedAt(LocalDateTime.now())
                .build();
            lastTransitScheduleRepository.save(newSchedule);
        }
    }
}

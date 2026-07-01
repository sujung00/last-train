package com.lasttrain.transit.repository;

import com.lasttrain.transit.domain.LastTransitSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 막차 시간 캐시 Repository
 *
 * 역할:
 *   - LastTransitSchedule 엔티티를 데이터베이스와 연동
 *   - Spring Data JPA가 자동으로 구현 (개발자는 인터페이스만 정의)
 *
 * 제공되는 기본 메서드 (JpaRepository에서 자동 제공):
 *   - findById(Long id) : ID로 막차 정보 1개 조회
 *   - findAll() : 모든 막차 정보 조회
 *   - save(LastTransitSchedule) : 새 막차 정보 저장 또는 기존 데이터 수정
 *   - delete(LastTransitSchedule) : 막차 정보 삭제
 *   - count() : 저장된 막차 정보 개수 조회
 *
 * 커스텀 메서드:
 *   - findByTransitTypeAndCacheKeyAndDayType() : 특정 경로와 요일의 막차 시간 조회
 *
 * 사용 예시:
 *   @Autowired
 *   private LastTransitScheduleRepository repository;
 *
 *   public Optional<LastTransitSchedule> getLastTrain(String transitType, String cacheKey, String dayType) {
 *       // "SUBWAY", "1000_2000", "WEEKDAY" 조합으로 막차 시간 조회
 *       Optional<LastTransitSchedule> schedule = repository.findByTransitTypeAndCacheKeyAndDayType(
 *           "SUBWAY",
 *           "1000_2000",
 *           "WEEKDAY"
 *       );
 *
 *       if (schedule.isPresent()) {
 *           System.out.println("막차 시간: " + schedule.get().getLastTime()); // "23:45"
 *           System.out.println("마지막 갱신: " + schedule.get().getUpdatedAt());
 *       } else {
 *           System.out.println("조회된 막차 정보가 없음 → API에서 새로 조회 필요");
 *       }
 *   }
 *
 *   public void saveOrUpdateLastTrain() {
 *       Optional<LastTransitSchedule> existing = repository.findByTransitTypeAndCacheKeyAndDayType(
 *           "SUBWAY", "1000_2000", "WEEKDAY"
 *       );
 *
 *       if (existing.isPresent()) {
 *           // 기존 데이터 수정
 *           existing.get().updateLastTime("23:50");
 *           repository.save(existing.get());
 *       } else {
 *           // 새 데이터 저장
 *           LastTransitSchedule newSchedule = LastTransitSchedule.builder()
 *               .transitType("SUBWAY")
 *               .cacheKey("1000_2000")
 *               .dayType("WEEKDAY")
 *               .lastTime("23:45")
 *               .updatedAt(LocalDateTime.now())
 *               .build();
 *           repository.save(newSchedule);
 *       }
 *   }
 */
@Repository
public interface LastTransitScheduleRepository extends JpaRepository<LastTransitSchedule, Long> {

    /**
     * 특정 대중교통 타입, 경로, 요일 조합으로 막차 정보 조회
     *
     * SQL이 자동 생성됨:
     *   SELECT * FROM last_transit_schedule
     *   WHERE transit_type = ? AND cache_key = ? AND day_type = ?
     *   LIMIT 1
     *
     * @param transitType 대중교통 타입 (예: "SUBWAY", "BUS")
     * @param cacheKey 출발지_도착지 조합 (예: "1000_2000")
     * @param dayType 요일 타입 (예: "WEEKDAY", "SATURDAY", "SUNDAY")
     * @return 해당하는 막차 정보 (없으면 Optional.empty())
     *
     * 반환 타입이 Optional인 이유:
     *   - 데이터가 없을 수 있으므로 null 대신 Optional 사용
     *   - 클라이언트에서 isPresent()로 데이터 존재 여부 판단 가능
     */
    Optional<LastTransitSchedule> findByTransitTypeAndCacheKeyAndDayType(
        String transitType,
        String cacheKey,
        String dayType
    );
}

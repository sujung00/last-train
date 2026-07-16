package com.lasttrain.bus.repository;

import com.lasttrain.bus.domain.BusRouteMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 버스 노선 마스터 Repository
 *
 * 역할:
 *   - BusRouteMaster 엔티티를 DB와 연동
 *   - 스케줄러가 배치 갱신 대상 노선 조회에 사용
 *   - 관리자 기능 (노선 추가/수정/삭제)
 *
 * 사용 예시:
 *   // 1. 모든 활성 서울 버스 노선 조회
 *   List<BusRouteMaster> activeSeoulBuses =
 *       repository.findByTransitTypeAndStatus("BUS_SEOUL", "ACTIVE");
 *
 *   // 2. 특정 노선 조회
 *   Optional<BusRouteMaster> route =
 *       repository.findByTransitTypeAndRouteId("BUS_SEOUL", "100100053");
 *
 *   // 3. 경기도 버스 중 특정 코드의 노선만 조회
 *   List<BusRouteMaster> gyeonggiMain =
 *       repository.findByTransitTypeAndBusCityCode("BUS_GYEONGGI", 1050);
 */
@Repository
public interface BusRouteMasterRepository extends JpaRepository<BusRouteMaster, Long> {

    /**
     * 특정 타입과 상태의 모든 버스 노선 조회
     *
     * @param transitType 버스 타입 (BUS_SEOUL, BUS_GYEONGGI, BUS_INCHEON)
     * @param status 노선 상태 (ACTIVE, INACTIVE)
     * @return 해당하는 모든 버스 노선
     */
    List<BusRouteMaster> findByTransitTypeAndStatus(String transitType, String status);

    /**
     * 특정 타입의 모든 버스 노선 조회
     *
     * @param transitType 버스 타입
     * @return 해당 타입의 모든 노선
     */
    List<BusRouteMaster> findByTransitType(String transitType);

    /**
     * 특정 타입과 노선 ID로 단일 노선 조회
     *
     * @param transitType 버스 타입
     * @param routeId 노선 ID
     * @return 해당하는 노선 (없으면 Optional.empty())
     */
    Optional<BusRouteMaster> findByTransitTypeAndRouteId(String transitType, String routeId);

    /**
     * 특정 버스 도시 코드의 모든 노선 조회
     *
     * 용도: 경기도 시내버스(1050) vs 경기도 마을버스(1030) 등 구분 시 사용
     *
     * @param busCityCode 버스 도시 코드
     * @return 해당 코드의 모든 노선
     */
    List<BusRouteMaster> findByBusCityCode(int busCityCode);

    /**
     * 특정 타입과 도시 코드의 모든 노선 조회
     *
     * @param transitType 버스 타입
     * @param busCityCode 버스 도시 코드
     * @return 해당하는 모든 노선
     */
    List<BusRouteMaster> findByTransitTypeAndBusCityCode(String transitType, int busCityCode);

    /**
     * 특정 타입과 상태, 도시 코드의 모든 노선 조회
     *
     * @param transitType 버스 타입
     * @param status 노선 상태
     * @param busCityCode 버스 도시 코드
     * @return 해당하는 모든 활성 노선
     */
    List<BusRouteMaster> findByTransitTypeAndStatusAndBusCityCode(
        String transitType, String status, int busCityCode
    );
}

package com.lasttrain.transit.repository;

import com.lasttrain.transit.domain.SubwayStationMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 전철역 마스터 데이터 Repository
 *
 * 역할:
 *   - SubwayStationMaster 엔티티를 데이터베이스와 연동
 *   - Spring Data JPA가 자동으로 구현 (개발자는 인터페이스만 정의)
 *
 * 제공되는 기본 메서드 (JpaRepository에서 자동 제공):
 *   - findById(Long id) : ID로 역 정보 1개 조회
 *   - findAll() : 모든 역 정보 조회
 *   - save(SubwayStationMaster) : 새 역 정보 저장
 *   - delete(SubwayStationMaster) : 역 정보 삭제
 *   - count() : 저장된 역 개수 조회
 *
 * 사용 예시:
 *   @Autowired
 *   private SubwayStationMasterRepository repository;
 *
 *   public void init() {
 *       // 1. 전철역 데이터 저장
 *       SubwayStationMaster station = SubwayStationMaster.builder()
 *           .stationCode("01")
 *           .stationName("서울역")
 *           .lineName("1호선")
 *           .odsayStationId("1000")
 *           .build();
 *       repository.save(station);
 *
 *       // 2. 모든 전철역 조회
 *       List<SubwayStationMaster> allStations = repository.findAll();
 *       System.out.println("저장된 역 개수: " + allStations.size());
 *   }
 */
@Repository
public interface SubwayStationMasterRepository extends JpaRepository<SubwayStationMaster, Long> {
    // 기본 CRUD 메서드만 사용하므로 추가 메서드 불필요
    // findAll(), findById() 등은 JpaRepository에서 자동으로 제공됨
}

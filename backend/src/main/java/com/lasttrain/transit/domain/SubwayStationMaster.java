package com.lasttrain.transit.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 전철역 마스터 데이터 엔티티
 *
 * 용도:
 *   - ODsay API에서 조회한 전철역 정보를 로컬 DB에 저장
 *   - 반복 조회 시 API 호출 대신 DB에서 조회하여 성능 개선
 *
 * 예시:
 *   - 역명: "서울역", 호선: "1호선", odsayStationId: "1000" (외부 API ID)
 *   - 역명: "강남역", 호선: "2호선", odsayStationId: "2000"
 *
 * 데이터 수명:
 *   - 한 번 저장되면 수정하지 않음 (전철역 정보는 변경 빈도가 매우 낮음)
 *   - 삭제는 관리자에 의해서만 수행 (일반 사용자는 조회만)
 */
@Entity
@Table(
    name = "subway_station_master",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_odsay_id_line",
            columnNames = {"odsay_station_id", "line_name"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SubwayStationMaster {

    /**
     * 자동 생성 ID
     * - JPA가 INSERT 시 자동으로 생성
     * - 다른 테이블에서 외래키로 사용됨
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 전철역 코드 (내부용)
     * 예: "01", "02", "03" 등
     * 사용: 내부 시스템에서 전철역을 식별할 때
     */
    @Column(nullable = false, length = 10)
    private String stationCode;

    /**
     * 전철역명
     * 예: "서울역", "강남역", "시청역"
     * 사용: 사용자에게 표시하는 역 이름
     */
    @Column(nullable = false, length = 50)
    private String stationName;

    /**
     * 호선명
     * 예: "1호선", "2호선", "경의중앙선"
     * 사용: 같은 이름의 역이 여러 호선에 있을 수 있으므로 호선 정보도 필요
     */
    @Column(nullable = false, length = 20)
    private String lineName;

    /**
     * ODsay 외부 코드 (stationId)
     * 예: "1000", "2000" 등
     * 용도: ODsay API와 통신할 때 이 ID를 사용하여 역을 식별
     *
     * 중요: odsayStationId + lineName 조합이 UNIQUE
     * → 같은 역도 다른 호선이면 다른 레코드로 저장됨
     */
    @Column(nullable = false, length = 10)
    private String odsayStationId;
}

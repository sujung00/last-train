package com.lasttrain.bus.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 버스 노선 마스터 테이블
 *
 * 역할:
 *   - 서울/경기/인천 버스의 모든 활성 노선을 관리
 *   - 스케줄러가 이 테이블의 모든 노선에 대해 API 호출하여 DB에 미리 저장
 *   - 새로운 노선도 자동으로 배치에 포함됨
 *
 * 데이터 구조:
 *   - transitType: BUS_SEOUL (1000), BUS_GYEONGGI (1030~3000), BUS_INCHEON (3000)
 *   - routeId: 버스 노선 ID (예: "100100053" for 서울, "200000037" for 경기)
 *   - routeName: 버스 노선명 (예: "123번 (강남-부천)")
 *   - busCityCode: 버스 도시 코드 (1000=서울, 1030=경기마을, 1040=경기일반, 등)
 *   - status: ACTIVE / INACTIVE (배치는 ACTIVE만 처리)
 *   - createdAt, updatedAt: 관리 용도
 *
 * 사용:
 *   - BusBatchScheduler에서 매일 이 테이블을 조회하여 모든 활성 노선 처리
 *   - BusRouteMasterLoader가 앱 시작 시 또는 관리자 요청 시 업데이트
 */
@Entity
@Table(
    name = "bus_route_master",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bus_route",
            columnNames = {"transit_type", "route_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class BusRouteMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 대중교통 타입
     * 예: "BUS_SEOUL" (1000), "BUS_GYEONGGI" (1030~2000)
     */
    @Column(nullable = false, length = 20)
    private String transitType;

    /**
     * 버스 노선 ID
     * 예: "100100053" (서울), "200000037" (경기)
     */
    @Column(nullable = false, length = 50)
    private String routeId;

    /**
     * 버스 노선명
     * 예: "123번 (강남-부천)", "광역버스 9900"
     */
    @Column(length = 100)
    private String routeName;

    /**
     * 버스 도시 코드
     * 1000: 서울시
     * 1030: 경기도 마을버스
     * 1040: 경기도 마을버스
     * 1050: 경기도 시내버스
     * 1140: 경기도 직행좌석버스
     * 1160: 경기도 시내버스
     * 2000: 광역버스 (직행좌석)
     * 3000: 인천시
     */
    @Column
    private int busCityCode;

    /**
     * 노선 상태
     * ACTIVE: 배치 갱신 대상
     * INACTIVE: 배치 갱신 제외
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 레코드 생성 시간
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 레코드 수정 시간
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 엔티티 생성 시 createdAt 자동 설정
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 엔티티 수정 시 updatedAt 자동 설정
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 노선 이름 업데이트
     */
    public void updateRouteName(String newRouteName) {
        this.routeName = newRouteName;
    }

    /**
     * 노선 상태 변경
     */
    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }
}

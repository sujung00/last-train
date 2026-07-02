package com.lasttrain.transit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 스케줄러가 미리 적재해두는 막차 시각 Fallback 테이블
 *
 * 용도:
 *   - 평상시: 실시간 API(ODsay/서울버스/경기버스)로 막차 시각 조회
 *   - API 장애 시: 이 테이블에서 마지막 저장값으로 Fallback
 *   - 스케줄러가 DB에 미리 적재하고 정기적으로 갱신
 *
 * 데이터 적재 방식:
 *   - SubwayStationLoader: 앱 시작 시 마스터 데이터 초기 적재
 *   - TransitRefreshScheduler: 정기적으로 스케줄러 실행하여 DB 갱신
 *   - 사용자 요청 시에는 DB 저장/갱신 없음 (스케줄러만 관리)
 *
 * 캐시 갱신 전략:
 *   - 스케줄러가 정기적으로 실행하여 latest 데이터 유지
 *   - updateLastTime() 메서드로 updated_at을 최신 시각으로 갱신
 *
 * 데이터 구조 예시:
 *   - transitType: "SUBWAY"
 *   - cacheKey: ODsay stationId 또는 버스 노선ID (예: "136", "100100578:124000414:29")
 *   - dayType: "WEEKDAY" (평일) / "SATURDAY" / "SUNDAY"
 *   - lastTime: "23:45" (마지막 열차 시간)
 *   - updatedAt: 2026-07-01 10:30:00 (이 데이터를 마지막으로 갱신한 시각)
 */
@Entity
@Table(
    name = "last_transit_schedule",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_transit_cache",
            columnNames = {"transit_type", "cache_key", "day_type"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class LastTransitSchedule {

    /**
     * 자동 생성 ID
     * - JPA가 INSERT 시 자동으로 생성
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 대중교통 타입
     * 예: "SUBWAY" (전철), "BUS" (버스)
     * 사용: 같은 경로라도 교통수단마다 막차 시간이 다를 수 있으므로 구분
     */
    @Column(nullable = false, length = 20)
    private String transitType;

    /**
     * 캐시 키
     * 대중교통 수단에 따라 다른 값을 사용:
     *   - SUBWAY: ODsay stationId (예: "136", "729")
     *   - BUS_SEOUL: "버스노선ID:정류소ID:순번" (예: "100100578:124000414:29")
     *   - BUS_GYEONGGI: 경기버스 routeId (예: "200000037")
     * 용도: 대중교통 수단별로 고유한 경로를 빠르게 찾기 위한 키
     */
    @Column(nullable = false, length = 50)
    private String cacheKey;

    /**
     * 요일 타입
     * 가능한 값:
     *   - "WEEKDAY" : 평일 (월~금) - 막차 시간이 가장 늦음
     *   - "SATURDAY" : 토요일 - 평일과 다를 수 있음
     *   - "SUNDAY" : 일요일 - 보통 가장 일찍 끝남
     *
     * 중요: 같은 경로라도 요일에 따라 막차 시간이 다르므로 구분 필수
     */
    @Column(nullable = false, length = 10)
    private String dayType;

    /**
     * 막차 시간 (HH:mm 형식 문자열)
     * 예: "23:45", "00:30" (자정 이후)
     * 형식: "HH:mm" (24시간 형식)
     *
     * 저장 형식이 String인 이유:
     *   - 자정을 넘은 시간을 다루기 쉬움 (예: 00:30은 전날 자정 이후)
     *   - 조회 후 클라이언트가 문자열로 바로 표시 가능
     */
    @Column(nullable = false, length = 5)
    private String lastTime;

    /**
     * 캐시 갱신 시각
     * 이 데이터가 마지막으로 업데이트된 시간
     * 용도: 캐시가 오래되었는지 판단 (예: 1주일 이상 지났으면 API 호출로 갱신)
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 막차 시간과 갱신 시각을 함께 업데이트
     *
     * 용도:
     *   - ODsay API에서 새로운 막차 시간을 조회했을 때 호출
     *   - 기존 캐시 레코드를 새 데이터로 갱신
     *
     * 예시:
     *   - 저장된 lastTime이 "23:45"였는데, 실제로는 "23:50"이 되었을 때
     *   - updateLastTime("23:50")을 호출하여 갱신
     *
     * @param newLastTime 새로운 막차 시간 (HH:mm 형식)
     */
    public void updateLastTime(String newLastTime) {
        this.lastTime = newLastTime;
        this.updatedAt = LocalDateTime.now();
    }
}

-- ============================================================================
-- V5__create_bus_route_master.sql
--
-- 목적: 버스 노선 마스터 데이터 테이블 생성
--
-- 역할:
--   - 서울/경기/인천 버스의 모든 활성 노선 관리
--   - BusBatchScheduler가 이 테이블의 모든 활성(ACTIVE) 노선에 대해
--     막차 시간을 API 호출하여 DB(last_transit_schedule)에 저장
--   - 새로운 노선도 BusRouteMasterLoader로 자동 추가
-- ============================================================================

CREATE TABLE bus_route_master (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '자동 생성 ID',

    transit_type VARCHAR(20) NOT NULL COMMENT '대중교통 타입 (BUS_SEOUL, BUS_GYEONGGI, BUS_INCHEON)',

    route_id VARCHAR(50) NOT NULL COMMENT '버스 노선 ID (예: 100100053, 200000037)',

    route_name VARCHAR(100) COMMENT '버스 노선명 (예: 123번 (강남-부천), 광역버스 9900)',

    bus_city_code INT COMMENT '버스 도시 코드 (1000=서울, 1030=경기마을, 1040=경기마을, 1050=경기시내, 1140=경기직행좌석, 1160=경기시내, 2000=광역직행좌석, 3000=인천)',

    status VARCHAR(20) NOT NULL COMMENT '노선 상태 (ACTIVE=배치 갱신 대상, INACTIVE=배치 갱신 제외)',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성 시각',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '레코드 수정 시각',

    UNIQUE KEY uk_bus_route (transit_type, route_id) COMMENT '같은 교통수단의 노선은 중복 저장 방지'

) COMMENT '버스 노선 마스터 데이터 테이블 (배치 처리 대상)' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

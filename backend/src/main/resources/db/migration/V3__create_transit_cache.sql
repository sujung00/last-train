-- ============================================================================
-- V3__create_transit_cache.sql
--
-- 목적: 전철역 마스터 데이터와 막차 시간 캐시 테이블 생성
--
-- 테이블 설명:
--   1. subway_station_master: ODsay API에서 조회한 전철역 정보를 저장
--   2. last_transit_schedule: 막차 시간을 캐시하여 API 호출 횟수를 줄임
-- ============================================================================

-- 전철역 마스터 데이터 테이블
-- 용도: ODsay API로부터 조회한 전철역 정보를 로컬 DB에 저장하여
--       반복 조회 시 네트워크 호출을 줄임
CREATE TABLE subway_station_master (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '자동 생성 ID',
    station_code VARCHAR(10) NOT NULL COMMENT '전철역코드 (내부용)',
    station_name VARCHAR(50) NOT NULL COMMENT '전철역명 (예: 서울역, 강남역)',
    line_name VARCHAR(20) NOT NULL COMMENT '호선 (예: 1호선, 2호선)',
    odsay_station_id VARCHAR(10) NOT NULL COMMENT '외부코드 (ODsay API에서 사용하는 stationId)',
    UNIQUE KEY uk_odsay_id_line (odsay_station_id, line_name) COMMENT 'ODsay stationId + 호선 조합으로 중복 방지'
) COMMENT '전철역 마스터 데이터 테이블' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 막차 시간 캐시 테이블
-- 용도: "출발지 → 도착지" 조합의 막차 시간을 캐시하여
--       같은 조합을 다시 조회할 때 DB에서 직접 조회 (API 호출 X)
CREATE TABLE last_transit_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '자동 생성 ID',
    transit_type VARCHAR(20) NOT NULL COMMENT '대중교통 타입 (SUBWAY, BUS, 등)',
    cache_key VARCHAR(50) NOT NULL COMMENT '캐시 키 (SUBWAY: ODsay stationId, BUS_SEOUL: 노선ID:정류소ID:순번, BUS_GYEONGGI: routeId)',
    day_type VARCHAR(10) NOT NULL COMMENT '요일 타입 (WEEKDAY=평일, SATURDAY=토요일, SUNDAY=일요일)',
    last_time VARCHAR(5) NOT NULL COMMENT '막차 시간 (HH:mm 형식, 예: 23:45)',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '캐시 갱신 시각 (다음 갱신이 필요한지 판단)',
    UNIQUE KEY uk_transit_cache (transit_type, cache_key, day_type) COMMENT '같은 조합은 중복 저장 방지'
) COMMENT '막차 시간 캐시 테이블' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

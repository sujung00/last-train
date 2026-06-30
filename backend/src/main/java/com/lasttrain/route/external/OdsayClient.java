package com.lasttrain.route.external;

/**
 * ODsay 대중교통 API를 호출하는 인터페이스입니다.
 *
 * 인터페이스로 선언하는 이유:
 *   실제 구현체(OdsayClientImpl)는 외부 API를 호출하지만,
 *   테스트에서는 가짜 구현체(Mock)를 주입해서 ODsay 없이도 테스트할 수 있습니다.
 *   ODsay API 스펙이 바뀌어도 OdsayClientImpl만 수정하면 됩니다.
 */
public interface OdsayClient {

    /**
     * 출발지에서 목적지까지의 대중교통 경로를 조회합니다.
     *
     * ODsay API: searchPubTransPathT
     * 반환 형식: JSON 문자열 (서비스 계층에서 파싱)
     *
     * @param sx 출발지 경도 (longitude, WGS84)
     * @param sy 출발지 위도 (latitude, WGS84)
     * @param ex 목적지 경도 (longitude, WGS84)
     * @param ey 목적지 위도 (latitude, WGS84)
     * @return ODsay 경로 조회 응답 JSON 문자열
     */
    String searchRoute(double sx, double sy, double ex, double ey);

    /**
     * 특정 지하철역의 시간표를 조회합니다.
     *
     * ODsay API: subwayTimeTable (firstLastFlag=2 고정 → 막차 시간표만 조회)
     * 반환 형식: JSON 문자열 (서비스 계층에서 파싱)
     *
     * @param stationId ODsay 지하철역 고유 ID
     * @param dayType   요일 구분 (1=평일, 2=토요일, 3=일요일/공휴일)
     * @return ODsay 지하철 시간표 응답 JSON 문자열
     */
    String searchSubwaySchedule(String stationId, String dayType);
}

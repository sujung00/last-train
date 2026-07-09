package com.lasttrain.bus.external;

/**
 * 경기도 버스 정류장 정보 API(getBusRouteStationList) 응답에서
 * 필요한 정류장 정보만 꺼내 담는 record입니다.
 *
 * @param stationSeq   정류장 순번 (노선 상 순서)
 * @param stationId    정류장 ID (경기도 버스 체계에서의 고유 ID)
 * @param stationName  정류장 명칭
 */
public record GyeonggiStationInfo(
        int stationSeq,
        String stationId,
        String stationName
) {}

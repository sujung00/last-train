package com.lasttrain.bus.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 경기도 버스 노선 정보 외부 API 클라이언트
 *
 * 경기도에서 제공하는 버스 노선 정보 API를 호출하여
 * 특정 버스 노선의 막차 시간을 조회한다.
 * 요일별로 다른 막차 시간(평일, 토요일, 일요일)을 제공한다.
 *
 * API: https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteInfoItemv2
 * 응답: JSON 형식 (upLastTime, satUpLastTime, sunUpLastTime 필드 포함)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GyeonggiBusRouteClient {

    // 환경변수에서 API key 주입
    @Value("${gyeonggi.bus.api-key}")
    private String serviceKey;

    // 환경변수에서 API 기본 URL 주입
    @Value("${gyeonggi.bus.route-base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 버스 시간을 LocalDateTime으로 변환하기 위한 포맷터
    // API에서 "HH:mm" 형식(예: "23:50")으로 시간을 반환함
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 경기도 버스 노선의 막차 시간을 조회한다.
     *
     * 요일별로 다른 막차 시간을 반환한다:
     * - 평일(월~금): upLastTime 필드 사용
     * - 토요일: satUpLastTime 필드 사용
     * - 일요일/공휴일: sunUpLastTime 필드 사용
     *
     * @param routeId 버스 노선 ID (예: "200000001")
     * @return 오늘 날짜 기준 막차 시간, 조회 실패 시 null
     *
     * 예시: getLastBusTime("200000001")
     *       → 2026-06-26T23:50:00 (오늘이 평일인 경우)
     */
    public LocalDateTime getLastBusTime(String routeId) {
        try {
            // Step 1: API 호출 URL 구성
            // String.format으로 직접 URL 생성하고 URI.create()로 감싼다
            // URI.create()는 이중 인코딩을 방지한다
            String url = String.format(
                    "%s?serviceKey=%s&routeId=%s&format=json",
                    baseUrl, serviceKey, routeId
            );
            URI uri = URI.create(url);

            log.debug("경기도 버스 API 호출: routeId={}", routeId);

            // Step 2: API 호출 (응답을 String으로 받음, JSON 형식)
            String response = restTemplate.getForObject(uri, String.class);

            if (response == null || response.isBlank()) {
                log.warn("경기도 버스 API 응답이 비어있음: routeId={}", routeId);
                return null;
            }

            // Step 3: JSON 응답 파싱
            // API 응답 구조: { "response": { "msgBody": { "busRouteInfoItem": {...} } } }
            JsonNode root = objectMapper.readTree(response);

            // 중첩된 구조를 따라가며 busRouteInfoItem 객체 찾기
            JsonNode itemNode = root
                    .path("response")
                    .path("msgBody")
                    .path("busRouteInfoItem");

            if (itemNode.isMissingNode()) {
                log.warn("응답에서 busRouteInfoItem 데이터를 찾을 수 없음: routeId={}", routeId);
                return null;
            }

            // Step 4: 오늘의 요일을 확인하여 적절한 시간 필드 선택
            LocalDate today = LocalDate.now();
            DayOfWeek dayOfWeek = today.getDayOfWeek();

            String lastTimeStr;
            if (dayOfWeek == DayOfWeek.SATURDAY) {
                // 토요일: satUpLastTime 사용
                lastTimeStr = itemNode.path("satUpLastTime").asText(null);
                log.debug("토요일 막차 시간: {}", lastTimeStr);
            } else if (dayOfWeek == DayOfWeek.SUNDAY) {
                // 일요일: sunUpLastTime 사용 (공휴일도 동일하게 처리)
                lastTimeStr = itemNode.path("sunUpLastTime").asText(null);
                log.debug("일요일 막차 시간: {}", lastTimeStr);
            } else {
                // 평일(월~금): upLastTime 사용
                lastTimeStr = itemNode.path("upLastTime").asText(null);
                log.debug("평일 막차 시간: {}", lastTimeStr);
            }

            if (lastTimeStr == null || lastTimeStr.isBlank()) {
                log.warn("응답에서 막차 시간을 찾을 수 없음: routeId={}, dayOfWeek={}", routeId, dayOfWeek);
                return null;
            }

            // Step 5: "HH:mm" 형식 시간을 LocalTime으로 파싱
            LocalTime lastTime = LocalTime.parse(lastTimeStr, TIME_FORMATTER);

            // Step 6: 오늘 날짜에 시간을 조합하여 LocalDateTime 생성
            LocalDateTime lastBusTime = LocalDateTime.of(today, lastTime);

            log.debug("막차 시간 조회 성공: routeId={}, dayOfWeek={}, time={}",
                    routeId, dayOfWeek, lastBusTime);

            return lastBusTime;

        } catch (RestClientException e) {
            // API 호출 실패 (네트워크 오류, 타임아웃 등)
            log.error("경기도 버스 API 호출 실패: routeId={}, error={}",
                    routeId, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            // JSON 파싱 또는 시간 변환 실패
            log.error("경기도 버스 응답 처리 중 에러: routeId={}, error={}",
                    routeId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 경기도 버스 노선의 정류장 목록을 조회한다.
     *
     * 노선 상 순서대로 정렬된 정류장 정보를 반환한다.
     *
     * @param routeId 버스 노선 ID (예: "200000001")
     * @return 정류장 목록 (stationSeq 순서로 정렬), 조회 실패 시 빈 리스트
     *
     * 예시: getBusRouteStationList("200000001")
     *       → [
     *            GyeonggiStationInfo(1, "1001", "출발지역터미널"),
     *            GyeonggiStationInfo(2, "1002", "중간역1"),
     *            GyeonggiStationInfo(3, "1003", "도착지역터미널")
     *          ]
     */
    public List<GyeonggiStationInfo> getBusRouteStationList(String routeId) {
        try {
            // Step 1: API 호출 URL 구성 (v2 버전)
            String url = String.format(
                    "https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteStationListv2?serviceKey=%s&routeId=%s&format=json",
                    serviceKey, routeId
            );
            URI uri = URI.create(url);

            log.debug("경기도 버스 정류장 목록 API 호출: routeId={}", routeId);

            // Step 2: API 호출 (응답을 String으로 받음, JSON 형식)
            String response = restTemplate.getForObject(uri, String.class);

            if (response == null || response.isBlank()) {
                log.warn("경기도 버스 정류장 목록 API 응답이 비어있음: routeId={}", routeId);
                return new ArrayList<>();
            }

            // Step 3: JSON 응답 파싱
            // API 응답 구조: { "response": { "msgBody": { "busRouteStationList": [ {...}, {...} ] } } }
            JsonNode root = objectMapper.readTree(response);

            // 중첩된 구조를 따라가며 busRouteStationList 배열 찾기
            JsonNode stationList = root
                    .path("response")
                    .path("msgBody")
                    .path("busRouteStationList");

            if (stationList.isMissingNode() || !stationList.isArray()) {
                log.warn("응답에서 busRouteStationList 데이터를 찾을 수 없음: routeId={}", routeId);
                return new ArrayList<>();
            }

            // Step 4: busRouteStationList 배열을 GyeonggiStationInfo 리스트로 변환
            List<GyeonggiStationInfo> stations = new ArrayList<>();

            for (JsonNode item : stationList) {
                try {
                    int stationSeq = item.path("stationSeq").asInt(-1);
                    String stationId = item.path("stationId").asText(null);
                    String stationName = item.path("stationName").asText(null);

                    // 필수 필드 검증
                    if (stationId != null && stationName != null && stationSeq >= 0) {
                        stations.add(new GyeonggiStationInfo(stationSeq, stationId, stationName));
                    } else {
                        log.warn("정류장 정보가 불완전함: routeId={}, stationSeq={}, stationId={}, stationName={}",
                                routeId, stationSeq, stationId, stationName);
                    }
                } catch (Exception e) {
                    log.warn("정류장 항목 파싱 실패: routeId={}, error={}", routeId, e.getMessage());
                }
            }

            log.debug("정류장 목록 조회 성공: routeId={}, count={}", routeId, stations.size());
            return stations;

        } catch (RestClientException e) {
            log.error("경기도 버스 정류장 목록 API 호출 실패: routeId={}, error={}",
                    routeId, e.getMessage(), e);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("경기도 버스 정류장 목록 응답 처리 중 에러: routeId={}, error={}",
                    routeId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}

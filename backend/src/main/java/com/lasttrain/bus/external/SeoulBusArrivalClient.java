package com.lasttrain.bus.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 서울시 버스 도착 정보 외부 API 클라이언트
 *
 * 서울시에서 제공하는 버스 도착 정보 API를 호출하여
 * 특정 정류소의 특정 버스 노선의 마지막 버스 시간을 조회한다.
 *
 * API: http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRouteAll
 * 응답: XML 형식 (lastTm 필드 포함)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeoulBusArrivalClient {

    // 환경변수에서 API key 주입
    @Value("${seoul.bus.api-key}")
    private String serviceKey;

    // 환경변수에서 API 기본 URL 주입
    @Value("${seoul.bus.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    // lastTm 값을 LocalDateTime으로 변환하기 위한 포맷터
    // yyyyMMddHHmmss 형식 (예: 20260625143000 = 2026-06-25 14:30:00)
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 서울시 버스 노선의 마지막 버스 도착 시간을 조회한다.
     *
     * @param busRouteId 버스 노선 ID (예: "100100053")
     * @param dayType 요일 구분 (WEEKDAY, SAT, SUN)
     * @return 마지막 버스 도착 시간, 조회 실패 시 null
     *
     * 예시: getLastBusTime("100100053", WEEKDAY)
     *       → 2026-06-25T14:30:00
     */
    public LocalDateTime getLastBusTime(String busRouteId, String dayType) {
        try {
            // Step 1: API 호출 URL 구성
            // resultType=xml로 설정 (XML 응답으로 파싱)
            String url = String.format(
                    "%s?serviceKey=%s&busRouteId=%s&resultType=xml",
                    baseUrl, serviceKey, busRouteId
            );
            URI uri = URI.create(url);

            // Step 2: API 호출 (응답을 String으로 받음, XML 형식)
            String response = restTemplate.getForObject(uri, String.class);

            if (response == null || response.isBlank()) {
                log.warn("서울시 버스 API 응답이 비어있음: busRouteId={}, dayType={}", busRouteId, dayType);
                return null;
            }

            // Step 3: XML 파싱
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(response)));

            // Step 4: lastTm 태그 전체 조회
            NodeList lastTmList = doc.getElementsByTagName("lastTm");

            if (lastTmList.getLength() == 0) {
                log.warn("응답에서 lastTm 태그를 찾을 수 없음: busRouteId={}, dayType={}, NodeList 크기=0",
                        busRouteId, dayType);
                return null;
            }

            // Step 5: 가장 큰 값(가장 늦은 시간) 선택
            long maxLastTm = 0;
            for (int i = 0; i < lastTmList.getLength(); i++) {
                String lastTmStr = lastTmList.item(i).getTextContent();
                try {
                    long lastTmValue = Long.parseLong(lastTmStr);
                    if (lastTmValue > maxLastTm) {
                        maxLastTm = lastTmValue;
                    }
                } catch (NumberFormatException e) {
                    log.warn("[lastTm 파싱 실패] index={}, value={}, error={}", i, lastTmStr, e.getMessage());
                }
            }

            if (maxLastTm == 0) {
                log.warn("유효한 lastTm 값이 없음: busRouteId={}, dayType={}, NodeList 크기={}",
                        busRouteId, dayType, lastTmList.getLength());
                return null;
            }

            // Step 6: 선택된 값을 LocalDateTime으로 변환
            String maxLastTmStr = String.valueOf(maxLastTm);
            LocalDateTime lastBusTime = LocalDateTime.parse(maxLastTmStr, TIME_FORMATTER);

            // Step 7: "HH:mm" 형식으로 변환
            String lastTimeFormatted = lastBusTime.format(DateTimeFormatter.ofPattern("HH:mm"));

            log.debug("마지막 버스 시간 조회 성공: busRouteId={}, dayType={}, maxLastTm={}, time={}",
                    busRouteId, dayType, maxLastTmStr, lastTimeFormatted);

            // LocalDateTime 형식으로 반환 (시간 정보만 유지)
            return LocalDateTime.of(lastBusTime.toLocalDate(), LocalTime.parse(lastTimeFormatted, DateTimeFormatter.ofPattern("HH:mm")));

        } catch (RestClientException e) {
            // API 호출 실패 (네트워크 오류, 타임아웃 등)
            log.error("서울시 버스 API 호출 실패: busRouteId={}, dayType={}, error={}",
                    busRouteId, dayType, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            // XML 파싱 또는 시간 변환 실패
            log.error("서울시 버스 응답 처리 중 에러: busRouteId={}, dayType={}, error={}",
                    busRouteId, dayType, e.getMessage(), e);
            return null;
        }
    }
}

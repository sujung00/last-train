package com.lasttrain.bus.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 서울시 버스 도착 정보 외부 API 클라이언트
 *
 * 서울시에서 제공하는 버스 도착 정보 API를 호출하여
 * 특정 정류소의 특정 버스 노선의 마지막 버스 시간을 조회한다.
 *
 * API: http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRouteList
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

    // XML 응답에서 lastTm 태그의 값을 추출하기 위한 정규표현식
    // <lastTm>20260625143000</lastTm> 형식에서 숫자만 추출
    private static final Pattern LAST_TM_PATTERN = Pattern.compile("<lastTm>(\\d{14})</lastTm>");

    // lastTm 값을 LocalDateTime으로 변환하기 위한 포맷터
    // yyyyMMddHHmmss 형식 (예: 20260625143000 = 2026-06-25 14:30:00)
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 서울시 버스 정류소의 마지막 버스 도착 시간을 조회한다.
     *
     * @param stId 정류소 ID (예: "107000100")
     * @param busRouteId 버스 노선 ID (예: "100100053")
     * @param ord 버스 순번 (예: "1", "2")
     * @return 마지막 버스 도착 시간, 조회 실패 시 null
     *
     * 예시: getLastBusTime("107000100", "100100053", "1")
     *       → 2026-06-25T14:30:00
     */
    public LocalDateTime getLastBusTime(String stId, String busRouteId, String ord) {
        try {
            // Step 1: API 호출 URL 구성
            // String.format으로 직접 URL 생성하고 URI.create()로 감싼다
            // URI.create()는 이중 인코딩을 방지한다
            String url = String.format(
                    "%s?serviceKey=%s&stId=%s&busRouteId=%s&ord=%s&resultType=json",
                    baseUrl, serviceKey, stId, busRouteId, ord
            );
            URI uri = URI.create(url);

            log.debug("서울시 버스 API 호출: stId={}, busRouteId={}, ord={}", stId, busRouteId, ord);

            // Step 2: API 호출 (응답을 String으로 받음, XML 형식)
            String response = restTemplate.getForObject(uri, String.class);

            if (response == null || response.isBlank()) {
                log.warn("서울시 버스 API 응답이 비어있음: stId={}, busRouteId={}, ord={}", stId, busRouteId, ord);
                return null;
            }

            // Step 3: XML 응답에서 lastTm 값 추출
            // 정규표현식으로 <lastTm>숫자들</lastTm> 패턴 찾기
            Matcher matcher = LAST_TM_PATTERN.matcher(response);

            if (!matcher.find()) {
                log.warn("응답에서 lastTm 태그를 찾을 수 없음: stId={}, busRouteId={}, ord={}", stId, busRouteId, ord);
                return null;
            }

            // Step 4: 추출한 값(yyyyMMddHHmmss 형식)을 LocalDateTime으로 변환
            String lastTmStr = matcher.group(1);  // 정규표현식 괄호 안의 숫자 부분만 추출
            LocalDateTime lastBusTime = LocalDateTime.parse(lastTmStr, TIME_FORMATTER);

            log.debug("마지막 버스 시간 조회 성공: stId={}, busRouteId={}, ord={}, time={}",
                    stId, busRouteId, ord, lastBusTime);

            return lastBusTime;

        } catch (RestClientException e) {
            // API 호출 실패 (네트워크 오류, 타임아웃 등)
            log.error("서울시 버스 API 호출 실패: stId={}, busRouteId={}, ord={}, error={}",
                    stId, busRouteId, ord, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            // XML 파싱 또는 시간 변환 실패
            log.error("서울시 버스 응답 처리 중 에러: stId={}, busRouteId={}, ord={}, error={}",
                    stId, busRouteId, ord, e.getMessage(), e);
            return null;
        }
    }
}

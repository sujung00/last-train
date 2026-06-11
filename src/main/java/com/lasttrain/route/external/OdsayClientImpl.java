package com.lasttrain.route.external;

import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * ODsay API를 실제로 호출하는 구현체입니다.
 *
 * ODsay Server 플랫폼 사용 이유:
 *   백엔드에서 직접 호출하는 방식이라 Server 플랫폼 API Key를 사용합니다.
 *   API Key가 프론트엔드(브라우저)에 노출되면 악용될 수 있어
 *   반드시 서버에서만 호출해야 합니다.
 *
 * IP 인증 주의:
 *   ODsay Server 플랫폼은 등록된 IP에서만 호출을 허용합니다.
 *   로컬 개발 시 공인 IP를, 운영 시 서버 IP를 ODsay 앱에 등록해야 합니다.
 */
@Slf4j
@Component
public class OdsayClientImpl implements OdsayClient {

    // application.yml의 odsay.api-key 값을 주입받습니다.
    // 실제 값은 환경변수 ODSAY_API_KEY에서 읽어옵니다.
    @Value("${odsay.api-key}")
    private String apiKey;

    // application.yml의 odsay.base-url 값을 주입받습니다.
    // 예: "https://api.odsay.com/v1/api"
    @Value("${odsay.base-url}")
    private String baseUrl;

    // HTTP 요청을 보내는 Spring 기본 HTTP 클라이언트입니다.
    // 별도 Bean 없이 직접 생성해서 사용합니다.
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 출발지→목적지 대중교통 경로를 ODsay에서 조회합니다.
     *
     * 호출 URL 예시:
     *   https://api.odsay.com/v1/api/searchPubTransPathT
     *     ?apiKey=...&SX=127.0473&SY=37.5172&EX=126.766&EY=37.5034&SearchPathType=0
     *
     * SearchPathType=0: 버스+지하철 모두 포함한 경로 검색
     */
    @Override
    public String searchRoute(double sx, double sy, double ex, double ey) {
        // UriComponentsBuilder를 사용하면 queryParam 값이 자동으로 URL 인코딩됩니다.
        // (예: apiKey에 특수문자가 있어도 안전하게 인코딩됨)
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/searchPubTransPathT")
                .queryParam("apiKey", apiKey)
                .queryParam("SX", sx)       // 출발지 경도
                .queryParam("SY", sy)       // 출발지 위도
                .queryParam("EX", ex)       // 목적지 경도
                .queryParam("EY", ey)       // 목적지 위도
                .queryParam("SearchPathType", 0) // 0=버스+지하철 통합 경로
                .toUriString();

        try {
            log.debug("[ODsay] 경로 조회 요청: SX={}, SY={}, EX={}, EY={}", sx, sy, ex, ey);
            String response = restTemplate.getForObject(url, String.class);
            log.debug("[ODsay] 경로 조회 성공");
            return response;
        } catch (Exception e) {
            // ODsay API 장애, 네트워크 오류, IP 미등록 등 모든 예외를 503으로 변환합니다.
            // 원본 예외 메시지는 로그에 남겨두어 운영 시 디버깅에 활용합니다.
            log.error("[ODsay] 경로 조회 실패: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.ODSAY_API_ERROR);
        }
    }

    /**
     * 지하철역의 막차 시간표를 ODsay에서 조회합니다.
     *
     * 호출 URL 예시:
     *   https://api.odsay.com/v1/api/subwayTimeTable
     *     ?apiKey=...&stationID=1002&dayType=1&firstLastFlag=2
     *
     * firstLastFlag=2: 막차 시간표만 조회 (1=첫차, 2=막차)
     * 이 서비스는 막차만 필요하므로 2로 고정합니다.
     */
    @Override
    public String searchSubwaySchedule(String stationId, String dayType) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/subwayTimeTable")
                .queryParam("apiKey", apiKey)
                .queryParam("stationID", stationId)
                .queryParam("dayType", dayType)   // 1=평일, 2=토요일, 3=일요일
                .queryParam("firstLastFlag", 2)   // 2=막차 고정
                .toUriString();

        try {
            log.debug("[ODsay] 지하철 시간표 조회 요청: stationId={}, dayType={}", stationId, dayType);
            String response = restTemplate.getForObject(url, String.class);
            log.debug("[ODsay] 지하철 시간표 조회 성공");
            return response;
        } catch (Exception e) {
            log.error("[ODsay] 지하철 시간표 조회 실패: stationId={}, 사유={}", stationId, e.getMessage(), e);
            throw new AppException(ErrorCode.ODSAY_API_ERROR);
        }
    }
}

package com.lasttrain.auth.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 카카오 로그인(OAuth 2.0)을 처리하는 클라이언트입니다.
 *
 * 카카오 로그인 흐름:
 *   1) 프론트엔드가 카카오 로그인 화면으로 사용자를 보내고, 사용자가 동의하면
 *      카카오가 "인가 코드(code)"를 프론트엔드로 돌려줍니다.
 *   2) 프론트엔드는 이 인가 코드를 우리 서버로 전달합니다.
 *   3) 우리 서버(getAccessToken)가 인가 코드를 카카오에 보내 "액세스 토큰"으로 교환합니다.
 *   4) 액세스 토큰으로 카카오에 사용자 정보(getUserInfo)를 요청합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoAuthClient {

    // application.yml의 kakao.client-id 값을 주입받습니다. (카카오 앱의 REST API 키)
    @Value("${kakao.client-id}")
    private String clientId;

    // application.yml의 kakao.redirect-uri 값을 주입받습니다.
    // 카카오 개발자 콘솔에 등록한 redirect uri와 정확히 일치해야 합니다.
    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    // application.yml의 kakao.client-secret 값을 주입받습니다.
    @Value("${kakao.client-secret}")
    private String clientSecret;

    // JSON 문자열을 JsonNode 트리로 파싱할 때 사용합니다. (Spring Boot가 자동 구성)
    private final ObjectMapper objectMapper;

    // HTTP 요청을 보내는 Spring 기본 HTTP 클라이언트입니다.
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 인가 코드(code)를 카카오 액세스 토큰으로 교환합니다.
     *
     * 호출 방식: POST https://kauth.kakao.com/oauth/token
     *   Content-Type: application/x-www-form-urlencoded
     *   응답 예시: {"access_token": "abc123", "token_type": "bearer", ...}
     */
    public String getAccessToken(String code) {
        String url = "https://kauth.kakao.com/oauth/token";

        // 카카오 토큰 API는 form-urlencoded 형식의 body를 요구합니다.
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("redirect_uri", redirectUri);
        body.add("code", code);
        body.add("client_secret", clientSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.debug("[Kakao] 액세스 토큰 요청");
            String response = restTemplate.postForObject(url, request, String.class);

            // 응답 JSON에서 access_token 필드만 꺼냅니다.
            JsonNode root = objectMapper.readTree(response);
            String accessToken = root.path("access_token").asText();

            log.debug("[Kakao] 액세스 토큰 발급 성공");
            return accessToken;
        } catch (Exception e) {
            // 인가 코드 만료, 잘못된 client 정보, 네트워크 오류 등 모든 예외를 동일하게 처리합니다.
            log.error("[Kakao] 액세스 토큰 발급 실패: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.KAKAO_AUTH_ERROR);
        }
    }

    /**
     * 액세스 토큰으로 카카오 사용자 정보를 조회합니다.
     *
     * 호출 방식: GET https://kapi.kakao.com/v2/user/me
     *   Header: Authorization: Bearer {accessToken}
     *   응답 예시:
     *     {
     *       "id": 123456789,
     *       "kakao_account": { "email": "user@example.com" },
     *       "properties": { "nickname": "홍길동" }
     *     }
     */
    public KakaoUserInfo getUserInfo(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            log.debug("[Kakao] 사용자 정보 조회 요청");
            String response = restTemplate.exchange(url, HttpMethod.GET, request, String.class).getBody();

            JsonNode root = objectMapper.readTree(response);
            Long id = root.path("id").asLong();
            String email = root.path("kakao_account").path("email").asText(null);
            String nickname = root.path("properties").path("nickname").asText(null);

            log.debug("[Kakao] 사용자 정보 조회 성공: id={}", id);
            return new KakaoUserInfo(id, email, nickname);
        } catch (Exception e) {
            log.error("[Kakao] 사용자 정보 조회 실패: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.KAKAO_AUTH_ERROR);
        }
    }
}

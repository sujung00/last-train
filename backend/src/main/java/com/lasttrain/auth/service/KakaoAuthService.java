package com.lasttrain.auth.service;

import com.lasttrain.auth.domain.User;
import com.lasttrain.auth.dto.TokenResponse;
import com.lasttrain.auth.external.KakaoAuthClient;
import com.lasttrain.auth.external.KakaoUserInfo;
import com.lasttrain.auth.repository.UserRepository;
import com.lasttrain.global.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    // 카카오 로그인으로 가입/조회되는 사용자의 provider 값
    // User.provider 컬럼은 ENUM('EMAIL', 'KAKAO')이므로 반드시 이 문자열을 사용해야 합니다.
    private static final String KAKAO_PROVIDER = "KAKAO";

    private static final long RT_TTL_DAYS = 7; // Refresh Token 유효기간 (일). AuthService와 동일하게 맞춥니다.

    private final KakaoAuthClient kakaoAuthClient;
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * 카카오 인가 코드로 로그인(또는 최초 로그인 시 회원가입)하고 JWT 토큰을 발급합니다.
     *
     * 처리 흐름:
     *   1. 인가 코드(code) → 카카오 액세스 토큰 교환
     *   2. 카카오 액세스 토큰 → 카카오 사용자 정보(id, email, nickname) 조회
     *   3. provider=KAKAO, providerId=카카오 id로 기존 회원 조회
     *      - 있으면: 기존 회원 그대로 사용
     *      - 없으면: 신규 회원으로 저장 (최초 카카오 로그인 = 회원가입)
     *   4. Access Token + Refresh Token 발급
     *   5. Redis에 Refresh Token 저장 (AuthService.login()과 동일한 방식, 7일 TTL)
     *
     * 이메일이 null일 수 있는 이유:
     *   카카오는 사용자가 이메일 제공에 동의하지 않으면 kakao_account.email을 내려주지 않습니다.
     *   User.email 컬럼은 nullable이라 문제없이 저장되지만,
     *   이메일 기반 기능(예: 비밀번호 찾기)은 카카오 가입자에게는 동작하지 않을 수 있습니다.
     */
    @Transactional
    public TokenResponse kakaoLogin(String code) {
        String kakaoAccessToken = kakaoAuthClient.getAccessToken(code);
        KakaoUserInfo kakaoUserInfo = kakaoAuthClient.getUserInfo(kakaoAccessToken);

        // 카카오 사용자 고유 ID(Long)를 User.providerId(String) 컬럼에 맞춰 문자열로 변환합니다.
        String providerId = String.valueOf(kakaoUserInfo.id());

        User user = userRepository.findByProviderAndProviderId(KAKAO_PROVIDER, providerId)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(KAKAO_PROVIDER)
                                .providerId(providerId)
                                .email(kakaoUserInfo.email()) // 동의하지 않았다면 null
                                .build()
                ));

        return issueAndStoreTokens(user.getUserId(), kakaoUserInfo.email());
    }

    // AT + RT 발급 후 Redis에 RT를 저장하는 로직.
    // AuthService.issueAndStoreTokens()와 동일한 방식(같은 Redis 키 형식 "RT:{userId}")을 사용해야
    // 이메일 로그인과 카카오 로그인이 같은 방식으로 재발급/로그아웃을 처리할 수 있습니다.
    private TokenResponse issueAndStoreTokens(Long userId, String email) {
        String accessToken = tokenProvider.createAccessToken(userId);
        String refreshToken = tokenProvider.createRefreshToken(userId);

        redisTemplate.opsForValue().set(AuthService.RT_PREFIX + userId, refreshToken, RT_TTL_DAYS, TimeUnit.DAYS);

        return new TokenResponse(accessToken, refreshToken, userId, email);
    }
}

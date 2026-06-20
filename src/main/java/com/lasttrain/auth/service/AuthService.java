package com.lasttrain.auth.service;

import com.lasttrain.auth.domain.User;
import com.lasttrain.auth.dto.LoginRequest;
import com.lasttrain.auth.dto.SignupRequest;
import com.lasttrain.auth.dto.TokenResponse;
import com.lasttrain.auth.repository.UserRepository;
import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import com.lasttrain.global.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    // Redis에 Refresh Token을 저장할 때 사용하는 키 접두사
    // 저장 형식: "RT:{userId}" → refreshToken 값
    public static final String RT_PREFIX = "RT:";

    private static final long RT_TTL_DAYS = 7; // Refresh Token 유효기간 (일)

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * 이메일로 회원가입합니다.
     *
     * 처리 흐름:
     *   1. 이미 가입된 이메일인지 확인
     *   2. 비밀번호를 BCrypt로 암호화해서 저장
     *
     * 왜 BCrypt를 쓰나요?
     *   평문 비밀번호를 DB에 그대로 저장하면 DB가 유출됐을 때 전체 비밀번호가 노출됩니다.
     *   BCrypt는 단방향 해시라 복호화가 불가능하고, 매번 다른 salt를 추가해서
     *   같은 비밀번호라도 다른 해시값이 생성됩니다.
     */
    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password())) // 평문 → BCrypt 해시
                .provider("EMAIL")
                .build();

        userRepository.save(user);
    }

    /**
     * 이메일로 로그인하고 JWT 토큰을 발급합니다.
     *
     * 처리 흐름:
     *   1. 이메일로 사용자 조회 (없으면 INVALID_CREDENTIALS)
     *   2. 입력한 비밀번호와 저장된 해시값 비교 (틀리면 INVALID_CREDENTIALS)
     *   3. Access Token + Refresh Token 발급
     *   4. Redis에 Refresh Token 저장 (7일 TTL)
     *
     * 이메일 없음과 비밀번호 불일치를 같은 에러코드로 처리하는 이유:
     *   "이메일이 없습니다"라고 응답하면 공격자가 가입 여부를 알 수 있어
     *   계정 열거 공격(Account Enumeration)에 취약해집니다.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueAndStoreTokens(user.getUserId());
    }

    /**
     * Refresh Token을 검증하고 새로운 AT + RT를 발급합니다.
     *
     * 처리 흐름:
     *   1. RT에서 userId 추출
     *   2. Redis에 저장된 RT와 요청으로 받은 RT 비교 (Refresh Token Rotation)
     *   3. 일치하면 새 AT + RT 발급, Redis 갱신
     *
     * Redis 비교를 하는 이유:
     *   탈취된 RT로 무한정 재발급하는 것을 막기 위해
     *   "DB(Redis)에 있는 RT와 일치하는 요청만" 허용합니다.
     */
    public TokenResponse reissue(String refreshToken) {
        Long userId = tokenProvider.getUserId(refreshToken);
        String storedRt = redisTemplate.opsForValue().get(RT_PREFIX + userId);

        if (storedRt == null || !storedRt.equals(refreshToken)) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        return issueAndStoreTokens(userId);
    }

    /**
     * 로그아웃합니다. Redis에서 Refresh Token을 삭제합니다.
     *
     * 클라이언트는 로컬에 저장된 AT/RT를 스스로 폐기해야 합니다.
     * 서버에서는 Redis에서 RT만 삭제합니다.
     *
     * AT는 만료될 때까지 유효하지만, RT가 없으면 재발급이 불가능해
     * 결과적으로 로그아웃 효과가 생깁니다.
     */
    public void logout(Long userId) {
        redisTemplate.delete(RT_PREFIX + userId);
    }

    // AT + RT 발급 후 Redis에 RT를 저장하는 공통 로직
    private TokenResponse issueAndStoreTokens(Long userId) {
        String accessToken  = tokenProvider.createAccessToken(userId);
        String refreshToken = tokenProvider.createRefreshToken(userId);

        // Redis에 "RT:{userId}" 키로 RT 저장, 7일 후 자동 만료
        redisTemplate.opsForValue().set(RT_PREFIX + userId, refreshToken, RT_TTL_DAYS, TimeUnit.DAYS);

        return new TokenResponse(accessToken, refreshToken, userId);
    }
}
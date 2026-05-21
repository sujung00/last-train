package com.lasttrain.auth.service;

import com.lasttrain.TestContainerConfig;
import com.lasttrain.auth.dto.LoginRequest;
import com.lasttrain.auth.dto.SignupRequest;
import com.lasttrain.auth.dto.TokenResponse;
import com.lasttrain.auth.repository.UserRepository;
import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthService 통합 테스트
 *
 * ── 이 테스트가 하는 일 ────────────────────────────────────────────────────────
 * 회원가입, 로그인, 토큰 재발급, 로그아웃이 실제 MySQL과 Redis를 통해
 * 올바르게 동작하는지 검증합니다.
 *
 * 단위 테스트(Mock)가 아닌 통합 테스트(실제 DB/Redis)를 사용하는 이유:
 *   Mock은 "가짜 객체"로 실제 DB 저장, Redis 연동, 트랜잭션 커밋 여부를
 *   확인하지 못합니다. 통합 테스트는 실제 환경과 가장 가까운 상태에서
 *   전체 흐름이 맞는지 검증합니다.
 *
 * ── @Transactional을 사용하는 이유 ─────────────────────────────────────────────
 * 각 테스트가 끝나면 DB 변경사항(INSERT, UPDATE 등)을 자동으로 롤백합니다.
 * 덕분에 테스트 실행 순서에 상관없이 각 테스트가 깨끗한 상태에서 시작됩니다.
 *
 * ── Redis는 별도로 정리 ────────────────────────────────────────────────────────
 * Redis 데이터는 JPA 트랜잭션 범위 밖이라 자동 롤백이 되지 않습니다.
 * Redis를 사용하는 테스트에서는 마지막에 직접 삭제합니다.
 * ────────────────────────────────────────────────────────────────────────────────
 */
@Transactional
@DisplayName("AuthService 테스트")
class AuthServiceTest extends TestContainerConfig {

    // Colima(macOS Docker 대안) 환경에서 Docker 소켓 경로를 직접 지정합니다.
    // TestContainerConfig의 static 블록보다 먼저 실행되지 않을 수 있지만,
    // withReuse(true)로 인해 이미 실행 중인 컨테이너가 재사용되므로 무방합니다.
    static {
        System.setProperty("docker.host",
                "unix:///Users/sujung/.colima/default/docker.sock");
        System.setProperty("DOCKER_HOST",
                "unix:///Users/sujung/.colima/default/docker.sock");
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 테스트 전체에서 공통으로 사용하는 이메일/비밀번호
    // 비밀번호는 실제 정책(영문+숫자 8~20자)을 만족하는 값
    private static final String TEST_EMAIL    = "test@example.com";
    private static final String TEST_PASSWORD = "Password1";


    // ── 1번: 이메일 회원가입 성공 ─────────────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   회원가입의 가장 기본 케이스입니다.
    //   단순히 "예외가 안 나면 성공"이 아니라, 실제로 DB에 저장됐는지,
    //   비밀번호가 평문이 아닌 BCrypt 해시로 저장됐는지까지 검증합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이메일 회원가입 성공")
    void 이메일_회원가입_성공() {
        // given: 회원가입에 필요한 이메일과 비밀번호를 준비합니다.
        SignupRequest request = new SignupRequest(TEST_EMAIL, TEST_PASSWORD);

        // when: 회원가입을 실행합니다.
        authService.signup(request);

        // then: 결과를 검증합니다.
        var savedUser = userRepository.findByEmail(TEST_EMAIL);

        assertThat(savedUser).isPresent();                                              // DB에 사용자가 실제로 저장됐는지
        assertThat(savedUser.get().getEmail()).isEqualTo(TEST_EMAIL);                   // 저장된 이메일이 입력값과 같은지
        assertThat(passwordEncoder.matches(TEST_PASSWORD, savedUser.get().getPassword()))
                .isTrue();                                                               // BCrypt 암호화 확인: 평문으로 matches()해야 true (DB 값과 직접 비교 불가)
    }


    // ── 2번: 중복 이메일 회원가입 시 예외 발생 ───────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   같은 이메일로 두 번 가입하면 하나의 이메일로 두 개 계정이 생겨
    //   로그인 시 어느 계정인지 알 수 없어집니다.
    //   이 테스트는 서버가 중복 가입을 올바르게 거부하는지 확인합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("중복 이메일 회원가입 시 예외 발생")
    void 중복_이메일_회원가입_시_예외_발생() {
        // given: 같은 이메일로 첫 번째 회원가입을 완료합니다.
        authService.signup(new SignupRequest(TEST_EMAIL, TEST_PASSWORD));

        // when & then: 동일한 이메일로 두 번째 가입 시도 시 AppException이 발생해야 합니다.
        assertThatThrownBy(() -> authService.signup(new SignupRequest(TEST_EMAIL, TEST_PASSWORD)))
                .isInstanceOf(AppException.class)                                        // AppException이 발생했는지
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_DUPLICATED));                         // 에러코드가 EMAIL_DUPLICATED인지
    }


    // ── 3번: 이메일 로그인 성공 ──────────────────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   로그인 시 AT/RT가 정상 발급되는지, 그리고 RT가 실제 Redis에 저장되는지
    //   확인합니다. Redis에 RT가 없으면 이후 재발급이나 로그아웃이 불가능합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이메일 로그인 성공")
    void 이메일_로그인_성공() {
        // given: 회원가입을 먼저 완료합니다.
        authService.signup(new SignupRequest(TEST_EMAIL, TEST_PASSWORD));

        // when: 올바른 이메일과 비밀번호로 로그인합니다.
        TokenResponse response = authService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD));

        // then: 토큰 발급 여부를 확인합니다.
        assertThat(response).isNotNull();                                                // TokenResponse 자체가 null이 아닌지
        assertThat(response.accessToken()).isNotNull();                                  // Access Token이 발급됐는지
        assertThat(response.refreshToken()).isNotNull();                                 // Refresh Token이 발급됐는지

        // Redis에 Refresh Token이 저장됐는지 확인합니다.
        // AuthService는 "RT:{userId}" 키로 RT를 Redis에 저장합니다.
        String redisKey = AuthService.RT_PREFIX + response.userId();
        String storedRt = redisTemplate.opsForValue().get(redisKey);

        assertThat(storedRt).isNotNull();                                               // Redis에 RT가 저장됐는지
        assertThat(storedRt).isEqualTo(response.refreshToken());                        // 저장된 RT가 응답의 RT와 일치하는지

        // Redis 정리: DB는 @Transactional이 롤백하지만 Redis는 수동 삭제가 필요합니다.
        redisTemplate.delete(redisKey);
    }


    // ── 4번: 존재하지 않는 이메일로 로그인 시 예외 발생 ────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   5번(잘못된 비밀번호)과 짝을 이루는 케이스입니다.
    //   "이메일이 없을 때"도 "비밀번호가 틀렸을 때"와 같은 에러코드를 반환하는지 확인합니다.
    //
    //   두 에러를 구분하지 않는 이유:
    //     "해당 이메일이 존재하지 않습니다"라고 응답하면 공격자가
    //     무작위 이메일을 대입해 실제 가입 여부를 알아낼 수 있습니다. (계정 열거 공격)
    //     따라서 이메일 없음 / 비밀번호 불일치 모두 동일한 INVALID_CREDENTIALS로 처리합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시 예외 발생")
    void 존재하지_않는_이메일로_로그인_시_예외_발생() {
        // given: 아무도 가입하지 않았기 때문에 DB에 이 이메일의 사용자가 없습니다.
        //        (@Transactional 롤백으로 이전 테스트 데이터가 없음이 보장됩니다)

        // when & then: 가입된 적 없는 이메일로 로그인 시 INVALID_CREDENTIALS 예외가 발생해야 합니다.
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", TEST_PASSWORD)))
                .isInstanceOf(AppException.class)                                        // AppException이 발생했는지
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));                      // 에러코드가 INVALID_CREDENTIALS인지 (이메일 없음도 동일 코드)
    }


    // ── 5번: 잘못된 비밀번호 로그인 시 예외 발생 ─────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   틀린 비밀번호로 로그인해도 토큰이 발급되어서는 안 됩니다.
    //   또한 "이메일이 없습니다" vs "비밀번호가 틀렸습니다"를 구분하지 않고
    //   같은 에러코드(INVALID_CREDENTIALS)로 응답하는지 확인합니다.
    //   (구분하면 공격자가 가입 여부를 추측할 수 있어 보안에 취약합니다)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("잘못된 비밀번호 로그인 시 예외 발생")
    void 잘못된_비밀번호_로그인_시_예외_발생() {
        // given: 회원가입을 먼저 완료합니다.
        authService.signup(new SignupRequest(TEST_EMAIL, TEST_PASSWORD));

        // when & then: 틀린 비밀번호로 로그인 시 INVALID_CREDENTIALS 예외가 발생해야 합니다.
        assertThatThrownBy(() -> authService.login(new LoginRequest(TEST_EMAIL, "WrongPass9")))
                .isInstanceOf(AppException.class)                                        // AppException이 발생했는지
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));                      // 에러코드가 INVALID_CREDENTIALS인지
    }


    // ── 6번: Access Token 재발급 성공 ─────────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   AT는 만료 시간(30분)이 짧습니다. 만료 후 RT로 새 AT를 받는 흐름이
    //   핵심 인증 메커니즘입니다. RT가 유효할 때 새 토큰이 정상 발급되는지 확인합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Access Token 재발급 성공")
    void 액세스_토큰_재발급_성공() {
        // given: 회원가입 후 로그인해서 Refresh Token을 받습니다.
        authService.signup(new SignupRequest(TEST_EMAIL, TEST_PASSWORD));
        TokenResponse loginResponse = authService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD));

        // when: 로그인에서 받은 Refresh Token으로 새 토큰을 재발급 요청합니다.
        TokenResponse newResponse = authService.reissue(loginResponse.refreshToken());

        // then: 새 토큰이 정상 발급됐는지 확인합니다.
        assertThat(newResponse).isNotNull();                                             // 새 TokenResponse가 null이 아닌지
        assertThat(newResponse.accessToken()).isNotNull();                               // 새 Access Token이 발급됐는지
        assertThat(newResponse.refreshToken()).isNotNull();                              // 새 Refresh Token이 발급됐는지

        // Redis 정리
        redisTemplate.delete(AuthService.RT_PREFIX + newResponse.userId());
    }


    // ── 7번: Redis RT와 불일치하는 RT로 재발급 시 예외 발생 ─────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   탈취된 RT로 무한정 재발급하는 것을 막는 Refresh Token Rotation을 검증합니다.
    //   서버가 Redis에 보관 중인 RT와 다른 RT로 재발급을 요청하면 거부해야 합니다.
    //
    //   Refresh Token Rotation이란?
    //     재발급 요청 시 "Redis에 있는 RT"와 "요청으로 받은 RT"가 같아야만 허용합니다.
    //     두 값이 다르면 누군가 RT를 탈취해 사용하거나, 이미 만료된 RT를 재사용하는
    //     상황일 수 있으므로 서버가 요청을 거부합니다.
    //
    //   [이전 방식의 문제] 두 번 로그인:
    //     두 번 로그인하면 Redis의 RT가 새 값으로 바뀌어 첫 번째 RT가 구버전이 될 것이라
    //     기대했지만, JWT는 동일한 userId와 만료 시각(millisecond 단위)으로 생성되므로
    //     두 번 로그인해도 완전히 같은 RT가 만들어질 수 있습니다.
    //     즉, "Redis 값 != 요청 RT" 조건이 성립하지 않아 예외가 발생하지 않을 수 있습니다.
    //
    //   [새 방식] Redis 직접 덮어쓰기:
    //     실제 RT로 로그인한 뒤, Redis에 저장된 RT를 가짜 값으로 직접 바꿉니다.
    //     그러면 "Redis 값(가짜) ≠ 요청 RT(진짜)" 조건이 항상 성립해 예외가 확실히 발생합니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis RT와 불일치하는 RT로 재발급 시 예외 발생")
    void Redis_RT와_불일치하는_RT로_재발급_시_예외_발생() {
        // given: 회원가입 후 로그인해서 실제 RT를 받습니다. Redis에는 이 RT가 저장됩니다.
        authService.signup(new SignupRequest(TEST_EMAIL, TEST_PASSWORD));
        TokenResponse loginResponse = authService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD));

        Long userId = loginResponse.userId();
        String redisKey = AuthService.RT_PREFIX + userId;

        // Redis에 저장된 RT를 가짜 값으로 직접 덮어씌웁니다.
        // 이제 Redis: "fake-refresh-token" / 실제 RT: loginResponse.refreshToken() 으로 불일치 상태입니다.
        redisTemplate.opsForValue().set(redisKey, "fake-refresh-token");

        // when & then: Redis 값과 다른 실제 RT로 재발급 요청 시 REFRESH_TOKEN_MISMATCH 예외가 발생해야 합니다.
        assertThatThrownBy(() -> authService.reissue(loginResponse.refreshToken()))
                .isInstanceOf(AppException.class)                                        // AppException이 발생했는지
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REFRESH_TOKEN_MISMATCH));                   // 에러코드가 REFRESH_TOKEN_MISMATCH인지

        // Redis 정리: 가짜 값으로 덮어쓴 키를 삭제합니다.
        redisTemplate.delete(redisKey);
    }


    // ── 8번: 로그아웃 성공 ────────────────────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   로그아웃 후 RT가 Redis에서 실제로 삭제됐는지 확인합니다.
    //   RT가 남아있으면 로그아웃 후에도 탈취된 RT로 재발급이 가능해 보안 취약점이 됩니다.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 성공")
    void 로그아웃_성공() {
        // given: 회원가입 후 로그인합니다. 이 시점에 Redis에 RT가 저장됩니다.
        authService.signup(new SignupRequest(TEST_EMAIL, TEST_PASSWORD));
        TokenResponse loginResponse = authService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD));

        Long userId = loginResponse.userId();
        String redisKey = AuthService.RT_PREFIX + userId;

        // 로그아웃 전에 Redis에 RT가 있는지 사전 확인합니다.
        assertThat(redisTemplate.opsForValue().get(redisKey)).isNotNull();              // 로그아웃 전: RT가 Redis에 있어야 함

        // when: 로그아웃을 실행합니다. 내부에서 Redis의 RT를 삭제합니다.
        authService.logout(userId);

        // then: Redis에서 RT가 삭제됐는지 확인합니다.
        assertThat(redisTemplate.opsForValue().get(redisKey)).isNull();                 // 로그아웃 후: RT가 Redis에서 삭제됐는지
    }
}
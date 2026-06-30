package com.lasttrain.auth.repository;

import com.lasttrain.TestContainerConfig;
import com.lasttrain.auth.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserRepository 통합 테스트
 *
 * ── given / when / then 패턴이란? ───────────────────────────────────────────
 * 테스트를 3단계로 나눠서 읽기 쉽게 만드는 구조입니다.
 *
 *   given (준비): 테스트에 필요한 데이터나 상태를 미리 만드는 단계
 *   when  (실행): 실제로 테스트하려는 동작을 실행하는 단계
 *   then  (검증): 결과가 기대한 것과 같은지 확인하는 단계
 * ────────────────────────────────────────────────────────────────────────────
 */
@Transactional
@DisplayName("UserRepository 테스트")
class UserRepositoryTest extends TestContainerConfig {

    static {
        System.setProperty("docker.host",
                "unix:///Users/sujung/.colima/default/docker.sock");
        System.setProperty("DOCKER_HOST",
                "unix:///Users/sujung/.colima/default/docker.sock");
    }

    @Autowired
    private UserRepository userRepository;

    // ── 조회 테스트 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("이메일로 사용자 조회 성공")
    void findByEmail_성공() {
        // given: 테스트에 사용할 사용자를 DB에 저장합니다.
        User user = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .provider("EMAIL")
                .build();
        userRepository.save(user);

        // when: 저장한 이메일로 사용자를 조회합니다.
        Optional<User> result = userRepository.findByEmail("test@example.com");

        // then: 조회 결과를 검증합니다.
        assertThat(result).isPresent();                                         // Optional에 값이 존재하는지
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");      // 이메일이 일치하는지
        assertThat(result.get().getProvider()).isEqualTo("EMAIL");              // provider 기본값이 "EMAIL"인지
    }

    @Test
    @DisplayName("존재하지 않는 이메일 조회 시 Optional.empty 반환")
    void findByEmail_존재하지_않으면_empty_반환() {
        // given: DB에 아무 사용자도 저장하지 않은 상태입니다.
        // (@Transactional 롤백 덕분에 이전 테스트 데이터가 없습니다)

        // when: DB에 없는 이메일로 조회합니다.
        Optional<User> result = userRepository.findByEmail("nobody@example.com");

        // then: 결과가 없어야 합니다.
        assertThat(result).isEmpty(); // Optional.empty()인지 확인
    }

    @Test
    @DisplayName("카카오 provider + providerId로 조회 성공")
    void findByProviderAndProviderId_카카오_조회_성공() {
        // given: 카카오로 가입한 사용자를 DB에 저장합니다.
        User kakaoUser = User.builder()
                .provider("KAKAO")
                .providerId("kakao-uid-12345")
                .build();
        userRepository.save(kakaoUser);

        // when: provider와 providerId 조합으로 사용자를 조회합니다.
        // 카카오 재로그인 시 "이미 가입된 유저인지" 확인하는 실제 흐름과 동일합니다.
        Optional<User> result = userRepository.findByProviderAndProviderId("KAKAO", "kakao-uid-12345");

        // then: 조회된 사용자의 정보를 검증합니다.
        assertThat(result).isPresent();                                          // 사용자가 조회됐는지
        assertThat(result.get().getProvider()).isEqualTo("KAKAO");               // provider가 "KAKAO"인지
        assertThat(result.get().getProviderId()).isEqualTo("kakao-uid-12345");   // providerId가 일치하는지
    }

    @Test
    @DisplayName("존재하지 않는 provider + providerId 조회 시 Optional.empty 반환")
    void findByProviderAndProviderId_존재하지_않으면_empty_반환() {
        // given: DB에 아무 카카오 사용자도 없는 상태입니다.

        // when: DB에 없는 providerId로 조회합니다.
        Optional<User> result = userRepository.findByProviderAndProviderId("KAKAO", "없는ID");

        // then: 결과가 없어야 합니다.
        assertThat(result).isEmpty(); // Optional.empty()인지 확인
    }

    // ── 중복 여부 테스트 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 이메일 중복 여부 확인 시 true 반환")
    void existsByEmail_존재하는_이메일은_true_반환() {
        // given: 이미 가입된 사용자를 DB에 저장합니다.
        User user = User.builder()
                .email("exists@example.com")
                .password("encodedPassword")
                .provider("EMAIL")
                .build();
        userRepository.save(user);

        // when & then: 등록된 이메일은 true를 반환하는지 확인합니다.
        assertThat(userRepository.existsByEmail("exists@example.com")).isTrue(); // 이미 있는 이메일 → true
    }

    @Test
    @DisplayName("존재하지 않는 이메일 중복 여부 확인 시 false 반환")
    void existsByEmail_존재하지_않는_이메일은_false_반환() {
        // given: DB에 아무 사용자도 없는 상태입니다.

        // when & then: 없는 이메일은 false를 반환하는지 확인합니다.
        assertThat(userRepository.existsByEmail("new@example.com")).isFalse(); // 없는 이메일 → false
    }

    // ── 저장 검증 테스트 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("User 저장 성공 후 userId 자동 생성 확인")
    void save_성공_후_userId_자동_생성() {
        // given: 저장할 사용자 객체를 만듭니다.
        // userId를 직접 지정하지 않아도 DB가 AUTO_INCREMENT로 자동 부여합니다.
        User user = User.builder()
                .email("newuser@example.com")
                .password("encodedPassword")
                .provider("EMAIL")
                .build();

        // when: DB에 저장합니다.
        User savedUser = userRepository.save(user);

        // then: 저장된 사용자의 userId를 검증합니다.
        assertThat(savedUser.getUserId()).isNotNull();          // userId가 자동 생성됐는지 (null이 아닌지)
        assertThat(savedUser.getUserId()).isGreaterThan(0L);    // userId가 양수인지 (AUTO_INCREMENT는 1부터 시작)
    }

    @Test
    @DisplayName("User 저장 시 createdAt 자동 설정 확인")
    void save_성공_후_createdAt_자동_설정() {
        // given: 저장할 사용자 객체를 만듭니다.
        // createdAt을 직접 지정하지 않아도 @CreatedDate가 자동으로 현재 시각을 넣어줍니다.
        LocalDateTime beforeSave = LocalDateTime.now();

        User user = User.builder()
                .email("audit@example.com")
                .password("encodedPassword")
                .provider("EMAIL")
                .build();

        // when: DB에 저장합니다.
        User savedUser = userRepository.save(user);

        // then: createdAt이 자동으로 설정됐는지 검증합니다.
        assertThat(savedUser.getCreatedAt()).isNotNull();                       // createdAt이 null이 아닌지
        assertThat(savedUser.getCreatedAt()).isAfterOrEqualTo(beforeSave);     // 저장 시각이 테스트 시작 이후인지
        assertThat(savedUser.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now()); // 저장 시각이 현재 이전인지
    }

    // ── 제약 조건 위반 테스트 ────────────────────────────────────────────────
    //
    // 아래 두 테스트에서 클래스 레벨 @Transactional을 비활성화한 이유:
    //
    // @Transactional이 걸린 테스트에서는 save()가 트랜잭션 안에 묶입니다.
    // 이 경우 Hibernate가 실제 INSERT를 트랜잭션 커밋 시점까지 지연할 수 있어서
    // DB 제약 조건 위반(DataIntegrityViolationException)이 예상한 위치에서
    // 발생하지 않을 수 있습니다.
    //
    // NOT_SUPPORTED로 설정하면 클래스 레벨 트랜잭션이 일시 중단됩니다.
    // 각 save() 호출이 독립적인 트랜잭션으로 즉시 커밋되므로
    // 두 번째 save() 시점에 DB가 제약 조건을 확인하고 예외를 바로 던집니다.
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일 이메일 중복 저장 시 예외 발생")
    void save_동일_이메일_중복_시_예외_발생() {
        // given: 첫 번째 사용자를 저장합니다. (즉시 커밋)
        User first = User.builder()
                .email("duplicate@example.com")
                .password("encodedPassword")
                .provider("EMAIL")
                .build();
        userRepository.save(first);

        // when & then: 동일한 이메일로 두 번째 저장 시도 시 예외가 발생해야 합니다.
        // user 테이블의 email 컬럼에 UNIQUE 제약이 걸려있기 때문입니다.
        User second = User.builder()
                .email("duplicate@example.com") // 같은 이메일
                .password("anotherPassword")
                .provider("EMAIL")
                .build();

        assertThatThrownBy(() -> userRepository.save(second))
                .isInstanceOf(DataIntegrityViolationException.class); // DB 제약 조건 위반 예외인지

        // cleanup: NOT_SUPPORTED라 롤백이 없으므로 직접 삭제합니다.
        userRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일 provider + providerId 중복 저장 시 예외 발생")
    void save_동일_provider_providerId_중복_시_예외_발생() {
        // given: 첫 번째 카카오 사용자를 저장합니다. (즉시 커밋)
        User first = User.builder()
                .provider("KAKAO")
                .providerId("dup-kakao-99999")
                .build();
        userRepository.save(first);

        // when & then: 동일한 provider + providerId로 두 번째 저장 시도 시 예외가 발생해야 합니다.
        // notification_subscription 테이블의 UNIQUE KEY uq_provider(provider, provider_id) 때문입니다.
        // 같은 카카오 계정으로 중복 가입을 방지하기 위한 제약입니다.
        User second = User.builder()
                .provider("KAKAO")          // 같은 provider
                .providerId("dup-kakao-99999") // 같은 providerId
                .build();

        assertThatThrownBy(() -> userRepository.save(second))
                .isInstanceOf(DataIntegrityViolationException.class); // DB 제약 조건 위반 예외인지

        // cleanup: NOT_SUPPORTED라 롤백이 없으므로 직접 삭제합니다.
        userRepository.deleteAll();
    }
}

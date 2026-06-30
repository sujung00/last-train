package com.lasttrain.auth.repository;

import com.lasttrain.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 테이블에 접근하는 Repository 인터페이스
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자를 조회합니다.
     */
    Optional<User> findByEmail(String email);

    /**
     * 소셜 로그인 제공자와 제공자 고유 ID로 사용자를 조회합니다.
     *
     * 언제 쓰이나요?
     *   카카오 로그인 시 카카오에서 받아온 사용자 식별자(providerId)로
     *   이미 가입된 사용자인지 확인할 때 사용합니다.
     *
     *   처음 카카오 로그인 → DB에 없음 → 신규 회원가입 처리
     *   재방문 카카오 로그인 → DB에 있음 → 바로 JWT 발급
     *
     * 파라미터 설명:
     *   provider   → 소셜 로그인 종류 (예: "KAKAO")
     *   providerId → 카카오가 부여한 사용자 고유 번호 (예: "123456789")
     */
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    /**
     * 해당 이메일로 가입한 사용자가 이미 있는지 확인합니다.
     *
     * 언제 쓰이나요?
     *   회원가입 시 이메일 중복 검사에 사용합니다.
     *   이미 사용 중인 이메일이면 가입을 거부합니다.
     *
     * existsBy를 쓰는 이유:
     *   findByEmail()로도 확인할 수 있지만,
     *   existsBy는 User 객체 전체를 가져오지 않고
     *   "있냐/없냐"만 확인하므로 더 가볍습니다.
     *   → SELECT COUNT(*) > 0 FROM user WHERE email = ?
     */
    boolean existsByEmail(String email);
}

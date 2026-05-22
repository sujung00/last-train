package com.lasttrain.global.security;

import com.lasttrain.auth.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security가 인증 정보를 담아두는 객체입니다.
 *
 * SecurityContext(인증 정보 보관함)에 저장되며, 이후 요청에서
 * "지금 로그인한 사람이 누구인가?"를 확인할 때 꺼내 씁니다.
 *
 * 왜 User를 직접 쓰지 않나요?
 *   Spring Security는 인증 객체의 형태를 UserDetails 인터페이스로 규정합니다.
 *   User 엔티티가 그 규격을 직접 구현하면 JPA 엔티티와 보안 계층이 뒤섞입니다.
 *   SecurityUserDetails가 중간에서 User를 UserDetails 형태로 변환해 줍니다.
 */
@Getter
public class SecurityUserDetails implements UserDetails {

    // 컨트롤러에서 @AuthenticationPrincipal SecurityUserDetails userDetails 로
    // 현재 로그인한 사용자의 ID를 꺼낼 때 사용합니다.
    private final Long userId;

    // DB에서 조회한 실제 사용자 정보를 보관합니다.
    private final User user;

    public SecurityUserDetails(User user) {
        this.user = user;
        this.userId = user.getUserId();
    }

    // 현재는 별도 권한 시스템이 없어 빈 목록을 반환합니다.
    // 추후 ROLE_USER, ROLE_ADMIN 등 권한을 도입하면 이 메서드에서 반환합니다.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    // DB에 저장된 BCrypt 암호화 비밀번호를 반환합니다.
    // JWT 방식에서는 필터가 비밀번호로 인증하지 않지만, Spring Security 규격상 구현합니다.
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    // Spring Security에서 "사용자 이름" 역할을 합니다.
    // 이 프로젝트에서는 userId를 문자열로 변환해 식별자로 사용합니다.
    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }

    // 계정 만료 여부입니다.
    // User 엔티티에 만료 시각 필드가 없어 항상 만료되지 않은 것으로 처리합니다.
    // 추후 User에 expiredAt 필드를 추가하면: return user.getExpiredAt() == null || user.getExpiredAt().isAfter(LocalDateTime.now())
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠금 여부입니다.
    // User 엔티티에 잠금 필드가 없어 항상 잠금 해제 상태로 처리합니다.
    // 추후 User에 boolean locked 필드를 추가하면: return !user.isLocked()
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 자격증명(비밀번호) 만료 여부입니다.
    // JWT 방식에서는 토큰 만료를 TokenProvider가 처리하므로 항상 유효로 처리합니다.
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 계정 활성화 여부입니다.
    // User 엔티티에 활성화 필드가 없어 DB에 존재하는 사용자는 모두 활성 상태로 처리합니다.
    // 추후 User에 boolean enabled 필드를 추가하면: return user.isEnabled()
    // (탈퇴 처리, 운영자 정지 등의 기능을 구현할 때 활용합니다)
    @Override
    public boolean isEnabled() {
        return true;
    }
}
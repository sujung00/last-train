package com.lasttrain.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class SecurityUserDetails implements UserDetails {

    private final Long userId;

    public SecurityUserDetails(Long userId) {
        this.userId = userId;
    }

    // TODO: User 엔티티 연동 후 실제 권한으로 교체 (예: ROLE_USER, ROLE_ADMIN)
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    // JWT 인증 방식에서는 password 미사용
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }

    // 계정 만료 여부
    // TODO: User 엔티티 연동 후 user.isAccountNonExpired() 연동
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠금 여부
    // TODO: User 엔티티 연동 후 user.isAccountNonLocked() 연동
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 비밀번호 만료 여부
    // 자격증명(비밀번호) 만료는 JWT 방식에서 TokenProvider가 처리
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 계정 활성화 여부
    // TODO: User 엔티티 연동 후 user.isEnabled() 연동 (탈퇴/정지 계정 처리)
    @Override
    public boolean isEnabled() {
        return true;
    }
}
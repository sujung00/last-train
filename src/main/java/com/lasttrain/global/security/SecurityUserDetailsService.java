package com.lasttrain.global.security;

import com.lasttrain.auth.domain.User;
import com.lasttrain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security가 "이 사용자가 실제로 존재하는가?"를 확인할 때 호출하는 서비스입니다.
 *
 * 언제 호출되나요?
 *   JwtAuthenticationFilter가 요청 헤더의 JWT 토큰을 검증한 뒤,
 *   토큰에서 꺼낸 userId로 이 메서드를 호출합니다.
 *   DB에서 실제 사용자를 조회해 SecurityContext(인증 정보 보관함)에 등록합니다.
 *
 * 왜 DB를 조회하나요?
 *   토큰이 유효해도 그 사이에 탈퇴한 사용자일 수 있습니다.
 *   DB를 직접 조회해야 "토큰은 유효하지만 사용자는 없는" 상황을 잡을 수 있습니다.
 */
@Service
@RequiredArgsConstructor
public class SecurityUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * @param userId JWT 토큰에서 꺼낸 사용자 ID (문자열 형태)
     * @throws UsernameNotFoundException DB에 해당 사용자가 없을 때 발생
     */
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다. userId=" + userId));

        return new SecurityUserDetails(user);
    }
}
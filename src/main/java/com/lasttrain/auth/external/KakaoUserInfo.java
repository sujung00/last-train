package com.lasttrain.auth.external;

/**
 * 카카오 사용자 정보 API(/v2/user/me) 응답에서 필요한 값만 꺼내 담는 record입니다.
 *
 * @param id       카카오 사용자 고유 ID (서비스 내부에서 provider_id로 사용)
 * @param email    카카오 계정 이메일 (kakao_account.email)
 * @param nickname 카카오 닉네임 (properties.nickname)
 */
public record KakaoUserInfo(
        Long id,
        String email,
        String nickname
) {}

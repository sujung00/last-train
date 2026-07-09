package com.lasttrain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT 토큰 응답")
public record TokenResponse(
        @Schema(description = "Access Token (30분)", example = "eyJhbGciOiJIUzI1NiJ9.access")
        String accessToken,

        @Schema(description = "Refresh Token (7일)", example = "eyJhbGciOiJIUzI1NiJ9.refresh")
        String refreshToken,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "사용자 이메일 (선택사항, 카카오 로그인 시 null 가능)", example = "user@example.com")
        String email
) {}
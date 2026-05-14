package com.lasttrain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 소셜 로그인 요청")
public record KakaoLoginRequest(
        @Schema(
                description = "카카오 인가 코드 (프론트에서 카카오 redirect 후 수신)",
                example = "ABC123xyz",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "인가 코드를 입력해주세요.")
        String code
) {}
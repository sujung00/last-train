package com.lasttrain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(
                description = "이메일",
                example = "user@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @Schema(
                description = "비밀번호",
                example = "password123!",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {}
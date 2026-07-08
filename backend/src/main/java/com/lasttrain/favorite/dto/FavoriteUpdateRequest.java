package com.lasttrain.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "즐겨찾기 수정 요청 (이름/이모지만)")
public record FavoriteUpdateRequest(
        @Schema(
                description = "즐겨찾기 이름",
                example = "집",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "즐겨찾기 이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Schema(description = "이모지", example = "🏠", nullable = true)
        String emoji
) {}

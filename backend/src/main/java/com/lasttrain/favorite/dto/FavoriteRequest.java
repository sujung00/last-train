package com.lasttrain.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "즐겨찾기 등록/수정 요청")
public record FavoriteRequest(
        @Schema(
                description = "즐겨찾기 이름",
                example = "집",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "즐겨찾기 이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Schema(description = "이모지", example = "🏠", nullable = true)
        String emoji,

        @Schema(
                description = "위도",
                example = "37.5172",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "위도를 입력해주세요.")
        @DecimalMin(value = "-90", message = "위도는 -90 ~ 90 사이여야 합니다")
        @DecimalMax(value = "90", message = "위도는 -90 ~ 90 사이여야 합니다")
        Double lat,

        @Schema(
                description = "경도",
                example = "127.0473",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "경도를 입력해주세요.")
        @DecimalMin(value = "-180", message = "경도는 -180 ~ 180 사이여야 합니다")
        @DecimalMax(value = "180", message = "경도는 -180 ~ 180 사이여야 합니다")
        Double lng,

        @Schema(description = "주소", example = "서울특별시 강남구 삼성동", nullable = true)
        String address
) {}
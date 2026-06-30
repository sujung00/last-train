package com.lasttrain.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "즐겨찾기 응답")
public record FavoriteResponse(
        @Schema(description = "즐겨찾기 ID", example = "1")
        Long id,

        @Schema(description = "즐겨찾기 이름", example = "집")
        String name,

        @Schema(description = "이모지", example = "🏠", nullable = true)
        String emoji,

        @Schema(description = "위도", example = "37.5172")
        Double lat,

        @Schema(description = "경도", example = "127.0473")
        Double lng,

        @Schema(description = "주소", example = "서울특별시 강남구 삼성동", nullable = true)
        String address,

        @Schema(description = "등록 일시", example = "2026-05-11T10:00:00")
        LocalDateTime createdAt
) {}
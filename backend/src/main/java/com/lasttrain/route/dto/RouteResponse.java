package com.lasttrain.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "막차 경로 조회 응답")
public record RouteResponse(
        @Schema(description = "출발지 명칭", example = "강남구청")
        String origin,

        @Schema(description = "목적지 명칭", example = "부천역")
        String destination,

        @Schema(description = "조회 기준 날짜", example = "2026-05-11")
        LocalDate date,

        @Schema(description = "요일 유형", example = "WEEKDAY", allowableValues = {"WEEKDAY", "SAT", "SUN"})
        String dayType,

        @Schema(description = "경로 목록 (departureDeadline 내림차순 정렬)")
        List<RouteItem> routes
) {
    @Schema(description = "경로 항목")
    public record RouteItem(
            @Schema(description = "출발 마감 시각 (HH:mm)", example = "23:11")
            String departureDeadline,

            @Schema(description = "현재 탑승 가능 상태")
            CurrentStatus currentStatus,

            @Schema(description = "환승 구간 목록")
            List<TransferDto> transfers
    ) {}

    @Schema(description = "현재 탑승 상태")
    public record CurrentStatus(
            @Schema(description = "막차 탑승 가능 여부", example = "true")
            boolean canCatch,

            @Schema(description = "막차까지 남은 시간 (분)", example = "35")
            int minutesLeft,

            @Schema(description = "안내 메시지", example = "막차까지 35분 남았어요!")
            String message
    ) {}
}
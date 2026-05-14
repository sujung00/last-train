package com.lasttrain.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "환승 구간")
public record TransferDto(
        @Schema(description = "교통 수단", example = "SUBWAY", allowableValues = {"SUBWAY", "BUS"})
        String type,

        @Schema(description = "노선명", example = "2호선")
        String line,

        @Schema(description = "승차 정류장/역", example = "강남역")
        String boardAt,

        @Schema(description = "하차 정류장/역", example = "신도림역")
        String alightAt,

        @Schema(description = "막차 탑승 시각 (HH:mm). busLastTime null인 경우 없을 수 있음.",
                example = "23:11", nullable = true)
        String lastBoardTime
) {}
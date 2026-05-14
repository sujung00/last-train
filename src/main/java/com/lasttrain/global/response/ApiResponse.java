package com.lasttrain.global.response;

import com.lasttrain.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공통 응답 구조")
public record ApiResponse<T>(
        @Schema(description = "성공 여부", example = "true")
        boolean success,

        @Schema(description = "응답 데이터")
        T data,

        @Schema(description = "에러 정보")
        ErrorDetail error,

        @Schema(description = "응답 시각", example = "2026-05-11T21:00:00")
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null,
                new ErrorDetail(errorCode.name(), errorCode.getMessage()), LocalDateTime.now());
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null,
                new ErrorDetail(code, message), LocalDateTime.now());
    }

    @Schema(description = "에러 상세")
    public record ErrorDetail(
            @Schema(description = "에러 코드", example = "INVALID_CREDENTIALS")
            String code,

            @Schema(description = "에러 메시지", example = "이메일 또는 비밀번호가 올바르지 않습니다.")
            String message
    ) {}
}
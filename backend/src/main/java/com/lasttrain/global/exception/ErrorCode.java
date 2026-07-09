package com.lasttrain.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "Refresh Token이 일치하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    KAKAO_AUTH_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "카카오 인증 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),

    // Route
    ODSAY_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "경로 조회 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    NO_ROUTE_FOUND(HttpStatus.NOT_FOUND, "해당 경로를 찾을 수 없습니다."),

    // Favorite
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "즐겨찾기를 찾을 수 없습니다."),
    FAVORITE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 즐겨찾기만 수정할 수 있습니다."),

    // Notification
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 정보를 찾을 수 없습니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
package com.lasttrain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 알림 구독 등록 요청 DTO입니다.
 *
 * 프론트엔드는 브라우저 PushManager.subscribe() 결과로 받은
 * endpoint, auth, p256dh 세 값을 백엔드로 전달합니다.
 * 백엔드는 이 값들을 저장해뒀다가 알림을 보낼 때 사용합니다.
 */
@Schema(description = "알림 구독 등록 요청")
public record SubscribeRequest(

        @Schema(description = "브라우저 Push 서비스 고유 주소", example = "https://fcm.googleapis.com/fcm/send/...")
        @NotBlank(message = "endpoint를 입력해주세요.")
        String endpoint,

        @Schema(description = "메시지 암호화 인증 비밀값 (Base64)", example = "tBHItJI5svbpez7KI4CCXg==")
        @NotBlank(message = "auth를 입력해주세요.")
        String auth,

        @Schema(description = "메시지 암호화 공개키 (Base64)", example = "BNcRdreALRFXTkOOUHK...")
        @NotBlank(message = "p256dh를 입력해주세요.")
        String p256dh,

        @Schema(description = "출발지 이름", example = "강남구청")
        @NotBlank(message = "출발지를 입력해주세요.")
        String origin,

        @Schema(description = "목적지 이름", example = "부천역")
        @NotBlank(message = "목적지를 입력해주세요.")
        String destination,

        @Schema(description = "막차 탑승 마감 시각 (자정 넘김 포함)", example = "2026-06-10T23:11:00")
        @NotNull(message = "막차 시각을 입력해주세요.")
        LocalDateTime lastBoardTime,

        @Schema(description = "막차 몇 분 전에 알림을 받을지", example = "30")
        @NotNull(message = "알림 시간을 입력해주세요.")
        Integer notifyMinutesBefore

) {}

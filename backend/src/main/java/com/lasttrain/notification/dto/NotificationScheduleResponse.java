package com.lasttrain.notification.dto;

import com.lasttrain.notification.domain.NotificationSchedule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 알림 구독 목록 조회 응답 DTO입니다.
 *
 * 사용자의 구독 목록을 조회할 때 반환합니다.
 */
@Schema(description = "알림 구독 정보")
public record NotificationScheduleResponse(

        @Schema(description = "구독 ID", example = "1")
        Long subscriptionId,

        @Schema(description = "출발지 이름", example = "강남구청")
        String origin,

        @Schema(description = "목적지 이름", example = "부천역")
        String destination,

        @Schema(description = "막차 탑승 마감 시각", example = "2026-07-09T01:08:00")
        LocalDateTime lastBoardTime,

        @Schema(description = "막차 몇 분 전에 알림을 받을지", example = "30")
        Integer notifyMinutesBefore

) {

    /**
     * NotificationSchedule 엔티티를 응답 DTO로 변환합니다.
     */
    public static NotificationScheduleResponse from(NotificationSchedule schedule) {
        return new NotificationScheduleResponse(
                schedule.getSubscription().getSubscriptionId(),
                schedule.getOrigin(),
                schedule.getDestination(),
                schedule.getLastBoardTime(),
                schedule.getNotifyMinutesBefore()
        );
    }
}

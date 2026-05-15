package com.lasttrain.notification.scheduler;

import com.lasttrain.notification.service.NotificationQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Delay Queue 기반 알림 스케줄러
 *
 * ─────────────────────────────────────────────────────────────────
 * 기존 방식 (제거됨):
 *   @Scheduled(60초) → DB polling (WHERE BETWEEN) → notified 컬럼 UPDATE
 *
 * 변경 방식:
 *   @Scheduled(1초) → Redis ZSET atomic pop → WebPush 발송
 * ─────────────────────────────────────────────────────────────────
 *
 * 처리 흐름:
 *   1. 1초마다 Redis ZSET에서 score <= 현재시각인 항목을 Lua script로 꺼냄
 *   2. 꺼낸 항목은 ZSET에서 즉시 제거됨 (원자적) → 중복 발송 불가
 *   3. "{scheduleId}:{minutesBefore}" 형식 파싱
 *   4. DB에서 구독 정보 조회 → WebPush 발송
 *   5. 실패 시 로그만 기록 (MVP: retry 미포함)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    // Redis ZSET 조작 전담 서비스
    private final NotificationQueueService queueService;

    // TODO: WebPushService 구현 후 주입
    //   - VAPID 키로 Web Push 발송하는 서비스
    //   - 라이브러리: nl.martijndwars:web-push:5.x.x
    // private final WebPushService webPushService;

    // TODO: ScheduleRepository 구현 후 주입
    //   - scheduleId로 NotificationSchedule + Subscription을 JOIN FETCH 조회
    // private final ScheduleRepository scheduleRepository;

    /**
     * Redis Delay Queue Worker
     *
     * fixedDelay = 1_000 의미:
     *   이전 실행이 완료된 후 1초 뒤에 다시 실행합니다.
     *   (fixedRate와 다르게 실행 시간이 1초 초과해도 겹치지 않음)
     *
     * 왜 1초인가?
     *   - Redis in-memory 조회는 부하가 거의 없어서 1초도 충분히 가능
     *   - 기존 60초 → 최대 60초 지연이었던 것이 1초 이내로 개선됨
     *   - 처리할 항목이 없으면 즉시 반환하므로 낭비 없음
     */
    @Scheduled(fixedDelay = 1_000)
    public void processQueue() {
        // Redis에서 실행 시점이 된 항목들을 원자적으로 꺼냄
        // 꺼낸 순간 ZSET에서 제거되므로 다른 서버가 중복 처리 불가
        List<String> dueItems = queueService.popDue();

        // 처리할 항목이 없으면 즉시 종료 (로그도 남기지 않음 - 초당 실행이라 노이즈 발생)
        if (dueItems.isEmpty()) {
            return;
        }

        log.debug("[Queue Worker] 처리 대상 {}개", dueItems.size());

        // 꺼낸 항목을 하나씩 처리
        for (String item : dueItems) {
            processItem(item);
        }
    }

    /**
     * 개별 알림 항목 처리
     *
     * item 형식: "{scheduleId}:{minutesBefore}"
     *   예) "42:30" → scheduleId=42, 막차 30분 전 알림
     *   예) "55:10" → scheduleId=55, 막차 10분 전 알림
     *
     * @param item Redis ZSET에서 꺼낸 값
     */
    private void processItem(String item) {
        try {
            // ① item 파싱: "42:30" → ["42", "30"]
            String[] parts = item.split(":");
            if (parts.length != 2) {
                log.error("[파싱 오류] 잘못된 Queue 항목 형식: item={}", item);
                return;
            }
            Long scheduleId = Long.parseLong(parts[0]);
            int minutesBefore = Integer.parseInt(parts[1]);

            log.info("[Push 발송 시작] scheduleId={}, minutesBefore={}분", scheduleId, minutesBefore);

            // ② DB에서 알림 예약 정보 + 구독 정보 조회
            // TODO: ScheduleRepository 구현 후 아래 코드로 교체
            //
            // NotificationSchedule schedule = scheduleRepository
            //     .findWithSubscription(scheduleId)  // JOIN FETCH로 N+1 방지
            //     .orElseThrow(() -> new IllegalStateException(
            //         "알림 예약 없음: scheduleId=" + scheduleId));

            // ③ 알림 메시지 생성 및 WebPush 발송
            // TODO: WebPushService 구현 후 아래 코드로 교체
            //
            // String message = minutesBefore == 30
            //     ? "막차까지 30분 남았어요! " + schedule.getOrigin() + " → " + schedule.getDestination()
            //     : "막차까지 10분 남았어요! 지금 출발하세요!";
            //
            // webPushService.send(schedule.getSubscription(), message);

            log.info("[Push 발송 완료] scheduleId={}", scheduleId);

        } catch (Exception e) {
            // 실패 시 로그만 기록하고 계속 진행
            // 이유: 한 건 실패가 전체 배치를 중단시키면 안 됨
            //
            // MVP에서 retry 미포함.
            // 향후 개선 방향:
            //   - retry_count 필드 추가 후 재시도 로직 구현
            //   - DLQ(Dead Letter Queue)로 실패 항목 별도 관리
            log.error("[Push 실패] item={}, 사유={}", item, e.getMessage(), e);
        }
    }
}
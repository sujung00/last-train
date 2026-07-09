package com.lasttrain.notification.service;

import com.lasttrain.auth.domain.User;
import com.lasttrain.auth.repository.UserRepository;
import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import com.lasttrain.notification.domain.NotificationSchedule;
import com.lasttrain.notification.domain.NotificationSubscription;
import com.lasttrain.notification.dto.NotificationScheduleResponse;
import com.lasttrain.notification.dto.SubscribeRequest;
import com.lasttrain.notification.repository.ScheduleRepository;
import com.lasttrain.notification.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional // 모든 메서드가 DB 쓰기 작업을 포함하므로 클래스 레벨 트랜잭션 적용
public class NotificationService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ScheduleRepository scheduleRepository;
    private final NotificationQueueService notificationQueueService;

    /**
     * 막차 알림을 구독합니다.
     *
     * 같은 브라우저(endpoint)로 이미 구독한 이력이 있으면 재구독(upsert)으로 처리합니다.
     * 이 경우 기존 알림 예약을 모두 제거하고 새 예약으로 교체합니다.
     *
     * @param request 구독 요청 정보 (endpoint, auth, p256dh, 출발지, 목적지, 막차 시각)
     * @param userId  JWT에서 추출한 현재 로그인 사용자의 ID
     */
    public void subscribe(SubscribeRequest request, Long userId) {
        User user = findUser(userId);

        // ── Step 1. 같은 브라우저로 이미 구독한 적 있는지 확인 ────────────────────────
        //
        // 같은 사람이 같은 브라우저에서 다시 구독하면 endpoint가 동일합니다.
        // 이때 구독을 새로 만들면 중복 알림이 발생할 수 있어서
        // 기존 구독을 찾아서 예약만 교체합니다. (upsert 패턴)
        Optional<NotificationSubscription> existingOpt =
                subscriptionRepository.findByUserAndEndpoint(user, request.endpoint());

        NotificationSubscription subscription;

        if (existingOpt.isPresent()) {
            // ── 기존 구독이 있는 경우: 알림 예약만 교체 ─────────────────────────────────
            subscription = existingOpt.get();

            // 기존 예약들을 Redis Delay Queue에서 먼저 취소합니다.
            // Redis 취소를 먼저 해야 하는 이유:
            //   DB에서 삭제하면 scheduleId를 더 이상 알 수 없어서
            //   Redis에서 "어떤 항목을 지워야 하는지" 파악이 불가능해집니다.
            List<NotificationSchedule> existingSchedules =
                    scheduleRepository.findAllBySubscription(subscription);
            existingSchedules.forEach(s -> notificationQueueService.cancel(s.getScheduleId()));

            // Redis 취소 완료 후 DB에서도 삭제합니다.
            scheduleRepository.deleteAll(existingSchedules);

        } else {
            // ── 새 구독인 경우: NotificationSubscription 저장 ────────────────────────────
            subscription = subscriptionRepository.save(
                    NotificationSubscription.builder()
                            .user(user)
                            .endpoint(request.endpoint())
                            .auth(request.auth())
                            .p256dh(request.p256dh())
                            .build()
            );
        }

        // ── Step 2. 새 알림 예약 저장 ──────────────────────────────────────────────────
        //
        // 구독(subscription)과 막차 정보를 연결해서 저장합니다.
        // save()가 완료되면 DB가 자동 생성한 scheduleId를 얻을 수 있습니다.
        NotificationSchedule schedule = scheduleRepository.save(
                NotificationSchedule.builder()
                        .subscription(subscription)
                        .origin(request.origin())
                        .destination(request.destination())
                        .notifyMinutesBefore(request.notifyMinutesBefore())
                        .lastBoardTime(request.lastBoardTime())
                        .build()
        );

        // ── Step 3. Redis Delay Queue에 알림 발송 시점 등록 ────────────────────────────
        //
        // Redis ZSET에 "{scheduleId}:30" (30분 전), "{scheduleId}:10" (10분 전) 항목을 추가합니다.
        // Worker(@Scheduled)가 1초마다 Queue를 확인해서 시점이 된 항목을 꺼내 알림을 보냅니다.
        notificationQueueService.enqueue(schedule.getScheduleId(), schedule.getLastBoardTime());
    }

    /**
     * 현재 로그인한 사용자의 알림 구독 목록을 조회합니다.
     *
     * @param userId 조회할 사용자의 ID
     * @return 사용자의 알림 구독 목록 (구독별 경로 정보 포함)
     */
    @Transactional(readOnly = true)
    public List<NotificationScheduleResponse> getMySubscriptions(Long userId) {
        User user = findUser(userId);

        // 사용자의 구독 목록 조회
        List<NotificationSubscription> subscriptions =
                subscriptionRepository.findByUser(user);

        // 각 구독에 연결된 알림 예약 조회 후 응답 DTO로 변환
        return subscriptions.stream()
                .flatMap(subscription ->
                        scheduleRepository.findAllBySubscription(subscription).stream()
                                .map(NotificationScheduleResponse::from)
                )
                .toList();
    }

    /**
     * 막차 알림 구독을 취소합니다.
     *
     * Redis와 DB를 모두 정리해야 하는 이유:
     *   Redis만 취소하면 DB에 불필요한 데이터가 쌓이고,
     *   DB만 삭제하면 Redis에 남은 항목이 발송 시점에 DB를 조회했을 때
     *   "예약 정보 없음" 오류가 발생합니다.
     *   두 곳 모두 정리해야 깔끔하게 취소됩니다.
     *
     * @param subscriptionId 취소할 구독 ID
     * @param userId         JWT에서 추출한 현재 로그인 사용자의 ID
     */
    public void unsubscribe(Long subscriptionId, Long userId) {
        // ── Step 1. 구독 조회 ────────────────────────────────────────────────────────────
        NotificationSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        // ── Step 2. 본인 구독인지 확인 ──────────────────────────────────────────────────
        //
        // 다른 사람의 구독을 취소하지 못하도록 막습니다.
        // Hibernate는 Lazy 프록시에서 ID 접근 시 추가 DB 조회 없이 처리합니다.
        if (!subscription.getUser().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FAVORITE_ACCESS_DENIED);
        }

        // ── Step 3. Redis Delay Queue에서 예약 취소 ─────────────────────────────────────
        //
        // DB 삭제 전에 Redis를 먼저 정리합니다.
        // DB를 먼저 삭제하면 scheduleId를 알 수 없어 Redis 취소가 불가능합니다.
        List<NotificationSchedule> schedules =
                scheduleRepository.findAllBySubscription(subscription);
        schedules.forEach(s -> notificationQueueService.cancel(s.getScheduleId()));

        // ── Step 4. DB에서 구독 삭제 ────────────────────────────────────────────────────
        //
        // ON DELETE CASCADE 설정으로 notification_schedule도 자동 삭제됩니다.
        // (notification_subscription 삭제 → 연결된 notification_schedule 자동 삭제)
        subscriptionRepository.delete(subscription);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * userId로 User를 조회합니다. 없으면 예외를 던집니다.
     *
     * JWT 인증을 통과한 사용자라면 반드시 DB에 존재해야 합니다.
     * 없다면 탈퇴 후 잔여 토큰 사용 등 비정상 상황이므로 예외 처리합니다.
     */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
    }
}

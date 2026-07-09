package com.lasttrain.notification.repository;

import com.lasttrain.notification.domain.NotificationSchedule;
import com.lasttrain.notification.domain.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 막차 알림 예약 정보를 DB에서 조회/저장/삭제하는 Repository입니다.
 *
 * JpaRepository를 상속하면 save(), findById(), delete() 같은
 * 기본 CRUD 메서드를 자동으로 사용할 수 있습니다.
 */
public interface ScheduleRepository extends JpaRepository<NotificationSchedule, Long> {

    /**
     * 특정 구독에 연결된 알림 예약 목록을 전부 가져옵니다.
     *
     * 언제 쓰이나요?
     *   사용자가 구독을 취소할 때, 해당 구독에 걸린 모든 알림 예약을
     *   Redis Delay Queue에서 제거하기 위해 scheduleId 목록을 얻을 때 사용합니다.
     *
     * @param subscription 조회할 구독
     * @return 해당 구독의 알림 예약 목록 (없으면 빈 리스트)
     */
    List<NotificationSchedule> findAllBySubscription(NotificationSubscription subscription);

    /**
     * 알림 예약 1건을 구독 정보, 구독자(User)까지 한 번에 조회합니다.
     *
     * 언제 쓰이나요?
     *   Redis Delay Queue Worker가 알림을 발송할 때 사용합니다.
     *   scheduleId로 "어디서 어디로 가는 막차인지(origin, destination)"와
     *   "누구에게 보낼지(subscription.endpoint, subscription.auth, subscription.p256dh)"를
     *   한 번의 쿼리로 모두 가져옵니다.
     *
     * ── JOIN FETCH가 왜 필요한가? (N+1 문제 방지) ──────────────────────────────
     *
     * NotificationSchedule은 subscription을, subscription은 user를 LAZY로 참조합니다.
     * JOIN FETCH 없이 일반 조회를 하면 아래처럼 쿼리가 3번 나갑니다.
     *
     *   ① SELECT * FROM notification_schedule WHERE schedule_id = ?  (예약 조회)
     *   ② SELECT * FROM notification_subscription WHERE subscription_id = ?  (구독 지연 로딩)
     *   ③ SELECT * FROM user WHERE user_id = ?  (사용자 지연 로딩)
     *
     * JOIN FETCH를 사용하면 쿼리 1번으로 세 테이블을 한꺼번에 가져옵니다.
     *
     *   ① SELECT s.*, sub.*, u.*
     *      FROM notification_schedule s
     *      JOIN notification_subscription sub ON s.subscription_id = sub.subscription_id
     *      JOIN user u ON sub.user_id = u.user_id
     *      WHERE s.schedule_id = ?
     *
     * Worker는 1초마다 실행되므로 불필요한 추가 쿼리를 최대한 줄여야 합니다.
     * ────────────────────────────────────────────────────────────────────────────
     *
     * @param scheduleId 조회할 알림 예약 ID
     * @return 구독·사용자까지 채워진 NotificationSchedule (없으면 Optional.empty())
     */
    @Query("""
            SELECT s FROM NotificationSchedule s
            JOIN FETCH s.subscription sub
            JOIN FETCH sub.user
            WHERE s.scheduleId = :scheduleId
            """)
    Optional<NotificationSchedule> findByIdWithSubscription(@Param("scheduleId") Long scheduleId);

    /**
     * 특정 사용자의 모든 알림 예약을 삭제합니다.
     *
     * 언제 쓰이나요?
     *   사용자 계정 삭제 시 해당 사용자의 알림 예약을 모두 삭제할 때 사용합니다.
     *   NotificationSchedule은 User를 직접 참조하지 않고 Subscription을 통해 참조하므로
     *   JOIN을 통해 조회합니다.
     *
     * @param userId 삭제할 사용자의 ID
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM NotificationSchedule s
            WHERE s.subscription.user.userId = :userId
            """)
    void deleteByUserUserId(@Param("userId") Long userId);
}

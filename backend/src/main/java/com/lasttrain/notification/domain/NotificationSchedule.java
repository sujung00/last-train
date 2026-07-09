package com.lasttrain.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 막차 알림 예약 정보를 저장하는 엔티티입니다.
 *
 * 사용자가 알림을 구독하면 이 테이블에 "언제, 어디서 어디로 가는 막차 알림을 보낼지"가 저장됩니다.
 * 예) "사용자A는 2026-06-10 23:11에 출발하는 강남구청→부천역 막차 알림을 원한다"
 *
 * 실제 알림 발송 시점 관리는 DB가 아닌 Redis Delay Queue(ZSET)가 담당합니다.
 *   - 구독 시: Redis에 "{scheduleId}:30", "{scheduleId}:10" 항목을 등록
 *   - 발송 시: Worker가 Redis에서 항목을 꺼내고 이 테이블에서 상세 정보를 조회
 *   - 취소 시: Redis에서 항목 제거 + 이 테이블에서 레코드 삭제
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 내부 사용 전용 기본 생성자 (직접 호출 금지)
@EntityListeners(AuditingEntityListener.class)      // createdAt 자동 주입을 위한 JPA Auditing 활성화
@Entity
@Table(name = "notification_schedule")
public class NotificationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB AUTO_INCREMENT
    @Column(name = "schedule_id")
    private Long scheduleId;

    // 어떤 구독에 대한 알림 예약인지 연결합니다.
    // FetchType.LAZY: 예약 정보를 조회할 때 구독 정보를 즉시 가져오지 않고,
    //                 실제로 접근할 때만 DB를 조회합니다. (N+1 방지용 JOIN FETCH와 함께 사용)
    // ON DELETE CASCADE는 DB DDL에서 처리합니다. (구독 삭제 시 예약도 자동 삭제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private NotificationSubscription subscription;

    // 출발지 이름입니다.
    // 예) "강남구청", "서울역"
    @Column(name = "origin", nullable = false, length = 100)
    private String origin;

    // 목적지 이름입니다.
    // 예) "부천역", "인천공항"
    @Column(name = "destination", nullable = false, length = 100)
    private String destination;

    // 막차 몇 분 전에 알림을 받을지 (10분, 20분, 30분 등)
    @Column(name = "notify_minutes_before", nullable = false)
    private Integer notifyMinutesBefore;

    /**
     * 막차 탑승 마감 시각입니다.
     *
     * ── 왜 TIME이 아닌 DATETIME인가? ────────────────────────────────────────────
     *
     * 자정 넘김(자정 이후 막차) 케이스 때문입니다.
     *
     * ODsay API는 자정 넘긴 막차 시각을 "24:10", "25:03" 형태로 반환합니다.
     * 예) 막차가 다음날 00:10 = ODsay 응답에서 "24:10"
     *
     * TIME 타입으로 저장하면 날짜 정보가 없어서 문제가 생깁니다.
     *   - 막차: 다음날 00:10 (= "24:10")
     *   - 30분 전 알림: 전날 23:40
     *   - TIME("00:10")에서 30분을 빼면 → 23:40, 하지만 날짜를 알 수 없어 틀린 시점에 발송
     *
     * DATETIME으로 저장하면 날짜까지 포함되어 정확하게 계산할 수 있습니다.
     *   - DATETIME("2026-06-11 00:10")에서 30분을 빼면 → 2026-06-10 23:40 (정확)
     *
     * ────────────────────────────────────────────────────────────────────────────
     */
    @Column(name = "last_board_time", nullable = false)
    private LocalDateTime lastBoardTime;

    // 예약이 저장된 시각을 자동으로 기록합니다.
    // @CreatedDate: 최초 INSERT 시점에 현재 시각을 자동 주입합니다. (@EnableJpaAuditing 필요)
    // updatable = false: 이후 UPDATE가 발생해도 이 컬럼은 변경되지 않습니다.
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // scheduleId와 createdAt은 DB/JPA가 자동 관리하므로 Builder에서 제외합니다.
    @Builder
    public NotificationSchedule(NotificationSubscription subscription,
                                 String origin, String destination,
                                 Integer notifyMinutesBefore,
                                 LocalDateTime lastBoardTime) {
        this.subscription          = subscription;
        this.origin                = origin;
        this.destination           = destination;
        this.notifyMinutesBefore   = notifyMinutesBefore;
        this.lastBoardTime         = lastBoardTime;
    }
}

package com.lasttrain.notification.domain;

import com.lasttrain.auth.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Web Push 알림 구독 정보를 저장하는 엔티티입니다.
 *
 * 사용자가 브라우저에서 알림 권한을 허용하면,
 * 브라우저가 Push 서비스(예: FCM)에 등록하고 아래 세 가지 값을 돌려줍니다.
 *   - endpoint : 이 브라우저로 메시지를 보낼 수 있는 고유 주소
 *   - auth     : 메시지 암호화에 사용하는 인증 비밀값
 *   - p256dh   : 메시지 암호화에 사용하는 공개키
 *
 * 서버는 이 세 가지 값을 저장해뒀다가, 알림을 보낼 때 꺼내서 사용합니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 내부 사용 전용 기본 생성자 (직접 호출 금지)
@EntityListeners(AuditingEntityListener.class)      // createdAt 자동 주입을 위한 JPA Auditing 활성화
@Entity
@Table(
        name = "notification_subscription",
        uniqueConstraints = {
                // 동일한 사용자가 동일한 브라우저(endpoint)로 중복 구독하는 것을 DB 레벨에서 방지합니다.
                // 같은 사람이 같은 기기에서 알림을 두 번 등록해도 하나만 저장됩니다.
                @UniqueConstraint(
                        name = "uq_subscription",
                        columnNames = {"user_id", "endpoint"}
                )
        }
)
public class NotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB AUTO_INCREMENT
    @Column(name = "subscription_id")
    private Long subscriptionId;

    // 어떤 사용자의 구독인지 연결합니다.
    // FetchType.LAZY: 구독 정보를 조회할 때 User를 즉시 가져오지 않고,
    //                 user.getEmail() 같이 실제로 접근할 때만 DB를 조회합니다. (성능 최적화)
    // ON DELETE CASCADE는 DB DDL에서 처리합니다. (user 삭제 시 구독 정보도 자동 삭제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 브라우저 Push 서비스가 제공하는 고유 주소입니다.
    // 알림을 보낼 때 이 주소로 HTTP 요청을 보냅니다.
    // 주소가 길 수 있어 VARCHAR(500)으로 넉넉하게 설정합니다.
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    // 알림 메시지를 암호화할 때 사용하는 인증 비밀값(128비트 랜덤 값, Base64 인코딩)입니다.
    @Column(name = "auth", nullable = false, length = 100)
    private String auth;

    // 알림 메시지를 암호화할 때 사용하는 공개키(P-256 타원곡선 공개키, Base64 인코딩)입니다.
    @Column(name = "p256dh", nullable = false, length = 200)
    private String p256dh;

    // 구독이 저장된 시각을 자동으로 기록합니다.
    // @CreatedDate: 최초 INSERT 시점에 현재 시각을 자동 주입합니다. (@EnableJpaAuditing 필요)
    // updatable = false: 이후 UPDATE가 발생해도 이 컬럼은 변경되지 않습니다.
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // subscriptionId와 createdAt은 DB/JPA가 자동 관리하므로 Builder에서 제외합니다.
    @Builder
    public NotificationSubscription(User user, String endpoint, String auth, String p256dh) {
        this.user     = user;
        this.endpoint = endpoint;
        this.auth     = auth;
        this.p256dh   = p256dh;
    }
}

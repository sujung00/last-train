package com.lasttrain.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_provider",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    // ENUM('EMAIL', 'KAKAO') — 이메일 가입은 "EMAIL", 카카오 가입은 "KAKAO"
    // 필드 초기값으로 "EMAIL"을 기본값으로 설정
    @Column(name = "provider", nullable = false,
            columnDefinition = "ENUM('EMAIL', 'KAKAO') DEFAULT 'EMAIL'")
    private String provider = "EMAIL";

    @Column(name = "provider_id", length = 100)
    private String providerId;

    // @CreatedDate: 최초 persist 시점에 자동으로 현재 시각 주입 (@EnableJpaAuditing 필요)
    // updatable = false: UPDATE 시 컬럼 변경 방지
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public User(String email, String password, String provider, String providerId) {
        this.email = email;
        this.password = password;
        this.provider = (provider != null) ? provider : "EMAIL";
        this.providerId = providerId;
    }
}

package com.lasttrain.favorite.domain;

import com.lasttrain.auth.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "favorite",
        indexes = {
                @Index(name = "idx_favorite_user", columnList = "user_id")
        }
)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Long favoriteId;

    // ON DELETE CASCADE는 DB 레벨에서 처리 → JPA cascade 미설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "emoji", length = 10)
    private String emoji;

    // DECIMAL(10,7): 소수점 7자리 = 약 1cm 오차 이내의 위경도 정밀도
    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "address", length = 200)
    private String address;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Favorite(User user, String name, String emoji,
                    BigDecimal lat, BigDecimal lng, String address) {
        this.user = user;
        this.name = name;
        this.emoji = emoji;
        this.lat = lat;
        this.lng = lng;
        this.address = address;
    }
}

package com.lasttrain.favorite.domain;

import com.lasttrain.auth.domain.User;
import com.lasttrain.favorite.dto.FavoriteResponse;
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

    /**
     * 즐겨찾기 정보를 수정합니다. (모든 필드)
     *
     * JPA는 트랜잭션이 끝날 때 엔티티의 변경 사항을 감지해서(Dirty Checking)
     * 자동으로 UPDATE 쿼리를 실행합니다. 그래서 이 메서드를 호출한 뒤
     * 별도로 save()를 호출하지 않아도 DB에 반영됩니다.
     *
     * @param name    새 즐겨찾기 이름
     * @param emoji   새 이모지 (없으면 null)
     * @param lat     새 위도
     * @param lng     새 경도
     * @param address 새 주소 (없으면 null)
     */
    public void update(String name, String emoji, Double lat, Double lng, String address) {
        this.name = name;
        this.emoji = emoji;
        // Double → BigDecimal 변환 (DB 컬럼 타입이 DECIMAL이라 변환 필요)
        this.lat = BigDecimal.valueOf(lat);
        this.lng = BigDecimal.valueOf(lng);
        this.address = address;
    }

    /**
     * 즐겨찾기의 이름과 이모지를 수정합니다. (PATCH 용도)
     *
     * @param name  새 즐겨찾기 이름
     * @param emoji 새 이모지 (없으면 null)
     */
    public void updatePartial(String name, String emoji) {
        this.name = name;
        this.emoji = emoji;
    }

    /**
     * 이 즐겨찾기 엔티티를 API 응답용 DTO(FavoriteResponse)로 변환합니다.
     *
     * 컨트롤러는 엔티티(Favorite)를 직접 반환하지 않고 DTO를 반환합니다.
     * 이유: 엔티티를 직접 반환하면 DB 구조가 그대로 외부에 노출되고,
     *       민감한 필드나 연관 객체(User 등)까지 직렬화될 수 있습니다.
     *
     * @return API 응답에 사용할 FavoriteResponse
     */
    public FavoriteResponse toResponse() {
        return new FavoriteResponse(
                this.favoriteId,
                this.name,
                this.emoji,
                this.lat.doubleValue(),  // BigDecimal → Double 변환 (응답 DTO 타입에 맞춤)
                this.lng.doubleValue(),
                this.address,
                this.createdAt
        );
    }
}

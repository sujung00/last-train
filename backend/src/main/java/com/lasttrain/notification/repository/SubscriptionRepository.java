package com.lasttrain.notification.repository;

import com.lasttrain.auth.domain.User;
import com.lasttrain.notification.domain.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 알림 구독 정보를 DB에서 조회/저장/삭제하는 Repository입니다.
 *
 * JpaRepository를 상속하면 save(), findById(), delete() 같은
 * 기본 CRUD 메서드를 자동으로 사용할 수 있습니다.
 * 아래 메서드들은 이름 규칙에 따라 Spring Data JPA가 SQL을 자동 생성합니다.
 */
public interface SubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    /**
     * 특정 사용자의 구독 정보 목록을 전부 가져옵니다.
     *
     * 언제 쓰이나요?
     *   사용자가 "내 알림 구독 목록"을 조회할 때 사용합니다.
     *   한 사용자가 여러 기기(PC 브라우저, 모바일 브라우저 등)에서
     *   구독할 수 있어 목록으로 반환합니다.
     *
     * @param user 조회할 사용자
     * @return 해당 사용자의 구독 목록 (없으면 빈 리스트)
     */
    List<NotificationSubscription> findAllByUser(User user);

    /**
     * 특정 사용자와 브라우저 endpoint가 모두 일치하는 구독을 찾습니다.
     *
     * 언제 쓰이나요?
     *   구독 등록 시 "이미 이 브라우저로 구독한 적이 있는지" 확인할 때 사용합니다.
     *   같은 사람이 같은 브라우저에서 다시 구독하면 새로 저장하지 않고
     *   기존 구독을 업데이트(upsert) 처리하기 위해 먼저 조회합니다.
     *
     *   endpoint는 브라우저마다 고유한 Push 서비스 주소입니다.
     *   사용자 + endpoint 조합이 같으면 "같은 기기의 같은 브라우저"로 봅니다.
     *
     * @param user     조회할 사용자
     * @param endpoint 브라우저 Push 서비스 고유 주소
     * @return 조건에 맞는 구독 (없으면 Optional.empty())
     */
    Optional<NotificationSubscription> findByUserAndEndpoint(User user, String endpoint);
}

package com.lasttrain.favorite.repository;

import com.lasttrain.auth.domain.User;
import com.lasttrain.favorite.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 즐겨찾기 데이터를 DB에서 조회/저장/삭제하는 Repository입니다.
 *
 * JpaRepository를 상속하면 save(), findById(), delete() 같은
 * 기본 CRUD 메서드를 자동으로 사용할 수 있습니다.
 * 아래 메서드들은 이름 규칙에 따라 Spring Data JPA가 SQL을 자동 생성합니다.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * 특정 사용자의 즐겨찾기 목록을 전부 가져옵니다.
     *
     * 언제 쓰이나요?
     *   로그인한 사용자가 "내 즐겨찾기 목록 보기"를 요청할 때 사용합니다.
     *   본인 데이터만 조회하므로 다른 사람의 즐겨찾기는 포함되지 않습니다.
     *
     * @param user 즐겨찾기를 조회할 사용자
     * @return 해당 사용자의 즐겨찾기 목록 (없으면 빈 리스트 반환)
     */
    List<Favorite> findAllByUser(User user);

    /**
     * 즐겨찾기 ID와 사용자를 동시에 확인해서 즐겨찾기를 가져옵니다.
     *
     * 언제 쓰이나요?
     *   즐겨찾기 수정/삭제 전에 "이 즐겨찾기가 정말 내 것인지" 확인할 때 사용합니다.
     *   favoriteId만으로 조회하면 다른 사람의 즐겨찾기도 수정할 수 있어서
     *   반드시 user 조건을 함께 걸어 본인 소유 여부를 검증합니다.
     *
     * @param favoriteId 조회할 즐겨찾기의 ID
     * @param user       소유자여야 할 사용자
     * @return 조건을 만족하는 즐겨찾기 (없거나 본인 것이 아니면 Optional.empty())
     */
    Optional<Favorite> findByFavoriteIdAndUser(Long favoriteId, User user);

    /**
     * 사용자가 같은 이름의 즐겨찾기를 이미 가지고 있는지 확인합니다.
     *
     * 언제 쓰이나요?
     *   즐겨찾기 등록/수정 시 같은 이름이 중복되지 않도록 막을 때 사용합니다.
     *   예) "집"이라는 즐겨찾기가 이미 있다면 또 "집"으로 등록하는 것을 거부할 수 있습니다.
     *
     * @param user 확인할 사용자
     * @param name 중복 여부를 확인할 즐겨찾기 이름
     * @return 이미 존재하면 true, 없으면 false
     */
    boolean existsByUserAndName(User user, String name);
}

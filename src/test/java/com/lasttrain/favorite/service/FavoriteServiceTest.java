package com.lasttrain.favorite.service;

import com.lasttrain.TestContainerConfig;
import com.lasttrain.auth.domain.User;
import com.lasttrain.auth.repository.UserRepository;
import com.lasttrain.favorite.dto.FavoriteRequest;
import com.lasttrain.favorite.dto.FavoriteResponse;
import com.lasttrain.favorite.repository.FavoriteRepository;
import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FavoriteService 통합 테스트
 *
 * ── 이 테스트가 하는 일 ────────────────────────────────────────────────────────
 * 즐겨찾기 목록 조회, 추가, 수정, 삭제가 실제 MySQL을 통해
 * 올바르게 동작하는지 검증합니다.
 *
 * ── 사용자를 직접 생성하는 이유 ─────────────────────────────────────────────────
 * 즐겨찾기 테스트는 인증(로그인, 토큰 발급) 흐름이 필요 없습니다.
 * FavoriteService는 userId(Long) 하나만 받으면 되므로
 * UserRepository로 User를 직접 저장해서 userId만 얻으면 충분합니다.
 *
 * ── @Transactional을 사용하는 이유 ─────────────────────────────────────────────
 * 각 테스트가 끝나면 INSERT/UPDATE/DELETE가 자동으로 롤백됩니다.
 * 덕분에 테스트 실행 순서나 앞선 테스트의 데이터에 영향받지 않습니다.
 * ────────────────────────────────────────────────────────────────────────────────
 */
@Transactional
@DisplayName("FavoriteService 테스트")
class FavoriteServiceTest extends TestContainerConfig {

    // Colima(macOS Docker 대안) 환경에서 Docker 소켓 경로를 직접 지정합니다.
    static {
        System.setProperty("docker.host",
                "unix:///Users/sujung/.colima/default/docker.sock");
        System.setProperty("DOCKER_HOST",
                "unix:///Users/sujung/.colima/default/docker.sock");
    }

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private UserRepository userRepository;

    // ── 공통 테스트 데이터 ─────────────────────────────────────────────────────────

    // 테스트에서 반복 사용하는 기본 즐겨찾기 값
    private static final double DEFAULT_LAT     = 37.5172;
    private static final double DEFAULT_LNG     = 127.0473;
    private static final String DEFAULT_EMOJI   = "🏠";
    private static final String DEFAULT_ADDRESS = "서울특별시 강남구";

    /**
     * 테스트용 사용자를 DB에 저장하고 반환합니다.
     * 즐겨찾기 테스트는 인증 흐름이 필요 없어 UserRepository로 직접 저장합니다.
     *
     * @param email 사용자 이메일 (테스트마다 다른 이메일 사용 — 유니크 제약 때문)
     */
    private User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password("test_password")
                .provider("EMAIL")
                .build());
    }

    /**
     * 기본값으로 채운 FavoriteRequest를 만들어 반환합니다.
     * 이름만 다르게 지정할 때 사용합니다.
     *
     * @param name 즐겨찾기 이름
     */
    private FavoriteRequest defaultRequest(String name) {
        return new FavoriteRequest(name, DEFAULT_EMOJI, DEFAULT_LAT, DEFAULT_LNG, DEFAULT_ADDRESS);
    }


    // ── 1번: 즐겨찾기 목록 조회 성공 - 본인 것만 반환 ───────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   "내 즐겨찾기 조회"인데 다른 사람의 즐겨찾기까지 섞여 나오면 안 됩니다.
    //   사용자 2명을 만들고 각각 즐겨찾기를 저장한 뒤,
    //   사용자1 조회 시 사용자1 것만 나오는지 확인합니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("즐겨찾기 목록 조회 성공 - 본인 것만 반환")
    void 즐겨찾기_목록_조회_성공_본인_것만_반환() {
        // given: 사용자 2명 생성. 사용자1에게 2개, 사용자2에게 1개 즐겨찾기를 추가합니다.
        User user1 = createUser("user1@example.com");
        User user2 = createUser("user2@example.com");

        favoriteService.add(defaultRequest("집"),    user1.getUserId());
        favoriteService.add(defaultRequest("회사"),  user1.getUserId());
        favoriteService.add(defaultRequest("헬스장"), user2.getUserId());

        // when: 사용자1의 즐겨찾기 목록을 조회합니다.
        List<FavoriteResponse> result = favoriteService.getList(user1.getUserId());

        // then: 사용자1 것 2개만 반환되어야 합니다.
        assertThat(result).hasSize(2);                                               // 반환 개수가 정확히 2개인지
        assertThat(result).extracting(FavoriteResponse::name)
                          .containsExactlyInAnyOrder("집", "회사");                  // 사용자1 이름들만 포함되는지 (사용자2의 "헬스장" 없어야 함)
    }


    // ── 2번: 즐겨찾기 없으면 빈 리스트 반환 ────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   즐겨찾기가 하나도 없는 신규 사용자에게 null 대신 빈 리스트가 반환되는지 확인합니다.
    //   null을 반환하면 프론트에서 .map() 같은 메서드 호출 시 에러가 납니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("즐겨찾기 없으면 빈 리스트 반환")
    void 즐겨찾기_없으면_빈_리스트_반환() {
        // given: 즐겨찾기를 하나도 추가하지 않은 사용자를 만듭니다.
        User user = createUser("empty@example.com");

        // when: 즐겨찾기 목록을 조회합니다.
        List<FavoriteResponse> result = favoriteService.getList(user.getUserId());

        // then: null이 아닌 빈 리스트가 반환되어야 합니다.
        assertThat(result).isNotNull();   // null이 아닌지 (null이면 프론트에서 NPE 발생)
        assertThat(result).isEmpty();     // 비어있는지
    }


    // ── 3번: 즐겨찾기 추가 성공 ─────────────────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   추가 후 반환된 값이 입력값과 일치하는지 확인합니다.
    //   또한 DB에 실제로 저장됐는지도 별도로 검증합니다.
    //   반환값만 맞다고 DB에 저장된 것은 아닐 수 있어서 두 가지를 모두 확인합니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("즐겨찾기 추가 성공")
    void 즐겨찾기_추가_성공() {
        // given: 사용자와 추가할 즐겨찾기 정보를 준비합니다.
        User user = createUser("add@example.com");
        FavoriteRequest request = new FavoriteRequest("회사", "🏢", 37.5665, 126.9780, "서울특별시 중구");

        // when: 즐겨찾기를 추가합니다.
        FavoriteResponse response = favoriteService.add(request, user.getUserId());

        // then: 반환된 값이 입력값과 일치하는지 확인합니다.
        assertThat(response).isNotNull();                                            // 응답 자체가 null이 아닌지
        assertThat(response.id()).isNotNull();                                        // DB가 자동 생성한 ID가 있는지
        assertThat(response.name()).isEqualTo("회사");                               // 이름이 일치하는지
        assertThat(response.lat()).isEqualTo(37.5665);                               // 위도가 일치하는지
        assertThat(response.lng()).isEqualTo(126.9780);                              // 경도가 일치하는지

        // DB에 실제로 저장됐는지 직접 조회해서 확인합니다.
        assertThat(favoriteRepository.findById(response.id())).isPresent();          // DB에서 조회 가능한지
    }


    // ── 4번: 즐겨찾기 수정 성공 ─────────────────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   수정 후 반환된 값이 새로 입력한 값으로 바뀌었는지 확인합니다.
    //   JPA Dirty Checking이 실제로 동작해 DB에 반영되는지도 검증합니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("즐겨찾기 수정 성공")
    void 즐겨찾기_수정_성공() {
        // given: 즐겨찾기를 먼저 추가합니다.
        User user = createUser("update@example.com");
        FavoriteResponse added = favoriteService.add(defaultRequest("집"), user.getUserId());

        FavoriteRequest updateRequest = new FavoriteRequest("헬스장", "💪", 37.4900, 127.0300, "서울특별시 서초구");

        // when: 추가한 즐겨찾기를 수정합니다.
        FavoriteResponse updated = favoriteService.update(added.id(), updateRequest, user.getUserId());

        // then: 반환된 값이 새 입력값으로 변경됐는지 확인합니다.
        assertThat(updated.name()).isEqualTo("헬스장");   // 이름이 수정됐는지
        assertThat(updated.lat()).isEqualTo(37.4900);     // 위도가 수정됐는지
        assertThat(updated.lng()).isEqualTo(127.0300);    // 경도가 수정됐는지
    }


    // ── 5번: 다른 사람 즐겨찾기 수정 시 예외 발생 ──────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   소유권 검증 없이 즐겨찾기 ID만으로 수정할 수 있다면
    //   다른 사람의 즐겨찾기를 몰래 바꿀 수 있는 보안 취약점이 됩니다.
    //   반드시 본인 것만 수정 가능해야 합니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("다른 사람 즐겨찾기 수정 시 예외 발생")
    void 다른_사람_즐겨찾기_수정_시_예외_발생() {
        // given: 사용자1의 즐겨찾기를 추가합니다.
        User user1 = createUser("owner@example.com");
        User user2 = createUser("attacker@example.com");

        FavoriteResponse added = favoriteService.add(defaultRequest("집"), user1.getUserId());

        // when & then: 사용자2가 사용자1의 즐겨찾기를 수정하려 하면 예외가 발생해야 합니다.
        assertThatThrownBy(() ->
                favoriteService.update(added.id(), defaultRequest("해킹"), user2.getUserId()))
                .isInstanceOf(AppException.class)                                    // AppException이 발생했는지
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FAVORITE_ACCESS_DENIED));              // 에러코드가 FAVORITE_ACCESS_DENIED(403)인지
    }


    // ── 6번: 즐겨찾기 삭제 성공 ─────────────────────────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   삭제 후 DB에서 실제로 제거됐는지 확인합니다.
    //   서비스가 성공 응답을 했더라도 DB에 데이터가 남아있으면 삭제 실패입니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("즐겨찾기 삭제 성공")
    void 즐겨찾기_삭제_성공() {
        // given: 즐겨찾기를 먼저 추가합니다.
        User user = createUser("delete@example.com");
        FavoriteResponse added = favoriteService.add(defaultRequest("집"), user.getUserId());

        // 삭제 전에 DB에 존재하는지 사전 확인합니다.
        assertThat(favoriteRepository.findById(added.id())).isPresent();            // 삭제 전: DB에 있어야 함

        // when: 즐겨찾기를 삭제합니다.
        favoriteService.delete(added.id(), user.getUserId());

        // then: DB에서 실제로 삭제됐는지 확인합니다.
        assertThat(favoriteRepository.findById(added.id())).isEmpty();              // 삭제 후: DB에 없어야 함
    }


    // ── 7번: 다른 사람 즐겨찾기 삭제 시 예외 발생 ──────────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   수정과 마찬가지로, 다른 사람의 즐겨찾기를 삭제하는 것도
    //   반드시 차단해야 합니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("다른 사람 즐겨찾기 삭제 시 예외 발생")
    void 다른_사람_즐겨찾기_삭제_시_예외_발생() {
        // given: 사용자1의 즐겨찾기를 추가합니다.
        User user1 = createUser("owner2@example.com");
        User user2 = createUser("attacker2@example.com");

        FavoriteResponse added = favoriteService.add(defaultRequest("집"), user1.getUserId());

        // when & then: 사용자2가 사용자1의 즐겨찾기를 삭제하려 하면 예외가 발생해야 합니다.
        assertThatThrownBy(() ->
                favoriteService.delete(added.id(), user2.getUserId()))
                .isInstanceOf(AppException.class)                                    // AppException이 발생했는지
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FAVORITE_ACCESS_DENIED));              // 에러코드가 FAVORITE_ACCESS_DENIED(403)인지
    }


    // ── 8번: 존재하지 않는 즐겨찾기 수정 시 예외 발생 ──────────────────────────────
    //
    // 이 테스트가 필요한 이유:
    //   DB에 없는 즐겨찾기 ID로 수정을 시도하면
    //   FAVORITE_NOT_FOUND(404) 예외를 반환해야 합니다.
    //   처리하지 않으면 NullPointerException이 발생하거나 의도치 않은 동작이 생깁니다.
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 즐겨찾기 수정 시 예외 발생")
    void 존재하지_않는_즐겨찾기_수정_시_예외_발생() {
        // given: 사용자를 만들고 실제로 존재하지 않는 ID를 준비합니다.
        User user = createUser("notfound@example.com");
        Long nonExistentId = 999_999L;  // DB에 절대 존재하지 않는 ID

        // when & then: 없는 ID로 수정 시도 시 FAVORITE_NOT_FOUND 예외가 발생해야 합니다.
        assertThatThrownBy(() ->
                favoriteService.update(nonExistentId, defaultRequest("집"), user.getUserId()))
                .isInstanceOf(AppException.class)                                    // AppException이 발생했는지
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND));                  // 에러코드가 FAVORITE_NOT_FOUND(404)인지
    }
}

package com.lasttrain.favorite.service;

import com.lasttrain.auth.domain.User;
import com.lasttrain.auth.repository.UserRepository;
import com.lasttrain.favorite.domain.Favorite;
import com.lasttrain.favorite.dto.FavoriteRequest;
import com.lasttrain.favorite.dto.FavoriteResponse;
import com.lasttrain.favorite.dto.FavoriteUpdateRequest;
import com.lasttrain.favorite.repository.FavoriteRepository;
import com.lasttrain.global.exception.AppException;
import com.lasttrain.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션 사용 (조회 성능 최적화)
public class FavoriteService {

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;

    /**
     * 로그인한 사용자의 즐겨찾기 목록을 반환합니다.
     *
     * @param userId JWT에서 추출한 현재 로그인 사용자의 ID
     * @return 해당 사용자의 즐겨찾기 목록 (없으면 빈 리스트)
     */
    public List<FavoriteResponse> getList(Long userId) {
        User user = findUser(userId);

        // 이 사용자의 즐겨찾기를 전부 조회한 뒤, 각각을 응답 DTO로 변환합니다.
        return favoriteRepository.findAllByUser(user).stream()
                .map(Favorite::toResponse)
                .toList();
    }

    /**
     * 즐겨찾기를 새로 등록하고 저장된 결과를 반환합니다.
     *
     * @param request 등록할 즐겨찾기 정보 (이름, 위경도 등)
     * @param userId  JWT에서 추출한 현재 로그인 사용자의 ID
     * @return 저장된 즐겨찾기 정보
     */
    @Transactional // 쓰기 작업이라 별도 트랜잭션 선언 (readOnly = false)
    public FavoriteResponse add(FavoriteRequest request, Long userId) {
        User user = findUser(userId);

        Favorite favorite = Favorite.builder()
                .user(user)
                .name(request.name())
                .emoji(request.emoji())
                // FavoriteRequest는 Double 타입이지만, DB 컬럼은 DECIMAL(10,7)이라 변환이 필요합니다.
                .lat(BigDecimal.valueOf(request.lat()))
                .lng(BigDecimal.valueOf(request.lng()))
                .address(request.address())
                .build();

        // save()는 INSERT 쿼리를 실행하고 DB가 자동 생성한 ID 등이 채워진 엔티티를 반환합니다.
        return favoriteRepository.save(favorite).toResponse();
    }

    /**
     * 즐겨찾기를 수정하고 수정된 결과를 반환합니다. (모든 필드)
     *
     * 수정 전에 반드시 "이 즐겨찾기가 요청한 사람의 것인지" 확인합니다.
     * 확인하지 않으면 다른 사람의 즐겨찾기를 임의로 수정하는 것이 가능해집니다.
     *
     * @param favoriteId 수정할 즐겨찾기의 ID
     * @param request    수정할 내용 (이름, 위경도 등)
     * @param userId     JWT에서 추출한 현재 로그인 사용자의 ID
     * @return 수정된 즐겨찾기 정보
     */
    @Transactional
    public FavoriteResponse update(Long favoriteId, FavoriteRequest request, Long userId) {
        Favorite favorite = findFavoriteWithOwnerCheck(favoriteId, userId);

        // JPA Dirty Checking: 트랜잭션 안에서 엔티티 값을 바꾸면
        // 트랜잭션이 끝날 때 변경 사항을 감지해서 UPDATE 쿼리를 자동 실행합니다.
        // 그래서 여기서는 save()를 호출하지 않아도 됩니다.
        favorite.update(request.name(), request.emoji(), request.lat(), request.lng(), request.address());

        return favorite.toResponse();
    }

    /**
     * 즐겨찾기의 이름과 이모지를 부분 수정하고 결과를 반환합니다. (PATCH)
     *
     * 수정 전에 반드시 "이 즐겨찾기가 요청한 사람의 것인지" 확인합니다.
     *
     * @param favoriteId 수정할 즐겨찾기의 ID
     * @param request    수정할 내용 (이름, 이모지만)
     * @param userId     JWT에서 추출한 현재 로그인 사용자의 ID
     * @return 수정된 즐겨찾기 정보
     */
    @Transactional
    public FavoriteResponse updatePartial(Long favoriteId, FavoriteUpdateRequest request, Long userId) {
        Favorite favorite = findFavoriteWithOwnerCheck(favoriteId, userId);
        favorite.updatePartial(request.name(), request.emoji());
        return favorite.toResponse();
    }

    /**
     * 즐겨찾기를 삭제합니다.
     *
     * 삭제 전에 반드시 "이 즐겨찾기가 요청한 사람의 것인지" 확인합니다.
     *
     * @param favoriteId 삭제할 즐겨찾기의 ID
     * @param userId     JWT에서 추출한 현재 로그인 사용자의 ID
     */
    @Transactional
    public void delete(Long favoriteId, Long userId) {
        Favorite favorite = findFavoriteWithOwnerCheck(favoriteId, userId);
        favoriteRepository.delete(favorite);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * userId로 User를 조회합니다. 없으면 예외를 던집니다.
     *
     * JWT 인증을 통과한 사용자라면 반드시 DB에 존재해야 합니다.
     * 없다면 비정상적인 상황(탈퇴 후 잔여 토큰 사용 등)이므로 예외 처리합니다.
     */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
    }

    /**
     * 즐겨찾기를 조회하고 요청한 사람의 것인지 확인합니다.
     *
     * 두 단계로 확인합니다:
     *   1단계: 해당 ID의 즐겨찾기가 존재하는지 → 없으면 FAVORITE_NOT_FOUND
     *   2단계: 그 즐겨찾기의 주인이 요청자인지 → 아니면 FAVORITE_ACCESS_DENIED
     */
    private Favorite findFavoriteWithOwnerCheck(Long favoriteId, Long userId) {
        Favorite favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new AppException(ErrorCode.FAVORITE_NOT_FOUND));

        // Hibernate는 Lazy 프록시에서 ID 접근 시 추가 DB 조회 없이 처리합니다.
        if (!favorite.getUser().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FAVORITE_ACCESS_DENIED);
        }

        return favorite;
    }
}

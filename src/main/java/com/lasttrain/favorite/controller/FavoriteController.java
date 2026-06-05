package com.lasttrain.favorite.controller;

import com.lasttrain.favorite.dto.FavoriteRequest;
import com.lasttrain.favorite.dto.FavoriteResponse;
import com.lasttrain.favorite.service.FavoriteService;
import com.lasttrain.global.response.ApiResponse;
import com.lasttrain.global.security.SecurityUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Favorite", description = "즐겨찾기 CRUD")
@SecurityRequirement(name = "BearerAuth")
@RequestMapping("/api/v1/favorites")
@RestController
@RequiredArgsConstructor // final 필드를 생성자로 자동 주입
public class FavoriteController {

    // Spring이 FavoriteService 빈을 자동으로 찾아서 주입해 줍니다.
    private final FavoriteService favoriteService;

    @Operation(summary = "즐겨찾기 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<FavoriteResponse>> getFavorites(
            // JWT 필터가 Access Token을 검증하고 SecurityContext에 저장한 사용자 정보를 꺼냅니다.
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        return ApiResponse.ok(favoriteService.getList(userId));
    }

    @Operation(summary = "즐겨찾기 등록")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<FavoriteResponse> createFavorite(
            @Valid @RequestBody FavoriteRequest request,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        return ApiResponse.ok(favoriteService.add(request, userId));
    }

    @Operation(summary = "즐겨찾기 수정")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 즐겨찾기만 수정 가능"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "즐겨찾기 없음")
    })
    @PutMapping("/{id}")
    public ApiResponse<FavoriteResponse> updateFavorite(
            @Parameter(description = "즐겨찾기 ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody FavoriteRequest request,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        // FavoriteService 내부에서 즐겨찾기가 본인 것인지 확인합니다.
        // 다른 사람의 즐겨찾기를 수정하려 하면 FAVORITE_ACCESS_DENIED(403) 예외가 발생합니다.
        return ApiResponse.ok(favoriteService.update(id, request, userId));
    }

    @Operation(summary = "즐겨찾기 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 즐겨찾기만 삭제 가능"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "즐겨찾기 없음")
    })
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFavorite(
            @Parameter(description = "즐겨찾기 ID", example = "1")
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        favoriteService.delete(id, userId);
        return ApiResponse.ok();
    }
}
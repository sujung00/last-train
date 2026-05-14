package com.lasttrain.favorite.controller;

import com.lasttrain.favorite.dto.FavoriteRequest;
import com.lasttrain.favorite.dto.FavoriteResponse;
import com.lasttrain.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Favorite", description = "즐겨찾기 CRUD")
@SecurityRequirement(name = "BearerAuth")
@RequestMapping("/api/v1/favorites")
@RestController
public class FavoriteController {

    @Operation(summary = "즐겨찾기 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<FavoriteResponse>> getFavorites() {
        // TODO: favoriteService.getList(userId from SecurityContext)
        return ApiResponse.ok(List.of());
    }

    @Operation(summary = "즐겨찾기 등록")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<FavoriteResponse> createFavorite(@Valid @RequestBody FavoriteRequest request) {
        // TODO: favoriteService.create(userId, request)
        return ApiResponse.ok(null);
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
            @Valid @RequestBody FavoriteRequest request
    ) {
        // TODO: favoriteService.update(id, userId, request)
        return ApiResponse.ok(null);
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
            @PathVariable Long id
    ) {
        // TODO: favoriteService.delete(id, userId)
        return ApiResponse.ok();
    }
}
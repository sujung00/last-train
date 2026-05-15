package com.lasttrain.auth.controller;

import com.lasttrain.auth.dto.KakaoLoginRequest;
import com.lasttrain.auth.dto.LoginRequest;
import com.lasttrain.auth.dto.SignupRequest;
import com.lasttrain.auth.dto.TokenResponse;
import com.lasttrain.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 관리")
@RequestMapping("/api/v1/auth")
@RestController
public class AuthController {

    @Operation(summary = "이메일 회원가입")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이메일 중복")
    })

    @SecurityRequirements({})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        // TODO: authService.signup(request)
        return ApiResponse.ok();
    }

    @Operation(summary = "로그인")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일/비밀번호 불일치")
    })
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        // TODO: authService.login(request)
        return ApiResponse.ok(null);
    }

    @Operation(summary = "카카오 소셜 로그인",
            description = "프론트에서 카카오 인가 코드를 받아 백엔드로 전달. 백엔드에서 카카오 토큰 교환 후 JWT 발급.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인/회원가입 성공")
    })
    @PostMapping("/kakao")
    public ApiResponse<TokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        // TODO: authService.kakaoLogin(request.code())
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Access Token 재발급",
            description = "Authorization 헤더에 Refresh Token을 담아 요청. 새 AT + RT 반환.")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "RT 만료 또는 불일치")
    })
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue() {
        // TODO: authService.reissue(userId from SecurityContext)
        return ApiResponse.ok(null);
    }

    @Operation(summary = "로그아웃", description = "Redis의 Refresh Token 삭제. 프론트는 로컬 AT/RT 폐기.")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // TODO: authService.logout(userId from SecurityContext)
        return ApiResponse.ok();
    }
}
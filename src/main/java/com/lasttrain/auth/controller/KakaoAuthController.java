package com.lasttrain.auth.controller;

import com.lasttrain.auth.dto.TokenResponse;
import com.lasttrain.auth.service.KakaoAuthService;
import com.lasttrain.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 관리")
@RequestMapping("/api/v1/auth")
@RestController
@RequiredArgsConstructor
public class KakaoAuthController {

    private final KakaoAuthService kakaoAuthService;

    @Operation(summary = "카카오 로그인 콜백",
            description = "카카오 로그인 동의 화면에서 사용자가 동의하면, 카카오가 이 주소로 redirect하면서 " +
                    "인가 코드(code)를 쿼리 파라미터로 함께 보내줍니다. " +
                    "백엔드는 이 code로 카카오 액세스 토큰을 발급받고, 사용자 정보를 조회해 로그인(또는 최초 1회 회원가입)을 처리합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인/회원가입 성공")
    })
    // 로그인 전이라 아직 JWT가 없는 상태이므로 인증 없이 호출 가능해야 합니다.
    @SecurityRequirements({})
    @GetMapping("/kakao/callback")
    public ApiResponse<TokenResponse> kakaoCallback(
            @Parameter(description = "카카오가 전달한 인가 코드", example = "ABC123xyz")
            @RequestParam String code) {

        // 인가 코드 → 카카오 액세스 토큰 교환 → 사용자 정보 조회 → 로그인/가입 → JWT 발급
        return ApiResponse.ok(kakaoAuthService.kakaoLogin(code));
    }
}

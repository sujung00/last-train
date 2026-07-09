package com.lasttrain.auth.controller;

import com.lasttrain.auth.dto.LoginRequest;
import com.lasttrain.auth.dto.SignupRequest;
import com.lasttrain.auth.dto.TokenResponse;
import com.lasttrain.auth.service.AuthService;
import com.lasttrain.global.response.ApiResponse;
import com.lasttrain.global.security.SecurityUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 관리")
@RequestMapping("/api/v1/auth")
@RestController
@RequiredArgsConstructor // final 필드를 생성자로 자동 주입 (= @Autowired 대신 권장 방식)
public class AuthController {

    // Spring이 AuthService 빈을 자동으로 찾아서 주입해 줍니다.
    private final AuthService authService;

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
        // 이메일 중복 확인 → 비밀번호 BCrypt 암호화 → DB 저장
        authService.signup(request);
        return ApiResponse.ok();
    }

    @Operation(summary = "로그인")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일/비밀번호 불일치")
    })
    @SecurityRequirements({})
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        // 이메일/비밀번호 검증 후 Access Token + Refresh Token 발급
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "Access Token 재발급",
            description = "Authorization 헤더에 Refresh Token을 담아 요청. 새 AT + RT 반환.")
    @SecurityRequirements({})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "RT 만료 또는 불일치")
    })
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(
            // reissue 엔드포인트는 SecurityConfig에서 permitAll() 처리되어 있어
            // JWT 필터가 인증을 건너뜁니다. 따라서 @AuthenticationPrincipal로는 userId를 꺼낼 수 없고,
            // 클라이언트가 Authorization 헤더에 직접 Refresh Token을 담아 보냅니다.
            @RequestHeader("Authorization") String bearerToken) {

        // "Bearer eyJhbGciOiJ..." 에서 앞 7글자("Bearer ")를 잘라내 순수 토큰만 추출합니다.
        String refreshToken = bearerToken.substring("Bearer ".length());

        // RT를 Redis에 저장된 값과 대조해 유효성 검증 후 새 AT + RT 발급
        return ApiResponse.ok(authService.reissue(refreshToken));
    }

    @Operation(summary = "로그아웃", description = "Redis의 Refresh Token 삭제. 프론트는 로컬 AT/RT 폐기.")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            // JWT 필터가 Access Token을 검증한 뒤 SecurityContext에 사용자 정보를 저장합니다.
            // @AuthenticationPrincipal이 그 정보를 꺼내서 userDetails로 전달해 줍니다.
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        // Redis에서 "RT:{userId}" 키를 삭제해 Refresh Token을 무효화합니다.
        authService.logout(userDetails.getUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "계정 삭제", description = "사용자 계정을 완전히 삭제합니다. (연쇄 삭제: 알림, 즐겨찾기 등)")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "계정 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping("/withdraw")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        // 사용자 계정 및 관련 데이터 삭제
        authService.withdraw(userDetails.getUserId());
        return ApiResponse.ok();
    }
}
package com.lasttrain.notification.controller;

import com.lasttrain.global.response.ApiResponse;
import com.lasttrain.global.security.SecurityUserDetails;
import com.lasttrain.notification.dto.SubscribeRequest;
import com.lasttrain.notification.service.NotificationService;
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

@Tag(name = "Notification", description = "웹 푸시 구독 관리")
@SecurityRequirement(name = "BearerAuth") // 모든 엔드포인트에 JWT 인증 필요
@RequestMapping("/api/v1/notifications")
@RestController
@RequiredArgsConstructor // final 필드를 생성자로 자동 주입
public class NotificationController {

    // Spring이 NotificationService 빈을 자동으로 찾아서 주입해 줍니다.
    private final NotificationService notificationService;

    @Operation(
            summary = "알림 구독",
            description = """
                    브라우저에서 Push 알림 권한 허용 후 받은 구독 정보를 저장합니다.

                    - 같은 브라우저(endpoint)로 이미 구독한 경우 기존 예약을 교체합니다. (upsert)
                    - lastBoardTime: 자정 넘김 막차도 포함 (예: 2026-06-10T00:10:00)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "구독 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/subscribe")
    public ApiResponse<Void> subscribe(
            @Valid @RequestBody SubscribeRequest request,
            // JWT 필터가 Access Token을 검증하고 SecurityContext에 저장한 사용자 정보를 꺼냅니다.
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        notificationService.subscribe(request, userId);
        return ApiResponse.ok();
    }

    @Operation(
            summary = "알림 구독 취소",
            description = """
                    등록된 알림 구독을 취소합니다.

                    - Redis Delay Queue에서 예약된 알림을 제거합니다.
                    - 본인의 구독만 취소할 수 있습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "구독 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 구독만 취소 가능"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 정보 없음")
    })
    @DeleteMapping("/{subscriptionId}")
    public ApiResponse<Void> unsubscribe(
            @Parameter(description = "취소할 구독 ID", example = "1")
            @PathVariable Long subscriptionId,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        // NotificationService 내부에서 본인 구독인지 확인합니다.
        // 다른 사람의 구독을 취소하려 하면 403 예외가 발생합니다.
        notificationService.unsubscribe(subscriptionId, userId);
        return ApiResponse.ok();
    }
}

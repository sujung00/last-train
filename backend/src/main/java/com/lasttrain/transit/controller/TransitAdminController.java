package com.lasttrain.transit.controller;

import com.lasttrain.global.response.ApiResponse;
import com.lasttrain.transit.service.TransitRefreshScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 막차 캐시 관리 API 컨트롤러
 *
 * 역할:
 *   - 관리자가 막차 시각 캐시를 수동으로 갱신할 수 있는 엔드포인트 제공
 *   - 지하철: 수동 트리거로 모든 역 갱신
 *   - 버스: 별도의 스케줄러가 매일 새벽 3시 자동 실행
 *
 * 경로: /admin/transit/...
 * 인증: 관리자 권한 필요 (향후 @PreAuthorize 추가 권장)
 *
 * 사용 예시:
 *   curl -X POST http://localhost:8080/admin/transit/refresh/subway
 *   → 지하철 모든 역의 막차 시각 갱신 시작
 */
@RestController
@RequestMapping("/admin/transit")
@RequiredArgsConstructor
@Slf4j
public class TransitAdminController {

    private final TransitRefreshScheduler transitRefreshScheduler;

    /**
     * 지하철 막차 시각 캐시를 갱신합니다.
     *
     * 요청:
     *   POST /admin/transit/refresh/subway
     *
     * 응답:
     *   {
     *     "success": true,
     *     "data": "지하철 막차 시각 갱신을 시작합니다.",
     *     "error": null,
     *     "timestamp": "2026-07-01T10:30:00"
     *   }
     *
     * 동작:
     *   1. TransitRefreshScheduler.refreshSubway() 호출
     *   2. 모든 전철역에 대해 ODsay API 호출
     *   3. 최신 막차 시각을 DB에 저장
     *   4. 백그라운드에서 비동기 실행 (응답 시간 단축)
     *
     * 예상 소요 시간:
     *   - 역 개수: ~400개
     *   - dayType: 3가지 (평일/토/일)
     *   - 예상 시간: 수 분 ~ 십 분
     *
     * 권장사항:
     *   - 트래픽이 적은 새벽시간에 실행
     *   - 향후 @Scheduled로 자동화 고려
     *   - 권한 관리: @PreAuthorize("hasRole('ADMIN')") 추가 필요
     *
     * @return 갱신 시작 메시지
     */
    @PostMapping("/refresh/subway")
    public ApiResponse<String> refreshSubway() {
        log.info("[TransitAdminController] 지하철 막차 시각 갱신 요청 수신");

        try {
            // TransitRefreshScheduler에게 갱신 작업 위임
            // (실제 실행은 별도 스레드 또는 비동기 처리 권장)
            transitRefreshScheduler.refreshSubway();

            log.info("[TransitAdminController] 지하철 막차 시각 갱신 시작");
            return ApiResponse.ok("지하철 막차 시각 갱신을 시작합니다.");

        } catch (Exception e) {
            log.error("[TransitAdminController] 지하철 갱신 요청 중 오류 발생", e);
            return ApiResponse.ok("지하철 갱신 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}

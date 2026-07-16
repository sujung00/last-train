package com.lasttrain.transit.controller;

import com.lasttrain.global.response.ApiResponse;
import com.lasttrain.transit.service.BatchMetrics;
import com.lasttrain.transit.service.TransitCacheService;
import com.lasttrain.transit.service.TransitRefreshScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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
 *   - 성과 메트릭 조회 및 리셋 기능
 *
 * 경로: /admin/transit/...
 * 인증: 관리자 권한 필요 (향후 @PreAuthorize 추가 권장)
 *
 * 사용 예시:
 *   curl -X POST http://localhost:8080/admin/transit/refresh/subway
 *   → 지하철 모든 역의 막차 시각 갱신 시작
 *
 *   curl -X GET http://localhost:8080/admin/transit/metrics
 *   → 성과 메트릭 조회
 */
@RestController
@RequestMapping("/admin/transit")
@RequiredArgsConstructor
@Slf4j
public class TransitAdminController {

    private final TransitRefreshScheduler transitRefreshScheduler;
    private final BatchMetrics batchMetrics;

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

    /**
     * 막차 조회 성과 메트릭을 조회합니다.
     *
     * 요청:
     *   GET /admin/transit/metrics
     *
     * 응답 예시:
     *   {
     *     "success": true,
     *     "data": "[API 성공] 1234회 / [Fallback 발생] 56회 / [Fallback 히트] 45회 / [Fallback 미스] 11회\n
     *             API 성공률: 95.67%\n
     *             외부 API 평균 응답 시간: 245.32ms\n
     *             DB Fallback 평균 응답 시간: 12.45ms",
     *     "error": null,
     *     "timestamp": "2026-07-01T10:30:00"
     *   }
     *
     * 메트릭 설명:
     *   - [API 성공]: 외부 API 호출 성공 건수
     *   - [Fallback 발생]: 외부 API 호출 실패로 Fallback 발생 건수
     *   - [Fallback 히트]: Fallback에서 DB 데이터 발견한 건수
     *   - [Fallback 미스]: Fallback에서 DB 데이터 없는 건수
     *   - API 성공률: (API 성공 / 전체) × 100%
     *   - 외부 API 평균 응답 시간: 모든 API 호출의 평균 시간
     *   - DB Fallback 평균 응답 시간: 모든 Fallback 조회의 평균 시간
     *
     * @return 성과 메트릭 정보
     */
    @GetMapping("/metrics")
    public ApiResponse<String> getMetrics() {
        log.info("[TransitAdminController] 성과 메트릭 조회 요청");

        try {
            String metrics = TransitCacheService.getMetrics();
            log.info("[TransitAdminController] 성과 메트릭 조회 완료:\n{}", metrics);
            return ApiResponse.ok(metrics);

        } catch (Exception e) {
            log.error("[TransitAdminController] 메트릭 조회 중 오류 발생", e);
            return ApiResponse.ok("메트릭 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 막차 조회 성과 메트릭을 리셋합니다.
     *
     * 요청:
     *   POST /admin/transit/metrics/reset
     *
     * 응답:
     *   {
     *     "success": true,
     *     "data": "성과 메트릭이 리셋되었습니다.",
     *     "error": null,
     *     "timestamp": "2026-07-01T10:30:00"
     *   }
     *
     * 용도:
     *   - 새로운 측정 기간 시작 시 카운터 초기화
     *   - 테스트 환경에서 깨끗한 상태 만들기
     *
     * @return 리셋 완료 메시지
     */
    @PostMapping("/metrics/reset")
    public ApiResponse<String> resetMetrics() {
        log.info("[TransitAdminController] 성과 메트릭 리셋 요청");

        try {
            TransitCacheService.resetMetrics();
            log.info("[TransitAdminController] 성과 메트릭 리셋 완료");
            return ApiResponse.ok("성과 메트릭이 리셋되었습니다.");

        } catch (Exception e) {
            log.error("[TransitAdminController] 메트릭 리셋 중 오류 발생", e);
            return ApiResponse.ok("메트릭 리셋 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 배치 처리 성능 메트릭을 조회합니다.
     *
     * 요청:
     *   GET /admin/transit/metrics/batch
     *
     * 응답:
     *   {
     *     "success": true,
     *     "data": "========== 배치 처리 성능 메트릭 ==========\n
     *              [DB 조회 통계]\n
     *              총 조회: 1234회\n
     *              히트: 1220회 ✅\n
     *              미스: 14회 ⚠️\n
     *              히트율: 98.87%\n
     *              평균 쿼리 시간: 4.56ms\n
     *              ...",
     *     "error": null,
     *     "timestamp": "2026-07-01T10:30:00"
     *   }
     *
     * 메트릭 설명:
     *   - DB 히트: 배치 데이터에서 조회 성공 (목표: >99%)
     *   - DB 미스: 배치 데이터 없어서 API Fallback (목표: <1%)
     *   - 평균 DB 쿼리 시간: 매우 빠름 (목표: <10ms)
     *   - API Fallback: 매우 적어야 함 (새로운 노선만 발생)
     *
     * @return 배치 성능 메트릭
     */
    @GetMapping("/metrics/batch")
    public ApiResponse<String> getBatchMetrics() {
        log.info("[TransitAdminController] 배치 메트릭 조회 요청");

        try {
            String metrics = batchMetrics.getMetricsReport();
            log.debug("[TransitAdminController] 배치 메트릭:\n{}", metrics);
            return ApiResponse.ok(metrics);

        } catch (Exception e) {
            log.error("[TransitAdminController] 배치 메트릭 조회 중 오류 발생", e);
            return ApiResponse.ok("배치 메트릭 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 배치 처리 메트릭을 초기화합니다.
     *
     * 요청:
     *   POST /admin/transit/metrics/batch/reset
     *
     * 응답:
     *   {
     *     "success": true,
     *     "data": "배치 메트릭이 초기화되었습니다.",
     *     "error": null,
     *     "timestamp": "2026-07-01T10:30:00"
     *   }
     *
     * 용도:
     *   - 일일 모니터링 시작 시 초기화
     *   - 새로운 배치 개선 측정 시작
     *
     * @return 초기화 완료 메시지
     */
    @PostMapping("/metrics/batch/reset")
    public ApiResponse<String> resetBatchMetrics() {
        log.info("[TransitAdminController] 배치 메트릭 초기화 요청");

        try {
            batchMetrics.reset();
            log.info("[TransitAdminController] 배치 메트릭 초기화 완료");
            return ApiResponse.ok("배치 메트릭이 초기화되었습니다.");

        } catch (Exception e) {
            log.error("[TransitAdminController] 배치 메트릭 초기화 중 오류 발생", e);
            return ApiResponse.ok("배치 메트릭 초기화 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}

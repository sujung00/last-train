package com.lasttrain.transit.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 배치 처리 성능 메트릭 추적
 *
 * 역할:
 *   - DB 조회 히트/미스 통계
 *   - API Fallback 호출 통계
 *   - 응답 시간 측정
 *   - 성과 리포트 생성
 *
 * 메트릭:
 *   - DB 히트율: 높을수록 좋음 (목표 >99%)
 *   - 평균 DB 쿼리 시간: 낮을수록 좋음 (목표 <10ms)
 *   - API Fallback 호출: 낮을수록 좋음 (목표 <1%)
 *   - 평균 API 호출 시간: 참고용 (배치에서만 호출되므로 영향 없음)
 */
@Component
@Slf4j
@Getter
public class BatchMetrics {

    // ── DB 조회 통계 ──────────────────────────────────────────────────────────────
    // DB 조회 성공 (배치 데이터 히트)
    private final AtomicInteger dbHitCount = new AtomicInteger(0);

    // DB 조회 실패 (배치 데이터 미스, API Fallback 필요)
    private final AtomicInteger dbMissCount = new AtomicInteger(0);

    // DB 조회 누적 시간 (밀리초)
    private final AtomicLong dbQueryTime = new AtomicLong(0);

    // ── API Fallback 통계 ─────────────────────────────────────────────────────────
    // API Fallback 호출 횟수 (매우 드물어야 함)
    private final AtomicInteger apiFallbackCount = new AtomicInteger(0);

    // API Fallback 누적 시간 (밀리초)
    private final AtomicLong apiFallbackTime = new AtomicLong(0);

    // ── 배치 갱신 통계 ─────────────────────────────────────────────────────────────
    // 마지막 배치 갱신 시간
    private LocalDateTime lastBatchTime = null;

    // 마지막 배치 갱신 소요 시간 (밀리초)
    private long lastBatchDuration = 0;

    // 마지막 배치 갱신 성공 횟수
    private int lastBatchSuccessCount = 0;

    // 마지막 배치 갱신 실패 횟수
    private int lastBatchFailureCount = 0;

    /**
     * DB 히트 기록
     *
     * @param queryTimeMs DB 쿼리 소요 시간 (밀리초)
     */
    public void recordDbHit(long queryTimeMs) {
        dbHitCount.incrementAndGet();
        dbQueryTime.addAndGet(queryTimeMs);
    }

    /**
     * DB 미스 기록 (API Fallback 필요)
     *
     * @param queryTimeMs DB 쿼리 소요 시간 (밀리초)
     */
    public void recordDbMiss(long queryTimeMs) {
        dbMissCount.incrementAndGet();
        dbQueryTime.addAndGet(queryTimeMs);
    }

    /**
     * API Fallback 호출 기록
     *
     * @param callTimeMs API 호출 소요 시간 (밀리초)
     */
    public void recordApiFallback(long callTimeMs) {
        apiFallbackCount.incrementAndGet();
        apiFallbackTime.addAndGet(callTimeMs);
    }

    /**
     * 배치 갱신 완료 기록
     *
     * @param durationMs 배치 소요 시간 (밀리초)
     * @param successCount 성공 횟수
     * @param failureCount 실패 횟수
     */
    public void recordBatchCompletion(long durationMs, int successCount, int failureCount) {
        this.lastBatchTime = LocalDateTime.now();
        this.lastBatchDuration = durationMs;
        this.lastBatchSuccessCount = successCount;
        this.lastBatchFailureCount = failureCount;

        log.info("[BatchMetrics] 배치 갱신 완료 기록: duration={}ms, success={}, failure={}",
            durationMs, successCount, failureCount);
    }

    /**
     * 메트릭 리포트 생성
     *
     * @return 포맷된 메트릭 문자열
     */
    public String getMetricsReport() {
        // 계산
        int totalDbQueries = dbHitCount.get() + dbMissCount.get();
        double dbHitRate = totalDbQueries > 0
            ? (double) dbHitCount.get() / totalDbQueries * 100
            : 0;
        double avgDbQueryTime = totalDbQueries > 0
            ? (double) dbQueryTime.get() / totalDbQueries
            : 0;
        double avgApiFallbackTime = apiFallbackCount.get() > 0
            ? (double) apiFallbackTime.get() / apiFallbackCount.get()
            : 0;

        // 배치 정보
        String batchInfo = lastBatchTime != null
            ? String.format(
                "마지막 배치 갱신: %s (소요: %dms, 성공: %d, 실패: %d)",
                lastBatchTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                lastBatchDuration, lastBatchSuccessCount, lastBatchFailureCount)
            : "아직 배치 갱신 없음";

        return String.format(
            "========== 배치 처리 성능 메트릭 ==========\n" +
            "[DB 조회 통계]\n" +
            "  총 조회: %d회\n" +
            "  히트: %d회 ✅\n" +
            "  미스: %d회 ⚠️\n" +
            "  히트율: %.2f%%\n" +
            "  평균 쿼리 시간: %.2fms\n" +
            "\n" +
            "[API Fallback 통계]\n" +
            "  호출: %d회 ⚠️\n" +
            "  평균 호출 시간: %.2fms\n" +
            "\n" +
            "[배치 갱신 통계]\n" +
            "  %s\n" +
            "\n" +
            "[성과 평가]\n" +
            "  DB 히트율 (목표: >99%%): %s\n" +
            "  API 호출 감소 (목표: <1%%): %s\n" +
            "==========================================",
            totalDbQueries, dbHitCount.get(), dbMissCount.get(), dbHitRate,
            avgDbQueryTime, apiFallbackCount.get(), avgApiFallbackTime,
            batchInfo,
            dbHitRate >= 99 ? "✅ 달성" : "⚠️ 미달성",
            apiFallbackCount.get() < totalDbQueries / 100 ? "✅ 달성" : "⚠️ 미달성"
        );
    }

    /**
     * 메트릭 초기화 (테스트 또는 일일 리셋용)
     */
    public void reset() {
        dbHitCount.set(0);
        dbMissCount.set(0);
        dbQueryTime.set(0);
        apiFallbackCount.set(0);
        apiFallbackTime.set(0);
        lastBatchTime = null;
        lastBatchDuration = 0;
        lastBatchSuccessCount = 0;
        lastBatchFailureCount = 0;

        log.info("[BatchMetrics] 메트릭 초기화 완료");
    }
}

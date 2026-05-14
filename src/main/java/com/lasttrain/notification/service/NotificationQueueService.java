package com.lasttrain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Redis ZSET 기반 Delay Queue 서비스
 *
 * ─────────────────────────────────────────────────────────────
 * Delay Queue란?
 *   "특정 시각이 됐을 때 실행할 작업"을 미리 등록해두는 구조입니다.
 *   Redis Sorted Set(ZSET)은 각 항목에 score(점수)를 부여하고
 *   score 순서로 정렬해주는데, 여기서 score = 실행 시각(epoch ms)으로 사용합니다.
 * ─────────────────────────────────────────────────────────────
 *
 * Queue 구조:
 *   Key  : "notification:queue"              ← ZSET 이름
 *   Value: "{scheduleId}:{minutesBefore}"    ← 예) "42:30", "42:10"
 *   Score: execution timestamp (epoch ms)   ← 실행해야 할 Unix 시간(밀리초)
 *
 * 사용 예시:
 *   scheduleId=42, lastBoardTime=2026-05-11 23:11
 *   → "42:30" 등록, score = 22:41의 epoch ms (30분 전)
 *   → "42:10" 등록, score = 23:01의 epoch ms (10분 전)
 *
 *   1초마다 Worker가 score <= 현재시각인 항목을 꺼내서 Push 발송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueueService {

    // Redis ZSET의 키 이름 (고정값)
    private static final String QUEUE_KEY = "notification:queue";

    // 서울 시간대 (LocalDateTime → epoch ms 변환에 사용)
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua script: score <= now인 항목을 원자적(atomic)으로 조회 + 삭제
     *
     * ─────────────────────────────────────────────────────
     * 왜 Lua script를 쓰나요?
     *
     *   // 문제 있는 코드 (non-atomic):
     *   Set<String> items = redisTemplate.opsForZSet().rangeByScore(key, 0, now); // ① 조회
     *   redisTemplate.opsForZSet().remove(key, items);                             // ② 삭제
     *
     *   ① 조회 후 ② 삭제 사이에 다른 서버가 같은 항목을 조회하면
     *   동일 알림이 2번 발송됩니다.
     *
     *   Lua script는 Redis에서 ①②를 하나의 원자적 명령으로 실행하므로
     *   이 문제가 발생하지 않습니다.
     * ─────────────────────────────────────────────────────
     *
     * Lua script 설명:
     *   KEYS[1]  = "notification:queue"
     *   ARGV[1]  = 현재 시각(epoch ms, 문자열)
     *   LIMIT 0 100 = 한 번에 최대 100개만 처리 (Lua unpack 스택 오버플로 방지)
     */
    private static final DefaultRedisScript<List> POP_DUE_SCRIPT = new DefaultRedisScript<>("""
            local items = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, 100)
            if #items > 0 then
                redis.call('ZREM', KEYS[1], unpack(items))
            end
            return items
            """, List.class);

    // ──────────────────────────────────────────────────────────────────
    // public 메서드
    // ──────────────────────────────────────────────────────────────────

    /**
     * 알림 예약 등록 (subscribe 시점에 호출)
     *
     * 하나의 scheduleId에 대해 ZSET에 2개 항목을 추가합니다:
     *   - "{scheduleId}:30" → 30분 전 실행
     *   - "{scheduleId}:10" → 10분 전 실행
     *
     * @param scheduleId    DB의 notification_schedule.schedule_id
     * @param lastBoardTime 막차 탑승 마감 시각 (DATETIME)
     */
    public void enqueue(Long scheduleId, LocalDateTime lastBoardTime) {
        // 30분 전 실행 시각 → epoch ms 변환
        long exec30 = toEpochMilli(lastBoardTime.minusMinutes(30));
        // 10분 전 실행 시각 → epoch ms 변환
        long exec10 = toEpochMilli(lastBoardTime.minusMinutes(10));

        // ZADD notification:queue <score> <value>
        // score가 낮은 항목(= 더 이른 시각)이 앞에 오므로 자동 정렬됨
        redisTemplate.opsForZSet().add(QUEUE_KEY, scheduleId + ":30", exec30);
        redisTemplate.opsForZSet().add(QUEUE_KEY, scheduleId + ":10", exec10);

        log.debug("[Queue 등록] scheduleId={}, 30min={}ms, 10min={}ms",
                scheduleId, exec30, exec10);
    }

    /**
     * 실행 시점이 된 알림 항목을 원자적으로 꺼냄 (pop)
     *
     * score <= 현재 시각인 항목만 반환합니다.
     * Lua script로 조회 + 삭제가 원자적으로 처리되므로 중복 발송 없음.
     *
     * @return 실행 대상 item 목록 (예: ["42:30", "55:10"])
     *         실행할 항목이 없으면 빈 리스트 반환
     */
    @SuppressWarnings("unchecked")
    public List<String> popDue() {
        // 현재 시각(epoch ms)을 문자열로 변환해서 Lua에 전달
        long now = System.currentTimeMillis();

        List<String> result = redisTemplate.execute(
                POP_DUE_SCRIPT,
                List.of(QUEUE_KEY),       // KEYS[1]
                String.valueOf(now)       // ARGV[1]
        );

        return result != null ? result : List.of();
    }

    /**
     * 알림 예약 취소 (schedule 삭제 시 호출)
     *
     * DB에서 schedule을 삭제할 때 Redis에서도 해당 항목을 함께 제거합니다.
     * 이미 발송된 항목이라면 ZSET에 없으므로 ZREM이 무시됩니다 (에러 없음).
     *
     * @param scheduleId 취소할 schedule ID
     */
    public void cancel(Long scheduleId) {
        // 30분 전, 10분 전 이벤트 모두 제거
        redisTemplate.opsForZSet().remove(
                QUEUE_KEY,
                scheduleId + ":30",
                scheduleId + ":10"
        );
        log.debug("[Queue 취소] scheduleId={}", scheduleId);
    }

    // ──────────────────────────────────────────────────────────────────
    // private 헬퍼
    // ──────────────────────────────────────────────────────────────────

    /**
     * LocalDateTime → epoch milliseconds 변환 (서울 시간대 기준)
     *
     * epoch ms란: 1970-01-01 00:00:00 UTC 기준으로 경과한 밀리초 수.
     * Redis ZSET의 score로 사용하기 위해 변환합니다.
     */
    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL).toInstant().toEpochMilli();
    }
}
-- notification_schedule 테이블에 notify_minutes_before 컬럼 추가
-- 사용자가 선택한 알림 시간(10분, 20분, 30분 등)을 저장합니다.

ALTER TABLE notification_schedule
ADD COLUMN notify_minutes_before INT NOT NULL DEFAULT 30;

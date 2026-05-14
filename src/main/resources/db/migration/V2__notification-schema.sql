-- V2 — notification_events 컬럼 추가 (PEL 재처리 멱등성, stuck 복구)
ALTER TABLE notification_events
    ADD COLUMN stream_message_id VARCHAR(100) NULL,
    ADD COLUMN updated_at        TIMESTAMP;

ALTER TABLE notification_events
    ADD CONSTRAINT uq_ne_stream_message_id UNIQUE (stream_message_id);

CREATE INDEX idx_ne_processing ON notification_events (status, updated_at)
    WHERE status = 'PROCESSING';

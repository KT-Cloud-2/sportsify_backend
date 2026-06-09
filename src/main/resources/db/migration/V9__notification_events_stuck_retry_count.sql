ALTER TABLE notification_events
    ADD COLUMN stuck_retry_count INT NOT NULL DEFAULT 0;

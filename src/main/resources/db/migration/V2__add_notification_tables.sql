CREATE TABLE notification_events (
    id           BIGSERIAL    PRIMARY KEY,
    event_type   VARCHAR(50)  NOT NULL,
    payload      JSONB,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP    NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_ne_status ON notification_events(status, created_at);

CREATE TABLE notification_settings (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT    NOT NULL,
    ticket_open_alert   BOOLEAN   NOT NULL DEFAULT TRUE,
    game_start_alert    BOOLEAN   NOT NULL DEFAULT TRUE,
    payment_alert       BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMP,
    CONSTRAINT fk_ns_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uq_ns_member UNIQUE (member_id)
);

CREATE TABLE notification_channels (
    id              BIGSERIAL    PRIMARY KEY,
    member_id       BIGINT       NOT NULL,
    channel_type    VARCHAR(20)  NOT NULL,
    channel_target  VARCHAR(500) NOT NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT fk_nc_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uq_nc        UNIQUE (member_id, channel_type)
);

CREATE INDEX idx_nc_member ON notification_channels(member_id);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT    NOT NULL,
    event_id   BIGINT    NOT NULL,
    is_read    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_noti_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_noti_event  FOREIGN KEY (event_id)  REFERENCES notification_events(id),
    CONSTRAINT uq_noti        UNIQUE (event_id, member_id)
);

CREATE INDEX idx_noti_member ON notifications(member_id, is_read, created_at DESC);

CREATE TABLE notification_history (
    id               BIGSERIAL   PRIMARY KEY,
    notification_id  BIGINT      NOT NULL,
    channel_type     VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    error_message    TEXT,
    created_at       TIMESTAMP   NOT NULL,
    CONSTRAINT fk_nh_notification FOREIGN KEY (notification_id) REFERENCES notifications(id)
);

CREATE INDEX idx_nh_notification ON notification_history(notification_id);

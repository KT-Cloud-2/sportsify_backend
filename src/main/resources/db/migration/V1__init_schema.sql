-- ============================================================
-- V1 — 초기 스키마 (ERD 기반)
-- PostgreSQL 18 기준
-- ============================================================

-- ============================================================
-- 회원 도메인
-- ============================================================

CREATE TABLE members
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    nickname      VARCHAR(50),
    provider      VARCHAR(20)  NOT NULL,                  -- GOOGLE | KAKAO
    provider_id   VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE | WITHDRAWN
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',   -- USER | ADMIN
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    last_login_at TIMESTAMP,
    CONSTRAINT uq_member_provider UNIQUE (provider, provider_id)
);

CREATE INDEX idx_members_email ON members (email);
CREATE INDEX idx_members_status ON members (status);

-- ============================================================
-- 팀 도메인
-- ============================================================

CREATE TABLE teams
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    short_name VARCHAR(20),
    sport_type VARCHAR(30)  NOT NULL, -- BASEBALL | FOOTBALL | BASKETBALL
    logo_url   VARCHAR(500),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_team_name_sport UNIQUE (name, sport_type)
);

CREATE INDEX idx_teams_sport_type ON teams (sport_type);
CREATE INDEX idx_teams_is_active ON teams (is_active);

CREATE TABLE member_favorite_teams
(
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT    NOT NULL,
    team_id    BIGINT    NOT NULL,
    priority   INT       NOT NULL DEFAULT 0, -- 낮을수록 우선순위 높음
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mft_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_mft_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT uq_mft UNIQUE (member_id, team_id)
);

CREATE INDEX idx_mft_member ON member_favorite_teams (member_id);

CREATE TABLE activity_logs
(
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT,               -- null 허용 (로그인 실패 시 미식별)
    action     VARCHAR(30) NOT NULL, -- LOGIN_SUCCESS | LOGIN_FAIL | LOGOUT
    ip_address VARCHAR(45),          -- IPv6 대비 45자
    user_agent TEXT,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT fk_log_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX idx_activity_member ON activity_logs (member_id, created_at DESC);



-- ============================================================
-- 경기/좌석 도메인
-- ============================================================

CREATE TABLE stadiums
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(200),
    total_seats INTEGER
);

CREATE TABLE zone_grades
(
    id         BIGSERIAL PRIMARY KEY,
    stadium_id BIGINT      NOT NULL,
    name       VARCHAR(30) NOT NULL, -- VIP | R | S | A | OUTFIELD
    CONSTRAINT fk_zg_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (id)
);

CREATE TABLE sections
(
    id            BIGSERIAL PRIMARY KEY,
    stadium_id    BIGINT NOT NULL,
    zone_grade_id BIGINT NOT NULL,
    name          VARCHAR(50),
    floor         VARCHAR(10),
    CONSTRAINT fk_sec_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (id),
    CONSTRAINT fk_sec_zone FOREIGN KEY (zone_grade_id) REFERENCES zone_grades (id)
);

CREATE TABLE seats
(
    id            BIGSERIAL PRIMARY KEY,
    section_id    BIGINT NOT NULL,
    zone_grade_id BIGINT NOT NULL,
    row_number    VARCHAR(10),
    seat_number   VARCHAR(10),
    CONSTRAINT fk_seat_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_seat_zone FOREIGN KEY (zone_grade_id) REFERENCES zone_grades (id),
    CONSTRAINT uq_seat UNIQUE (section_id, row_number, seat_number)
);

CREATE TABLE games
(
    id                  BIGSERIAL PRIMARY KEY,
    stadium_id          BIGINT      NOT NULL,
    home_team_id        BIGINT,
    away_team_id        BIGINT,
    sport_type          VARCHAR(30),
    start_at            TIMESTAMP   NOT NULL,
    duration_minutes    INT         NOT NULL DEFAULT 180,
    status              VARCHAR(20) NOT NULL, -- SCHEDULED | OPEN | IN_PROGRESS | FINISHED | CANCELLED
    day_type            VARCHAR(10),          -- WEEKDAY | WEEKEND | HOLIDAY
    game_grade          VARCHAR(20),          -- NORMAL | RIVAL
    max_ticket_per_user INT         NOT NULL DEFAULT 4,
    sale_start_at       TIMESTAMP,
    sale_end_at         TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL,
    deleted_at          TIMESTAMP,
    CONSTRAINT fk_game_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (id),
    CONSTRAINT fk_game_home_team FOREIGN KEY (home_team_id) REFERENCES teams (id),
    CONSTRAINT fk_game_away_team FOREIGN KEY (away_team_id) REFERENCES teams (id)
);

CREATE INDEX idx_games_status ON games (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_games_start_at ON games (start_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_games_sport_type ON games (sport_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_games_home_team ON games (home_team_id);
CREATE INDEX idx_games_away_team ON games (away_team_id);

CREATE TABLE game_seats
(
    id          BIGSERIAL PRIMARY KEY,
    game_id     BIGINT      NOT NULL,
    seat_id     BIGINT      NOT NULL,
    seat_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE | RESERVED | SOLD,
    price       INT         NOT NULL,
    CONSTRAINT fk_gs_game FOREIGN KEY (game_id) REFERENCES games (id),
    CONSTRAINT fk_gs_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT uq_game_seat UNIQUE (game_id, seat_id)
);

CREATE INDEX idx_game_seats_status ON game_seats (game_id, seat_status);

-- ============================================================
-- 예매/결제 도메인
-- ============================================================

-- 주문 (좌석 선점 + 결제의 컨테이너)
CREATE TABLE orders
(
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT,
    status     VARCHAR(20) NOT NULL, -- PENDING | CONFIRMED | CANCELLED
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_order_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX idx_orders_member ON orders (member_id);

-- 주문 좌석 (주문 1건에 여러 좌석 가능, 선점 만료 포함)
CREATE TABLE order_seats
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL,
    game_seat_id BIGINT NOT NULL,
    status       VARCHAR(30), -- HOLDING | CONFIRMED | CANCELLED | EXPIRED
    expires_at   TIMESTAMP,   -- 선점 만료 시각 (15분)
    created_at   TIMESTAMP,
    CONSTRAINT fk_os_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_os_game_seat FOREIGN KEY (game_seat_id) REFERENCES game_seats (id)
);

CREATE INDEX idx_order_seats_order ON order_seats (order_id);
CREATE INDEX idx_order_seats_expires ON order_seats (expires_at) WHERE status = 'HOLDING';

-- 티켓 (결제 완료 후 발급, UUID 기반 고유 번호)
CREATE TABLE tickets
(
    id            BIGSERIAL PRIMARY KEY,
    order_seat_id BIGINT      NOT NULL,
    member_id     BIGINT      NOT NULL,
    ticket_number VARCHAR(36) NOT NULL,                     -- UUID
    price         INT         NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED', -- CONFIRMED | USED | CANCELLED
    issued_at     TIMESTAMP   NOT NULL,
    used_at       TIMESTAMP,
    cancelled_at  TIMESTAMP,
    CONSTRAINT fk_ticket_order_seat FOREIGN KEY (order_seat_id) REFERENCES order_seats (id),
    CONSTRAINT fk_ticket_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uq_ticket_number UNIQUE (ticket_number)
);

CREATE INDEX idx_tickets_status ON tickets (member_id, status);

-- 결제
CREATE TABLE payments
(
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT    NOT NULL,
    member_id       BIGINT,
    payment_key     VARCHAR(200), -- PG사 거래 ID
    idempotency_key VARCHAR(100), -- 중복 결제 방지 키
    method          VARCHAR(30),  -- CARD | KAKAO_PAY | TOSS_PAY
    total_amount    INT,
    discount_amount INT       NOT NULL DEFAULT 0,
    final_amount    INT,
    status          VARCHAR(30),  -- PENDING | COMPLETED | REFUNDED | FAILED | CANCELLED
    requested_at    TIMESTAMP,
    approved_at     TIMESTAMP,
    failed_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uq_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_member ON payments (member_id);
CREATE INDEX idx_payments_status ON payments (status);

-- 환불
CREATE TABLE refunds
(
    id            BIGSERIAL PRIMARY KEY,
    payment_id    BIGINT NOT NULL,
    refund_amount INT,
    reason        VARCHAR(255),
    status        VARCHAR(30), -- PENDING | COMPLETED | FAILED
    created_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

-- 가격 정책
CREATE TABLE price_policies
(
    id            BIGSERIAL PRIMARY KEY,
    stadium_id    BIGINT      NOT NULL,
    day_type      VARCHAR(10) NOT NULL, -- WEEKDAY | WEEKEND | HOLIDAY
    zone_grade_id BIGINT      NOT NULL,
    game_grade    VARCHAR(20) NOT NULL, -- REGULAR | PLAYOFF | FINAL
    price         INT         NOT NULL,

    CONSTRAINT fk_pp_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (id),
    CONSTRAINT fk_pp_zone_grade FOREIGN KEY (zone_grade_id) REFERENCES zone_grades (id),
    CONSTRAINT uq_price_policy UNIQUE (stadium_id, day_type, zone_grade_id, game_grade)
);

CREATE INDEX idx_price_policies_stadium ON price_policies (stadium_id);
CREATE INDEX idx_price_policies_zone_grade ON price_policies (zone_grade_id);

-- ============================================================
-- 채팅 도메인
-- ============================================================

CREATE TABLE chat_rooms
(
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100),
    type             VARCHAR(20) NOT NULL, -- GAME | TEAM | PRIVATE | GROUP
    game_id          BIGINT,
    team_id          BIGINT,
    created_by       BIGINT,
    max_participants INT         NOT NULL DEFAULT 5000,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT fk_chat_game FOREIGN KEY (game_id) REFERENCES games (id),
    CONSTRAINT fk_chat_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_chat_creator FOREIGN KEY (created_by) REFERENCES members (id)
);

CREATE INDEX idx_chat_rooms_type ON chat_rooms (type);
CREATE INDEX idx_chat_rooms_game ON chat_rooms (game_id);
CREATE INDEX idx_chat_rooms_team ON chat_rooms (team_id);

CREATE TABLE chat_participants
(
    id                   BIGSERIAL PRIMARY KEY,
    room_id              BIGINT      NOT NULL,
    member_id            BIGINT      NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'JOINED', -- INVITED | JOINED | LEFT | KICKED
    notification_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    last_read_message_id BIGINT,
    joined_at            TIMESTAMP,
    left_at              TIMESTAMP,
    CONSTRAINT fk_cp_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_cp_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uq_cp UNIQUE (room_id, member_id)
);

CREATE INDEX idx_cp_room ON chat_participants (room_id, status);
CREATE INDEX idx_cp_member ON chat_participants (member_id, status);

CREATE TABLE chat_messages
(
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT      NOT NULL,
    sender_id   BIGINT      NOT NULL,
    content     TEXT,
    type        VARCHAR(20) NOT NULL DEFAULT 'MESSAGE', -- MESSAGE | CHEER | SYSTEM | FILE | IMAGE
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | DELETED
    is_filtered BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL,
    deleted_at  TIMESTAMP,
    CONSTRAINT fk_msg_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES members (id)
);

CREATE INDEX idx_msg_room_time ON chat_messages (room_id, created_at DESC);
CREATE INDEX idx_msg_sender ON chat_messages (sender_id);

-- ============================================================
-- 알림 도메인
-- ============================================================

-- 알림 설정 (사용자별 ON/OFF)
CREATE TABLE notification_settings
(
    id                BIGSERIAL PRIMARY KEY,
    member_id         BIGINT  NOT NULL,
    ticket_open_alert BOOLEAN NOT NULL DEFAULT TRUE,
    game_start_alert  BOOLEAN NOT NULL DEFAULT TRUE,
    payment_alert     BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMP,
    CONSTRAINT fk_ns_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uq_ns_member UNIQUE (member_id)
);

-- 알림 채널 (EMAIL | MQTT | SLACK)
CREATE TABLE notification_channels
(
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT       NOT NULL,
    channel_type   VARCHAR(20)  NOT NULL, -- EMAIL | MQTT | SLACK
    channel_target VARCHAR(500) NOT NULL, -- 이메일 주소 / 슬랙 웹훅 URL 등
    is_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP,
    CONSTRAINT fk_nc_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uq_nc UNIQUE (member_id, channel_type)
);

CREATE INDEX idx_nc_member ON notification_channels (member_id);

-- 알림 이벤트 (발행 원장 — source of truth, Redis 장애 시 재처리 기반)
CREATE TABLE notification_events
(
    id           BIGSERIAL PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,                   -- TICKET_OPEN | GAME_START | PAYMENT_COMPLETED | CHAT_MENTION
    payload      JSONB,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PUBLISHED | FAILED
    created_at   TIMESTAMP   NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_ne_status ON notification_events (status, created_at);

-- 사용자 인박스 알림
CREATE TABLE notifications
(
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT    NOT NULL,
    event_id   BIGINT    NOT NULL,
    is_read    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_noti_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_noti_event FOREIGN KEY (event_id) REFERENCES notification_events (id),
    CONSTRAINT uq_noti UNIQUE (event_id, member_id) -- 중복 알림 방지
);

CREATE INDEX idx_noti_member ON notifications (member_id, is_read, created_at DESC);

-- 알림 발송 이력
CREATE TABLE notification_history
(
    id              BIGSERIAL PRIMARY KEY,
    notification_id BIGINT      NOT NULL,
    channel_type    VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL, -- SENT | FAILED
    error_message   TEXT,
    created_at      TIMESTAMP   NOT NULL,
    CONSTRAINT fk_nh_notification FOREIGN KEY (notification_id) REFERENCES notifications (id)
);

CREATE INDEX idx_nh_notification ON notification_history (notification_id);
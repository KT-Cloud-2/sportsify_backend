# ERD — Sportsify

> 모든 테이블은 PostgreSQL 18 기준으로 작성.  
> `BIGSERIAL` = auto-increment Long PK.  
> `TIMESTAMP` = timezone 없는 UTC 기준 저장 (애플리케이션 레이어에서 UTC 강제).

---

## 테이블 목록

| 테이블                   | 도메인 | 설명                                                       |
|-----------------------|-----|----------------------------------------------------------|
| members               | 회원  | 회원 기본 정보                                                 |
| member_favorite_teams | 회원  | 선호 팀 매핑                                                  |
| activity_logs         | 회원  | 로그인 기록, 접속 이력                                            |
| teams                 | 팀   | 스포츠 팀 정보                                                 |
| stadiums              | 경기  | 경기장 정보                                                   |
| zone_grades           | 경기  | 경기장 구역 등급                                                |
| sections              | 경기  | 경기장 구역                                                   |
| seats                 | 경기  | 물리 좌석 (경기장 고정)                                           |
| games                 | 경기  | 경기 정보                                                    |
| price_policies        | 경기  | 경기장/구역/등급별 가격 정책                                         |
| game_seats            | 예매  | 경기별 좌석 상태 (가격/선점 포함)                                     |
| orders                | 결제  | 주문 헤더                                                    |
| order_seats           | 결제  | 주문 좌석 상세                                                 |
| tickets               | 예매  | 발급된 티켓                                                   |
| payments              | 결제  | 결제 정보                                                    |
| refunds               | 결제  | 환불 정보                                                    |
| chat_rooms            | 채팅  | 채팅방 (type: DIRECT/GAME, status: ACTIVE/ARCHIVED/DELETED) |
| chat_room_members     | 채팅  | 채팅방 참여자 (status: INVITED/JOINED/LEFT/BANNED)             |
| chat_messages         | 채팅  | 채팅 메시지 (type: TEXT/IMAGE/FILE/SYSTEM)                    |
| notification_settings | 알림  | 사용자별 알림 ON/OFF 설정                                        |
| notification_channels | 알림  | 사용자별 알림 채널 (EMAIL/MQTT/SLACK)                            |
| notification_events   | 알림  | 발행된 알림 이벤트 (영속화)                                         |
| notifications         | 알림  | 사용자 인박스 알림                                               |
| notification_history  | 알림  | 알림 발송 이력                                                 |

---

## DDL

### 회원 도메인

```sql
-- 회원
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

-- 선호 팀
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

-- 로그인/활동 기록
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
```

---

### 팀 도메인

```sql
-- 팀
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
```

---

### 경기/좌석 도메인

```sql
-- 경기장
CREATE TABLE stadiums
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(200),
    total_seats INTEGER
);

-- 구역 등급
CREATE TABLE zone_grades
(
    id         BIGSERIAL PRIMARY KEY,
    stadium_id BIGINT      NOT NULL,
    name       VARCHAR(30) NOT NULL, -- VIP | R | S | A | OUTFIELD
    CONSTRAINT fk_zg_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (id)
);

-- 구역 (경기장 물리 구역)
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

-- 물리 좌석 (경기장에 고정된 좌석. 경기마다 재사용)
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

-- 경기
CREATE TABLE games
(
    id                  BIGSERIAL PRIMARY KEY,
    stadium_id          BIGINT      NOT NULL,
    home_team_id        BIGINT,
    away_team_id        BIGINT,
    sport_type          VARCHAR(30),
    start_at            TIMESTAMP   NOT NULL,
    duration_minutes    INT         NOT NULL DEFAULT 180,
    status              VARCHAR(20) NOT NULL, -- SCHEDULED | ON_SALE | SALE_CLOSED | IN_PROGRESS | FINISHED | CANCELLED
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

-- 가격 정책 (경기장/구역/등급/요일별 기준 가격)
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

-- 경기별 좌석 (경기마다 상태/가격이 달라짐)
CREATE TABLE game_seats
(
    id          BIGSERIAL PRIMARY KEY,
    game_id     BIGINT      NOT NULL,
    seat_id     BIGINT      NOT NULL,
    price       INT         NOT NULL,
    seat_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE | RESERVED | SOLD
    CONSTRAINT fk_gs_game FOREIGN KEY (game_id) REFERENCES games (id),
    CONSTRAINT fk_gs_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT uq_game_seat UNIQUE (game_id, seat_id)
);

CREATE INDEX idx_game_seats_status ON game_seats (game_id, seat_status);
```

---

### 예매/결제 도메인

```sql
-- 주문 (좌석 선점 + 결제의 컨테이너)
CREATE TABLE orders
(
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT,
    status     VARCHAR(20) NOT NULL, -- PENDING | CONFIRMED | CANCELLED | PAYING
    expires_at TIMESTAMP,            -- 선점 만료 시각 (15분)
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_order_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX idx_orders_member ON orders (member_id);
CREATE INDEX idx_order_seats_expires ON orders (expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_orders_status_paying ON orders (id) WHERE status = 'PAYING';

-- 주문 좌석 (주문 1건에 여러 좌석 가능)
CREATE TABLE order_seats
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT      NOT NULL,
    game_seat_id BIGINT      NOT NULL,
    status       VARCHAR(30) NOT NULL, -- HOLDING | CONFIRMED | CANCELLED | EXPIRED
    price        INT         NOT NULL,
    created_at   TIMESTAMP,
    CONSTRAINT fk_os_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_os_game_seat FOREIGN KEY (game_seat_id) REFERENCES game_seats (id)
);

CREATE INDEX idx_order_seats_order ON order_seats (order_id);

-- 티켓 (결제 완료 후 발급)
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
    toss_order_id   VARCHAR(50),           -- Toss 결제용 주문번호
    order_id        BIGINT,                -- orders.id FK (nullable: 백필 정책 확정 전)
    member_id       BIGINT,
    user_id         BIGINT,
    match_id        BIGINT,
    seat_id         BIGINT,
    payment_key     VARCHAR(200),          -- PG사 거래 ID
    idempotency_key VARCHAR(100) NOT NULL, -- 중복 결제 방지 키
    payment_method  VARCHAR(30)  NOT NULL, -- CARD | KAKAO_PAY | TOSS_PAY
    amount          BIGINT       NOT NULL,
    status          VARCHAR(30)  NOT NULL, -- PENDING | COMPLETED | REFUNDED | FAILED | CANCELLED
    requested_at    TIMESTAMP    NOT NULL,
    approved_at     TIMESTAMP,
    failed_at       TIMESTAMP,
    cancel_reason   VARCHAR(255),
    canceled_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_payments_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uq_payment_key UNIQUE (payment_key),
    CONSTRAINT uq_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_payment_order_id UNIQUE (order_id)
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
```

---

### 채팅 도메인

```sql
-- 채팅방
-- type: DIRECT(1:1) | GAME(경기방)
-- status: ACTIVE | ARCHIVED | DELETED
CREATE TABLE chat_rooms
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    image_url  TEXT,
    game_id    BIGINT,
    created_by BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    status     VARCHAR(20)  NOT NULL, -- ACTIVE | ARCHIVED | DELETED
    CONSTRAINT fk_chat_game FOREIGN KEY (game_id) REFERENCES games (id),
    CONSTRAINT fk_chat_creator FOREIGN KEY (created_by) REFERENCES members (id)
);

CREATE INDEX idx_chat_rooms_game_id ON chat_rooms (game_id);

-- 채팅방 참여자
-- status: INVITED | JOINED | LEFT | BANNED
CREATE TABLE chat_room_members
(
    id                   BIGSERIAL PRIMARY KEY,
    room_id              BIGINT      NOT NULL,
    member_id            BIGINT      NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'JOINED',
    notification_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    last_read_message_id BIGINT,
    joined_at            TIMESTAMP   NOT NULL,
    updated_at           TIMESTAMP   NOT NULL,
    CONSTRAINT fk_cp_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_cp_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_cp_message FOREIGN KEY (last_read_message_id) REFERENCES chat_messages (id),
    CONSTRAINT uq_cp UNIQUE (room_id, member_id)
);

CREATE INDEX idx_chat_room_members_room ON chat_room_members (room_id);
CREATE INDEX idx_chat_room_members_member ON chat_room_members (member_id);

-- 채팅 메시지
-- type: TEXT | IMAGE | FILE | SYSTEM
-- status: ACTIVE | DELETED (soft delete)
CREATE TABLE chat_messages
(
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL,
    sender_id  BIGINT, -- SYSTEM 메시지는 null 허용 (V6)
    content    TEXT,
    type       VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT fk_msg_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES members (id)
);

CREATE INDEX idx_messages_room_id_id ON chat_messages (room_id, id);
CREATE INDEX idx_msg_sender ON chat_messages (sender_id);
```

---

### 알림 도메인

```sql
-- 알림 설정 (사용자별 ON/OFF)
CREATE TABLE notification_settings
(
    id                 BIGSERIAL PRIMARY KEY,
    member_id          BIGINT  NOT NULL,
    ticket_open_alert  BOOLEAN NOT NULL DEFAULT TRUE,
    game_start_alert   BOOLEAN NOT NULL DEFAULT TRUE,
    payment_alert      BOOLEAN NOT NULL DEFAULT TRUE,
    chat_mention_alert BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at         TIMESTAMP,
    CONSTRAINT fk_ns_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uq_ns_member UNIQUE (member_id)
);

-- 알림 채널 (EMAIL | MQTT | SLACK 등)
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

-- 알림 이벤트 (발행 원장 — source of truth)
CREATE TABLE notification_events
(
    id                BIGSERIAL PRIMARY KEY,
    event_type        VARCHAR(50) NOT NULL,                   -- TICKET_OPEN | GAME_START | PAYMENT_COMPLETED | CHAT_MENTION
    payload           JSONB,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PROCESSING | PUBLISHED | FAILED | CANCELLED
    stream_message_id VARCHAR(100),                           -- Redis Streams 메시지 ID (멱등성)
    scheduled_at      TIMESTAMP,                              -- 예약 발송 시각 (null = 즉시 발송)
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP,
    published_at      TIMESTAMP,
    CONSTRAINT uq_ne_stream_message_id UNIQUE (stream_message_id)
);

CREATE INDEX idx_ne_status ON notification_events (status, created_at);
CREATE INDEX idx_ne_scheduled ON notification_events (status, scheduled_at) WHERE scheduled_at IS NOT NULL;
CREATE INDEX idx_ne_processing ON notification_events (status, updated_at) WHERE status = 'PROCESSING';

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
```

---

## Redis Key 설계

### 3.1 회원 / 인증 도메인

| Key Pattern                    | Type   | TTL              | Purpose          |
|--------------------------------|--------|------------------|------------------|
| `auth:refresh:{memberId}`      | String | 14일              | Refresh Token 저장 |
| `auth:blacklist:{accessToken}` | String | AccessToken 만료시간 | 로그아웃 토큰 블랙리스트    |

### 3.2 채팅 도메인

| Key Pattern                     | Type   | TTL  | Purpose           |
|---------------------------------|--------|------|-------------------|
| `chat:read:{roomId}:{memberId}` | String | 24시간 | 사용자 마지막 읽은 메시지 ID |

---

## Redis Streams (이벤트 버스)

| Stream Key          | Producer               | Consumer     | 트리거           |
|---------------------|------------------------|--------------|---------------|
| `ticket.opened`     | Ticketing              | Notification | 경기 티켓 판매 오픈 시 |
| `payment.completed` | Payment                | Notification | 결제 검증 완료 시    |
| `game.starting`     | Ticketing (@Scheduled) | Notification | 경기 시작 1시간 전   |
| `chat.mentioned`    | Chat                   | Notification | 채팅에서 @멘션 발생 시 |

> **Consumer Group** 방식 사용 → ACK 기반 안정적 소비, 재시도 보장  
> **Redis Streams vs Kafka**: 인프라 단순화를 위해 Redis Streams 채택. Kafka는 별도 운영 비용 발생.

---

## 설계 결정 사항

### D-1. game_seats 분리

물리 좌석(`seats`)과 경기별 좌석 상태/가격(`game_seats`)을 분리.  
같은 물리 좌석이 경기마다 다른 가격·상태를 가질 수 있음.

### D-2. seats 테이블에 zone_grade_id FK 추가
`seats` 테이블이 `section_id`와 별도로 `zone_grade_id`를 직접 참조.  
`Seat` 엔티티가 `Section`을 통하지 않고 직접 `ZoneGrade`에 접근할 수 있도록 하여 JOIN 횟수를 줄임.  
`Seat.Builder`에서 `section.getZoneGrade()`로 자동 설정.

### D-3. price_policies 테이블 (동적 가격 정책)
`game_seats.price`는 경기 생성 시 `price_policies` 테이블에서 조회하여 설정.  
경기장(stadium) × 요일(day_type) × 구역등급(zone_grade) × 경기등급(game_grade) 조합으로 가격 결정.  
`PricePolicyService`가 해당 조합의 가격을 조회하고, 누락된 구역이 있으면 `PRICE_POLICY_NOT_FOUND` 예외 발생.

### D-4. game_seats에서 zone_grade_id, team_side 제거
`game_seats`는 `seat_id`를 통해 `seats → zone_grades`로 등급 정보 접근 가능하므로 중복 FK 제거.  
`team_side` 컬럼도 현재 구현에서 사용하지 않아 제거. 필요 시 향후 추가.

### D-5. orders.expires_at (주문 레벨 만료)
선점 만료를 `order_seats`가 아닌 `orders` 레벨에서 관리.  
주문 생성 시 `LocalDateTime.now().plusMinutes(10)` 설정.  
`OrderExpirationScheduler`가 매 60초마다 만료 주문을 스캔하여 좌석 해제.

### D-6. orders 상태 확장 (PAYING, EXPIRED)
기존 `PENDING | CONFIRMED | CANCELLED`에 `PAYING`, `EXPIRED` 추가.
- `PENDING` → 결제 시작 시 `PAYING` 전환
- `PAYING` → 결제 성공 시 `CONFIRMED`, 실패 시 `CANCELLED`
- `PENDING` → 15분 만료 시 `EXPIRED`
- `PAYING` 상태에서 결제 실패 감지 시 `CANCELLED`

### D-7. tickets 테이블

`order_seats`에서 결제 완료 시 `tickets`를 발급. tickets는 사용자에게 전달되는 "실제 입장권"이며, UUID 기반 고유 번호를 가짐.

### D-8. games 상태 자동 전환 (TaskScheduler)
- 서버 시작 시 `GameScheduleInitializer`가 보정 처리:
    - `saleStartAt` 경과 + SCHEDULED → 즉시 ON_SALE 전환
    - `saleEndAt` 경과 + ON_SALE → 즉시 SALE_CLOSED 전환
    - 미래 `saleStartAt`/`saleEndAt` → TaskScheduler 등록
- `GameStatusUpdater`가 상태 전환 시 비관적 락(`findByIdForUpdate`) 사용

### D-9. notification_events 영속화

Redis Streams 발행 전에 반드시 DB에 저장 (source of truth).  
Redis 장애 시 DB를 기반으로 재처리 가능.

### D-10. Soft Delete

`games.deleted_at` — 논리 삭제. 예매 내역이 있는 경기는 물리 삭제 금지.  
조회 쿼리에는 `WHERE deleted_at IS NULL` 조건 항상 포함.

### D-11. Partial Index 최적화
- `idx_order_seats_expires`: `status = 'PENDING'` 조건으로 만료 스캔 최적화
- `idx_orders_status_paying`: `status = 'PAYING'` 조건으로 결제 실패 주문 스캔 최적화

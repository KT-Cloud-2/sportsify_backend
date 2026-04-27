# ERD — Sortsify

> 모든 테이블은 PostgreSQL 18 기준으로 작성.  
> `BIGSERIAL` = auto-increment Long PK.  
> `TIMESTAMP` = timezone 없는 UTC 기준 저장 (애플리케이션 레이어에서 UTC 강제).

---

## 테이블 목록

| 테이블 | 도메인 | 설명 |
|--------|--------|------|
| members | 회원 | 회원 기본 정보 |
| member_favorite_teams | 회원 | 선호 팀 매핑 |
| activity_logs | 회원 | 로그인 기록, 접속 이력 |
| teams | 팀 | 스포츠 팀 정보 |
| stadiums | 경기 | 경기장 정보 |
| zone_grades | 경기 | 경기장 구역 등급 |
| sections | 경기 | 경기장 구역 |
| seats | 경기 | 물리 좌석 (경기장 고정) |
| games | 경기 | 경기 정보 |
| game_seats | 예매 | 경기별 좌석 상태 (가격/선점 포함) |
| orders | 결제 | 주문 헤더 |
| order_seats | 결제 | 주문 좌석 상세 |
| tickets | 예매 | 발급된 티켓 |
| payments | 결제 | 결제 정보 |
| refunds | 결제 | 환불 정보 |
| chat_rooms | 채팅 | 채팅방 |
| chat_participants | 채팅 | 채팅방 참여자 |
| chat_messages | 채팅 | 채팅 메시지 |
| notification_settings | 알림 | 사용자별 알림 ON/OFF 설정 |
| notification_channels | 알림 | 사용자별 알림 채널 (EMAIL/MQTT/SLACK) |
| notification_events | 알림 | 발행된 알림 이벤트 (영속화) |
| notifications | 알림 | 사용자 인박스 알림 |
| notification_history | 알림 | 알림 발송 이력 |

---

## DDL

### 회원 도메인

```sql
-- 회원
CREATE TABLE members (
    id             BIGSERIAL    PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    nickname       VARCHAR(50),
    provider       VARCHAR(20)  NOT NULL,          -- GOOGLE | KAKAO
    provider_id    VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | INACTIVE | WITHDRAWN
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',    -- USER | ADMIN
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP,
    last_login_at  TIMESTAMP,
    CONSTRAINT uq_member_provider UNIQUE (provider, provider_id)
);

CREATE INDEX idx_members_email ON members(email);
CREATE INDEX idx_members_status ON members(status);

-- 선호 팀
CREATE TABLE member_favorite_teams (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT    NOT NULL,
    team_id    BIGINT    NOT NULL,
    priority   INT       NOT NULL DEFAULT 0,  -- 낮을수록 우선순위 높음
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mft_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_mft_team   FOREIGN KEY (team_id)   REFERENCES teams(id),
    CONSTRAINT uq_mft        UNIQUE (member_id, team_id)
);

CREATE INDEX idx_mft_member ON member_favorite_teams(member_id);

-- 로그인/활동 기록
CREATE TABLE activity_logs (
    id          BIGSERIAL    PRIMARY KEY,
    member_id   BIGINT,                            -- null 허용 (로그인 실패 시 미식별)
    action      VARCHAR(30)  NOT NULL,             -- LOGIN_SUCCESS | LOGIN_FAIL | LOGOUT
    ip_address  VARCHAR(45),                       -- IPv6 대비 45자
    user_agent  TEXT,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_log_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE INDEX idx_activity_member ON activity_logs(member_id, created_at DESC);
```

---

### 팀 도메인

```sql
-- 팀
CREATE TABLE teams (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    short_name  VARCHAR(20),
    sport_type  VARCHAR(30)  NOT NULL,   -- BASEBALL | FOOTBALL | BASKETBALL
    logo_url    VARCHAR(500),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_team_name_sport UNIQUE (name, sport_type)
);

CREATE INDEX idx_teams_sport_type ON teams(sport_type);
CREATE INDEX idx_teams_is_active  ON teams(is_active);
```

---

### 경기/좌석 도메인

```sql
-- 경기장
CREATE TABLE stadiums (
    id      BIGSERIAL    PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    address VARCHAR(200)
);

-- 구역 등급
CREATE TABLE zone_grades (
    id          BIGSERIAL   PRIMARY KEY,
    stadium_id  BIGINT      NOT NULL,
    name        VARCHAR(30) NOT NULL,   -- VIP | R | S | A | OUTFIELD
    CONSTRAINT fk_zg_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums(id)
);

-- 구역 (경기장 물리 구역)
CREATE TABLE sections (
    id             BIGSERIAL   PRIMARY KEY,
    stadium_id     BIGINT      NOT NULL,
    zone_grade_id  BIGINT      NOT NULL,
    name           VARCHAR(50),
    floor          VARCHAR(10),
    CONSTRAINT fk_sec_stadium FOREIGN KEY (stadium_id)    REFERENCES stadiums(id),
    CONSTRAINT fk_sec_zone    FOREIGN KEY (zone_grade_id) REFERENCES zone_grades(id)
);

-- 물리 좌석 (경기장에 고정된 좌석. 경기마다 재사용)
CREATE TABLE seats (
    id           BIGSERIAL   PRIMARY KEY,
    section_id   BIGINT      NOT NULL,
    row_number   VARCHAR(10),
    seat_number  VARCHAR(10),
    CONSTRAINT fk_seat_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT uq_seat         UNIQUE (section_id, row_number, seat_number)
);

-- 경기
CREATE TABLE games (
    id                   BIGSERIAL   PRIMARY KEY,
    stadium_id           BIGINT      NOT NULL,
    home_team_id         BIGINT,
    away_team_id         BIGINT,
    sport_type           VARCHAR(30),
    start_at             TIMESTAMP   NOT NULL,
    duration_minutes     INT         NOT NULL DEFAULT 180,
    status               VARCHAR(20) NOT NULL,  -- SCHEDULED | OPEN | IN_PROGRESS | FINISHED | CANCELLED
    day_type             VARCHAR(10),           -- WEEKDAY | WEEKEND | HOLIDAY
    game_grade           VARCHAR(20),           -- NORMAL | RIVAL
    is_rival_match       BOOLEAN     NOT NULL DEFAULT FALSE,
    max_ticket_per_user  INT         NOT NULL DEFAULT 4,
    total_seats          INT         NOT NULL DEFAULT 0,
    available_seats      INT         NOT NULL DEFAULT 0,
    sale_start_at        TIMESTAMP,
    sale_end_at          TIMESTAMP,
    created_at           TIMESTAMP   NOT NULL,
    deleted_at           TIMESTAMP,
    CONSTRAINT fk_game_stadium   FOREIGN KEY (stadium_id)    REFERENCES stadiums(id),
    CONSTRAINT fk_game_home_team FOREIGN KEY (home_team_id)  REFERENCES teams(id),
    CONSTRAINT fk_game_away_team FOREIGN KEY (away_team_id)  REFERENCES teams(id)
);

CREATE INDEX idx_games_status     ON games(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_games_start_at   ON games(start_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_games_sport_type ON games(sport_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_games_home_team  ON games(home_team_id);
CREATE INDEX idx_games_away_team  ON games(away_team_id);

-- 경기별 좌석 (경기마다 상태/가격이 달라짐)
CREATE TABLE game_seats (
    id             BIGSERIAL   PRIMARY KEY,
    game_id        BIGINT      NOT NULL,
    seat_id        BIGINT      NOT NULL,
    zone_grade_id  BIGINT,
    team_side      VARCHAR(10) NOT NULL DEFAULT 'NEUTRAL',  -- TEAM1 | TEAM2 | NEUTRAL
    price          INT         NOT NULL DEFAULT 0,
    seat_status    VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE | RESERVED | SOLD
    CONSTRAINT fk_gs_game      FOREIGN KEY (game_id)       REFERENCES games(id),
    CONSTRAINT fk_gs_seat      FOREIGN KEY (seat_id)       REFERENCES seats(id),
    CONSTRAINT fk_gs_zone      FOREIGN KEY (zone_grade_id) REFERENCES zone_grades(id),
    CONSTRAINT uq_game_seat    UNIQUE (game_id, seat_id)
);

CREATE INDEX idx_game_seats_game   ON game_seats(game_id);
CREATE INDEX idx_game_seats_status ON game_seats(game_id, seat_status);
```

---

### 예매/결제 도메인

```sql
-- 주문 (좌석 선점 + 결제의 컨테이너)
CREATE TABLE orders (
    id          BIGSERIAL   PRIMARY KEY,
    member_id   BIGINT,
    status      VARCHAR(20) NOT NULL,  -- PENDING | CONFIRMED | CANCELLED
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_order_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE INDEX idx_orders_member ON orders(member_id);

-- 주문 좌석 (주문 1건에 여러 좌석 가능)
CREATE TABLE order_seats (
    id            BIGSERIAL   PRIMARY KEY,
    order_id      BIGINT      NOT NULL,
    game_seat_id  BIGINT      NOT NULL,
    status        VARCHAR(30),              -- HOLDING | CONFIRMED | CANCELLED | EXPIRED
    expires_at    TIMESTAMP,               -- 선점 만료 시각 (15분)
    created_at    TIMESTAMP,
    CONSTRAINT fk_os_order     FOREIGN KEY (order_id)     REFERENCES orders(id),
    CONSTRAINT fk_os_game_seat FOREIGN KEY (game_seat_id) REFERENCES game_seats(id)
);

CREATE INDEX idx_order_seats_order    ON order_seats(order_id);
CREATE INDEX idx_order_seats_expires  ON order_seats(expires_at) WHERE status = 'HOLDING';

-- 티켓 (결제 완료 후 발급)
CREATE TABLE tickets (
    id              BIGSERIAL PRIMARY KEY,
    order_seat_id   BIGINT    NOT NULL,
    member_id       BIGINT    NOT NULL,
    ticket_number   VARCHAR(36) NOT NULL,  -- UUID
    price           INT       NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',  -- CONFIRMED | USED | CANCELLED
    issued_at       TIMESTAMP NOT NULL,
    used_at         TIMESTAMP,
    cancelled_at    TIMESTAMP,
    CONSTRAINT fk_ticket_order_seat FOREIGN KEY (order_seat_id) REFERENCES order_seats(id),
    CONSTRAINT fk_ticket_member     FOREIGN KEY (member_id)     REFERENCES members(id),
    CONSTRAINT uq_ticket_number     UNIQUE (ticket_number)
);

CREATE INDEX idx_tickets_member ON tickets(member_id);
CREATE INDEX idx_tickets_status ON tickets(member_id, status);

-- 결제
CREATE TABLE payments (
    id               BIGSERIAL    PRIMARY KEY,
    order_id         BIGINT       NOT NULL,
    member_id        BIGINT,
    payment_key      VARCHAR(200),          -- PG사 거래 ID
    idempotency_key  VARCHAR(100),          -- 중복 결제 방지 키
    method           VARCHAR(30),           -- CARD | KAKAO_PAY | TOSS_PAY
    total_amount     INT,
    discount_amount  INT          NOT NULL DEFAULT 0,
    final_amount     INT,
    status           VARCHAR(30),           -- PENDING | COMPLETED | REFUNDED | FAILED | CANCELLED
    requested_at     TIMESTAMP,
    approved_at      TIMESTAMP,
    failed_at        TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    CONSTRAINT fk_payment_order  FOREIGN KEY (order_id)  REFERENCES orders(id),
    CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uq_idempotency    UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_member ON payments(member_id);
CREATE INDEX idx_payments_status ON payments(status);

-- 환불
CREATE TABLE refunds (
    id             BIGSERIAL    PRIMARY KEY,
    payment_id     BIGINT       NOT NULL,
    refund_amount  INT,
    reason         VARCHAR(255),
    status         VARCHAR(30),             -- PENDING | COMPLETED | FAILED
    created_at     TIMESTAMP,
    completed_at   TIMESTAMP,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);
```

---

### 채팅 도메인

```sql
-- 채팅방
CREATE TABLE chat_rooms (
    id                BIGSERIAL   PRIMARY KEY,
    name              VARCHAR(100),
    type              VARCHAR(20) NOT NULL,  -- GAME | TEAM | PRIVATE | GROUP
    game_id           BIGINT,
    team_id           BIGINT,
    created_by        BIGINT,
    max_participants  INT         NOT NULL DEFAULT 5000,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT fk_chat_game    FOREIGN KEY (game_id)    REFERENCES games(id),
    CONSTRAINT fk_chat_team    FOREIGN KEY (team_id)    REFERENCES teams(id),
    CONSTRAINT fk_chat_creator FOREIGN KEY (created_by) REFERENCES members(id)
);

CREATE INDEX idx_chat_rooms_type    ON chat_rooms(type);
CREATE INDEX idx_chat_rooms_game    ON chat_rooms(game_id);
CREATE INDEX idx_chat_rooms_team    ON chat_rooms(team_id);

-- 채팅방 참여자
CREATE TABLE chat_participants (
    id             BIGSERIAL   PRIMARY KEY,
    room_id        BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'JOINED',  -- INVITED | JOINED | LEFT | KICKED
    notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_read_message_id BIGINT,
    joined_at      TIMESTAMP,
    left_at        TIMESTAMP,
    CONSTRAINT fk_cp_room   FOREIGN KEY (room_id)   REFERENCES chat_rooms(id),
    CONSTRAINT fk_cp_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uq_cp        UNIQUE (room_id, member_id)
);

CREATE INDEX idx_cp_room   ON chat_participants(room_id, status);
CREATE INDEX idx_cp_member ON chat_participants(member_id, status);

-- 채팅 메시지
CREATE TABLE chat_messages (
    id          BIGSERIAL    PRIMARY KEY,
    room_id     BIGINT       NOT NULL,
    sender_id   BIGINT       NOT NULL,
    content     TEXT,
    type        VARCHAR(20)  NOT NULL DEFAULT 'MESSAGE',  -- MESSAGE | CHEER | SYSTEM | FILE | IMAGE
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | DELETED
    is_filtered BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    deleted_at  TIMESTAMP,
    CONSTRAINT fk_msg_room   FOREIGN KEY (room_id)   REFERENCES chat_rooms(id),
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES members(id)
);

-- 채팅 메시지 순서 보장: created_at 인덱스 필수
CREATE INDEX idx_msg_room_time ON chat_messages(room_id, created_at DESC);
CREATE INDEX idx_msg_sender    ON chat_messages(sender_id);
```

---

### 알림 도메인

```sql
-- 알림 설정 (사용자별 ON/OFF)
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

-- 알림 채널 (EMAIL | MQTT | SLACK 등)
CREATE TABLE notification_channels (
    id              BIGSERIAL    PRIMARY KEY,
    member_id       BIGINT       NOT NULL,
    channel_type    VARCHAR(20)  NOT NULL,   -- EMAIL | MQTT | SLACK
    channel_target  VARCHAR(500) NOT NULL,   -- 이메일 주소 / 슬랙 웹훅 URL 등
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT fk_nc_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uq_nc        UNIQUE (member_id, channel_type)
);

CREATE INDEX idx_nc_member ON notification_channels(member_id);

-- 알림 이벤트 (발행 원장 — source of truth)
CREATE TABLE notification_events (
    id           BIGSERIAL    PRIMARY KEY,
    event_type   VARCHAR(50)  NOT NULL,   -- TICKET_OPEN | GAME_START | PAYMENT_COMPLETED | CHAT_MENTION
    payload      JSONB,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED | FAILED
    created_at   TIMESTAMP    NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_ne_status ON notification_events(status, created_at);

-- 사용자 인박스 알림
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT    NOT NULL,
    event_id   BIGINT    NOT NULL,
    is_read    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_noti_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_noti_event  FOREIGN KEY (event_id)  REFERENCES notification_events(id),
    CONSTRAINT uq_noti        UNIQUE (event_id, member_id)  -- 중복 알림 방지
);

CREATE INDEX idx_noti_member ON notifications(member_id, is_read, created_at DESC);

-- 알림 발송 이력
CREATE TABLE notification_history (
    id               BIGSERIAL   PRIMARY KEY,
    notification_id  BIGINT      NOT NULL,
    channel_type     VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,  -- SENT | FAILED
    error_message    TEXT,
    created_at       TIMESTAMP   NOT NULL,
    CONSTRAINT fk_nh_notification FOREIGN KEY (notification_id) REFERENCES notifications(id)
);

CREATE INDEX idx_nh_notification ON notification_history(notification_id);
```

---

## Redis Key 설계

### 3.1 회원 / 인증 도메인

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `auth:refresh:{memberId}` | String | 14일 | Refresh Token 저장 |
| `auth:blacklist:{accessToken}` | String | AccessToken 만료시간 | 로그아웃 토큰 블랙리스트 |
| `member:activity:{memberId}` | String | 24시간 | 마지막 활동 시간 |

### 3.2 채팅 도메인

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `chat:room:{roomId}` | Hash | 없음 | 채팅방 메타데이터 |
| `chat:room:{roomId}:members` | Set | 없음 | 채팅방 참여자 |
| `chat:room:{roomId}:online` | Set | 없음 | 현재 접속자 |
| `chat:user:{memberId}:rooms` | Set | 없음 | 사용자 참여 채팅방 |
| `chat:user:{memberId}:connection` | String | 5분 | WebSocket 연결 정보 |
| `chat:message:temp:{tempId}` | Hash | 1분 | 임시 메시지 (중복 방지) |
| `chat:last_read:{roomId}:{memberId}` | String | 없음 | 사용자 마지막 읽은 메시지 ID |

### 3.3 알림 도메인

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `notification:settings:{memberId}` | Hash | 1시간 | 알림 설정 캐시 |
| `notification:connected:{memberId}` | String | 없음 | MQTT 연결 상태 |
| `notification:alert:ticket_open` | Set | 없음 | 티켓 알림 ON 사용자 목록 |
| `notification:alert:game_start` | Set | 없음 | 경기 시작 알림 사용자 목록 |
| `notification:alert:payment` | Set | 없음 | 결제 알림 사용자 목록 |
| `notification:sent:{eventId}:{memberId}` | String | 24시간 | 중복 발송 방지 |

### 3.4 예매 도메인 (핵심 — 대기열/좌석)

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `reservation:queue:{gameId}` | Sorted Set | 없음 | 대기열 (score = 요청 타임스탬프) |
| `reservation:queue:{gameId}:uuid:{memberId}` | String | 30초 | SSE 진입 UUID 검증 |
| `reservation:queue:{gameId}:disconnect:{memberId}` | String | 30초 | 연결 끊김 시각 (재연결 유예) |
| `reservation:active:{gameId}` | Set | 없음 | 예매 화면 진입 사용자 |
| `reservation:active:{gameId}:uuid:{memberId}` | String | 15분 | 예매 세션 UUID (TTL=15분) |
| `reservation:seat:{gameId}:{seatId}` | String | 15분 | 좌석 선점 (SETNX) |
| `reservation:block:{memberId}:{gameId}` | String | 10분 | 재구매 제한 |

### 3.5 결제 도메인

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `payment:idempotency:{key}` | Hash | 24시간 | 멱등성 보장 |
| `payment:lock:{orderId}` | String | 30초 | 분산락 |
| `payment:status:{paymentId}` | String | 1시간 | 결제 상태 캐시 |

### 3.6 게임 도메인

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `game:list:{sportType}:{status}` | String(JSON) | 30초 | 경기 목록 캐시 |
| `game:detail:{gameId}` | Hash | 30초 | 경기 상세 캐시 |
| `game:seats:{gameId}:summary` | Hash | 10초 | 좌석 등급별 요약 |
| `game:schedule:{gameId}` | Hash | 1시간 | 예매 스케줄 |

### 3.7 팀 도메인

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `team:list` | String(JSON) | 5분 | 팀 목록 캐시 |
| `team:detail:{teamId}` | Hash | 5분 | 팀 상세 캐시 |
| `team:favorite:{memberId}` | List | 1시간 | 선호 팀 캐시 |

---

## Redis Streams (이벤트 버스)

| Stream Key | Producer | Consumer | 트리거 |
|------------|----------|----------|--------|
| `ticket.opened` | Ticketing | Notification | 경기 티켓 판매 오픈 시 |
| `payment.completed` | Payment | Notification | 결제 검증 완료 시 |
| `game.starting` | Ticketing (@Scheduled) | Notification | 경기 시작 1시간 전 |
| `chat.mentioned` | Chat | Notification | 채팅에서 @멘션 발생 시 |

> **Consumer Group** 방식 사용 → ACK 기반 안정적 소비, 재시도 보장  
> **Redis Streams vs Kafka**: 인프라 단순화를 위해 Redis Streams 채택. Kafka는 별도 운영 비용 발생.

---

## 설계 결정 사항

### D-1. game_seats 분리
물리 좌석(`seats`)과 경기별 좌석 상태/가격(`game_seats`)을 분리.  
같은 물리 좌석이 경기마다 다른 가격·상태를 가질 수 있음.

### D-2. tickets 테이블
`order_seats`에서 결제 완료 시 `tickets`를 발급. tickets는 사용자에게 전달되는 "실제 입장권"이며, UUID 기반 고유 번호를 가짐.

### D-3. notification_events 영속화
Redis Streams 발행 전에 반드시 DB에 저장 (source of truth).  
Redis 장애 시 DB를 기반으로 재처리 가능.

### D-4. chat_participants last_read_message_id
오프라인 메시지 읽음 처리는 DB의 `last_read_message_id`로 관리.  
온라인 중 실시간 읽음 처리는 Redis(`chat:last_read:{roomId}:{memberId}`)로 관리 → 주기적 또는 연결 종료 시 DB 동기화.

### D-5. Soft Delete
`games.deleted_at` — 논리 삭제. 예매 내역이 있는 경기는 물리 삭제 금지.  
조회 쿼리에는 `WHERE deleted_at IS NULL` 조건 항상 포함.

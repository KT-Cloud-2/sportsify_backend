-- ============================================================
-- Sortsify ERD Cloud용 DDL
-- V1 ~ V6 마이그레이션 최종 반영본
-- ERD Cloud > 새 ERD > "DDL 가져오기(Import DDL)" 에 전체 붙여넣기
-- ============================================================

-- ============================================================
-- [회원 도메인]
-- 서비스 이용자의 계정·인증·활동 로그를 관리한다.
-- 소셜 로그인(Google/Kakao) 전용이므로 자체 비밀번호가 없다.
-- 회원은 즐겨찾기 팀을 최대 N개 등록할 수 있으며,
-- 로그인·로그아웃 등 보안 이벤트는 activity_logs에 별도 기록된다.
-- ============================================================

-- 서비스에 가입한 사용자 계정. 소셜 로그인(Google/Kakao) 전용이므로 password 없음.
CREATE TABLE members
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 PK',
    email         VARCHAR(255) NOT NULL COMMENT '이메일',
    nickname      VARCHAR(50)           COMMENT '닉네임',
    provider      VARCHAR(20)  NOT NULL COMMENT '소셜 공급자 (GOOGLE | KAKAO)',
    provider_id   VARCHAR(255) NOT NULL COMMENT '소셜 공급자 발급 고유 ID',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'  COMMENT '계정 상태 (ACTIVE | INACTIVE | WITHDRAWN)',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER'    COMMENT '권한 (USER | ADMIN)',
    created_at    DATETIME     NOT NULL COMMENT '가입일시',
    updated_at    DATETIME              COMMENT '수정일시',
    last_login_at DATETIME              COMMENT '마지막 로그인 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_provider (provider, provider_id)
);

-- 회원이 즐겨찾기한 팀 목록. priority가 낮을수록 우선 노출.
CREATE TABLE member_favorite_teams
(
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '즐겨찾기 PK',
    member_id  BIGINT   NOT NULL COMMENT '회원 FK',
    team_id    BIGINT   NOT NULL COMMENT '팀 FK',
    priority   INT      NOT NULL DEFAULT 0 COMMENT '노출 우선순위 (낮을수록 상위)',
    created_at DATETIME NOT NULL COMMENT '등록일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_mft (member_id, team_id),
    FOREIGN KEY (member_id) REFERENCES members (id),
    FOREIGN KEY (team_id)   REFERENCES teams (id)
);

-- 로그인 성공/실패, 로그아웃 등 보안 감사용 로그. member_id는 로그인 실패 시 null 가능.
CREATE TABLE activity_logs
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '로그 PK',
    member_id  BIGINT               COMMENT '회원 FK (로그인 실패 시 null)',
    action     VARCHAR(30) NOT NULL COMMENT '행동 유형 (LOGIN_SUCCESS | LOGIN_FAIL | LOGOUT)',
    ip_address VARCHAR(45)          COMMENT 'IPv6 포함 45자',
    user_agent TEXT                 COMMENT '브라우저/앱 정보',
    created_at DATETIME    NOT NULL COMMENT '발생일시',
    PRIMARY KEY (id),
    FOREIGN KEY (member_id) REFERENCES members (id)
);

-- ============================================================
-- [팀 도메인]
-- 야구·축구·농구 등 스포츠 종목별 팀 마스터 데이터를 관리한다.
-- teams는 경기(games)의 홈팀·원정팀으로 참조되며,
-- 회원은 member_favorite_teams를 통해 관심 팀을 등록할 수 있다.
-- ============================================================

-- 야구·축구·농구 등 스포츠 팀 마스터 데이터.
CREATE TABLE teams
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '팀 PK',
    name       VARCHAR(100) NOT NULL COMMENT '팀 정식명칭',
    short_name VARCHAR(20)           COMMENT '약칭 (KIA, 삼성 등)',
    sport_type VARCHAR(30)  NOT NULL COMMENT '종목 (BASEBALL | FOOTBALL | BASKETBALL)',
    logo_url   VARCHAR(500)          COMMENT '로고 이미지 URL',
    is_active  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '노출 여부',
    created_at DATETIME     NOT NULL COMMENT '등록일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_team_name_sport (name, sport_type)
);

-- ============================================================
-- [경기/좌석 도메인]
-- 경기장 구조(stadium → zone_grade → section → seat)와
-- 경기 일정(games), 경기별 좌석 현황(game_seats), 가격 정책(price_policies)을 관리한다.
-- 좌석 계층: 경기장 > 구역등급(VIP/R/S/A) > 섹션(블록) > 개별좌석
-- game_seats.seat_status가 AVAILABLE → RESERVED → SOLD 순으로 변경되며,
-- 예매 선점 경쟁의 핵심 테이블이다.
-- ============================================================

-- 경기장 기본 정보.
CREATE TABLE stadiums
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '경기장 PK',
    name        VARCHAR(100) NOT NULL COMMENT '경기장명',
    address     VARCHAR(200)          COMMENT '주소',
    total_seats INT                   COMMENT '총 좌석 수',
    PRIMARY KEY (id)
);

-- 경기장 내 좌석 등급 구역 (VIP, R, S, A, 외야 등).
CREATE TABLE zone_grades
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '구역등급 PK',
    stadium_id BIGINT      NOT NULL COMMENT '경기장 FK',
    name       VARCHAR(30) NOT NULL COMMENT '등급명 (VIP | R | S | A | OUTFIELD)',
    PRIMARY KEY (id),
    FOREIGN KEY (stadium_id) REFERENCES stadiums (id)
);

-- 구역(블록) 단위 좌석 구획. 한 경기장에 여러 섹션.
CREATE TABLE sections
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '섹션 PK',
    stadium_id    BIGINT      NOT NULL COMMENT '경기장 FK',
    zone_grade_id BIGINT      NOT NULL COMMENT '구역등급 FK',
    name          VARCHAR(50)          COMMENT '섹션명 (1루 내야 1 등)',
    floor         VARCHAR(10)          COMMENT '층 정보',
    PRIMARY KEY (id),
    FOREIGN KEY (stadium_id)    REFERENCES stadiums (id),
    FOREIGN KEY (zone_grade_id) REFERENCES zone_grades (id)
);

-- 개별 좌석. (섹션, 줄번호, 좌석번호) 조합으로 유일하게 식별.
CREATE TABLE seats
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '좌석 PK',
    section_id    BIGINT      NOT NULL COMMENT '섹션 FK',
    zone_grade_id BIGINT      NOT NULL COMMENT '구역등급 FK',
    row_number    VARCHAR(10)          COMMENT '줄번호',
    seat_number   VARCHAR(10)          COMMENT '좌석번호',
    PRIMARY KEY (id),
    UNIQUE KEY uq_seat (section_id, row_number, seat_number),
    FOREIGN KEY (section_id)    REFERENCES sections (id),
    FOREIGN KEY (zone_grade_id) REFERENCES zone_grades (id)
);

-- 경기 일정. sale_start_at ~ sale_end_at 기간 동안 예매 가능.
CREATE TABLE games
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '경기 PK',
    stadium_id          BIGINT      NOT NULL COMMENT '경기장 FK',
    home_team_id        BIGINT               COMMENT '홈팀 FK',
    away_team_id        BIGINT               COMMENT '원정팀 FK',
    sport_type          VARCHAR(30)          COMMENT '종목',
    start_at            DATETIME    NOT NULL COMMENT '경기 시작일시',
    duration_minutes    INT         NOT NULL DEFAULT 180 COMMENT '예상 경기시간(분)',
    status              VARCHAR(20) NOT NULL COMMENT '경기 상태 (SCHEDULED | ON_SALE | SALE_CLOSED | IN_PROGRESS | FINISHED | CANCELLED)',
    day_type            VARCHAR(10)          COMMENT '요일 유형 (WEEKDAY | WEEKEND | HOLIDAY)',
    game_grade          VARCHAR(20)          COMMENT '경기 등급 (NORMAL | RIVAL)',
    max_ticket_per_user INT         NOT NULL DEFAULT 4 COMMENT '1인 최대 예매 수',
    sale_start_at       DATETIME             COMMENT '예매 시작일시',
    sale_end_at         DATETIME             COMMENT '예매 종료일시',
    created_at          DATETIME    NOT NULL COMMENT '등록일시',
    deleted_at          DATETIME             COMMENT '소프트삭제 일시',
    PRIMARY KEY (id),
    FOREIGN KEY (stadium_id)   REFERENCES stadiums (id),
    FOREIGN KEY (home_team_id) REFERENCES teams (id),
    FOREIGN KEY (away_team_id) REFERENCES teams (id)
);

-- 경기별 좌석 현황. 실시간 예매 시 seat_status를 RESERVED로 변경해 선점.
CREATE TABLE game_seats
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '경기좌석 PK',
    game_id     BIGINT      NOT NULL COMMENT '경기 FK',
    seat_id     BIGINT      NOT NULL COMMENT '좌석 FK',
    seat_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '좌석 상태 (AVAILABLE | RESERVED | SOLD)',
    price       INT         NOT NULL COMMENT '해당 경기 좌석 가격',
    PRIMARY KEY (id),
    UNIQUE KEY uq_game_seat (game_id, seat_id),
    FOREIGN KEY (game_id) REFERENCES games (id),
    FOREIGN KEY (seat_id) REFERENCES seats (id)
);

-- 구역등급·요일유형·경기등급 조합으로 가격을 결정하는 정책 테이블.
CREATE TABLE price_policies
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '가격정책 PK',
    stadium_id    BIGINT      NOT NULL COMMENT '경기장 FK',
    day_type      VARCHAR(10) NOT NULL COMMENT '요일 유형 (WEEKDAY | WEEKEND | HOLIDAY)',
    zone_grade_id BIGINT      NOT NULL COMMENT '구역등급 FK',
    game_grade    VARCHAR(20) NOT NULL COMMENT '경기 등급 (REGULAR | PLAYOFF | FINAL)',
    price         INT         NOT NULL COMMENT '책정 가격',
    PRIMARY KEY (id),
    UNIQUE KEY uq_price_policy (stadium_id, day_type, zone_grade_id, game_grade),
    FOREIGN KEY (stadium_id)    REFERENCES stadiums (id),
    FOREIGN KEY (zone_grade_id) REFERENCES zone_grades (id)
);

-- ============================================================
-- [예매/결제 도메인]
-- 좌석 선점부터 티켓 발급, 결제, 환불까지의 전체 흐름을 관리한다.
-- orders: 선점 컨테이너 (15분 내 미결제 시 자동 만료)
-- order_seats: 주문에 속한 개별 좌석 (1주문 N좌석)
-- tickets: 결제 완료 후 발급되는 실물 티켓 (UUID 식별)
-- payments: PG사(Toss) 연동 결제 원장. toss_order_id(Toss용)와 order_id(내부 FK) 분리
-- refunds: 환불 이력 (payments와 1:1 또는 부분환불 고려)
-- ============================================================

-- 좌석 선점과 결제의 컨테이너. expires_at까지 결제가 안 되면 PENDING → CANCELLED.
CREATE TABLE orders
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '주문 PK',
    member_id  BIGINT               COMMENT '회원 FK',
    status     VARCHAR(20) NOT NULL COMMENT '주문 상태 (PENDING | PAYING | CONFIRMED | CANCELLED)',
    expires_at DATETIME             COMMENT '선점 만료일시 (15분)',
    created_at DATETIME    NOT NULL COMMENT '생성일시',
    updated_at DATETIME             COMMENT '수정일시',
    PRIMARY KEY (id),
    FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 주문 1건에 속하는 개별 좌석. 선점 만료 시 EXPIRED로 변경.
CREATE TABLE order_seats
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '주문좌석 PK',
    order_id     BIGINT      NOT NULL COMMENT '주문 FK',
    game_seat_id BIGINT      NOT NULL COMMENT '경기좌석 FK',
    status       VARCHAR(30) NOT NULL COMMENT '상태 (HOLDING | CONFIRMED | CANCELLED | EXPIRED)',
    price        INT         NOT NULL COMMENT '결제 시점 가격 스냅샷',
    created_at   DATETIME             COMMENT '생성일시',
    PRIMARY KEY (id),
    FOREIGN KEY (order_id)     REFERENCES orders (id),
    FOREIGN KEY (game_seat_id) REFERENCES game_seats (id)
);

-- 결제 완료 후 발급되는 실물 티켓. ticket_number(UUID)가 입장 식별자.
CREATE TABLE tickets
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '티켓 PK',
    order_seat_id BIGINT      NOT NULL COMMENT '주문좌석 FK',
    member_id     BIGINT      NOT NULL COMMENT '소유자 FK',
    ticket_number VARCHAR(36) NOT NULL COMMENT '티켓 UUID (입장 식별자)',
    price         INT         NOT NULL COMMENT '최종 결제 금액',
    status        VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' COMMENT '상태 (CONFIRMED | USED | CANCELLED)',
    issued_at     DATETIME    NOT NULL COMMENT '발급일시',
    used_at       DATETIME             COMMENT '사용일시',
    cancelled_at  DATETIME             COMMENT '취소일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_ticket_number (ticket_number),
    FOREIGN KEY (order_seat_id) REFERENCES order_seats (id),
    FOREIGN KEY (member_id)     REFERENCES members (id)
);

-- PG사(Toss)와의 결제 원장. toss_order_id는 Toss 전용 주문번호, order_id는 내부 주문 FK.
CREATE TABLE payments
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '결제 PK',
    order_id        BIGINT                COMMENT '주문 FK (내부 orders.id)',
    toss_order_id   VARCHAR(50)           COMMENT 'Toss 전용 주문번호 (구 order_id)',
    member_id       BIGINT                COMMENT '결제 회원 FK',
    user_id         BIGINT                COMMENT '결제 요청 사용자 FK (엔티티 정합용)',
    match_id        BIGINT                COMMENT '경기 FK (비정규화 참조)',
    seat_id         BIGINT                COMMENT '좌석 FK (비정규화 참조)',
    payment_key     VARCHAR(200)          COMMENT 'PG사 거래 고유 ID',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '중복 결제 방지 키',
    payment_method  VARCHAR(30)  NOT NULL COMMENT '결제 수단 (CARD | KAKAO_PAY | TOSS_PAY)',
    amount          BIGINT       NOT NULL COMMENT '결제 금액',
    status          VARCHAR(30)  NOT NULL COMMENT '상태 (PENDING | COMPLETED | REFUNDED | FAILED | CANCELLED)',
    cancel_reason   VARCHAR(255)          COMMENT '취소 사유',
    requested_at    DATETIME     NOT NULL COMMENT '결제 요청일시',
    approved_at     DATETIME              COMMENT '승인일시',
    failed_at       DATETIME              COMMENT '실패일시',
    canceled_at     DATETIME              COMMENT '취소일시',
    created_at      DATETIME     NOT NULL COMMENT '생성일시',
    updated_at      DATETIME     NOT NULL COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_payment_key (payment_key),
    UNIQUE KEY uq_idempotency (idempotency_key),
    UNIQUE KEY uq_payment_order_id (toss_order_id),
    UNIQUE KEY uk_payments_order_id_long (order_id),
    FOREIGN KEY (order_id)  REFERENCES orders (id),
    FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 결제 취소/환불 이력.
CREATE TABLE refunds
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '환불 PK',
    payment_id    BIGINT      NOT NULL COMMENT '결제 FK',
    refund_amount INT                  COMMENT '환불 금액',
    reason        VARCHAR(255)         COMMENT '환불 사유',
    status        VARCHAR(30)          COMMENT '상태 (PENDING | COMPLETED | FAILED)',
    created_at    DATETIME             COMMENT '환불 요청일시',
    completed_at  DATETIME             COMMENT '환불 완료일시',
    PRIMARY KEY (id),
    FOREIGN KEY (payment_id) REFERENCES payments (id)
);

-- ============================================================
-- [채팅 도메인]
-- 경기 연동 단체 채팅(GAME)과 1:1 직접 채팅(DIRECT)을 지원한다.
-- chat_rooms: 방 메타 정보. GAME 타입은 games.id를 참조.
-- chat_messages: 실제 메시지 내용. SYSTEM 타입은 입장/퇴장 알림 등 시스템 메시지.
-- chat_room_members: 참여자별 읽음 위치(last_read_message_id)를 관리해
--   안 읽은 메시지 카운트 계산의 기준으로 사용.
-- ============================================================

-- 채팅방. GAME 타입은 특정 경기에 연결되고, DIRECT는 1:1 채팅.
CREATE TABLE chat_rooms
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '채팅방 PK',
    name       VARCHAR(100) NOT NULL COMMENT '방 이름',
    type       VARCHAR(20)  NOT NULL COMMENT '방 유형 (GAME | DIRECT)',
    image_url  TEXT                  COMMENT '방 대표 이미지',
    game_id    BIGINT                COMMENT '연결 경기 FK (GAME 타입만 사용)',
    created_by BIGINT       NOT NULL COMMENT '개설자 FK',
    created_at DATETIME     NOT NULL COMMENT '생성일시',
    updated_at DATETIME     NOT NULL COMMENT '수정일시',
    status     VARCHAR(20)  NOT NULL COMMENT '상태 (ACTIVE | ARCHIVED | DELETED)',
    PRIMARY KEY (id),
    FOREIGN KEY (game_id)    REFERENCES games (id),
    FOREIGN KEY (created_by) REFERENCES members (id)
);

-- 채팅 메시지. SYSTEM 타입은 입장/퇴장 알림 등 시스템 메시지. sender_id는 SYSTEM 메시지 시 null.
CREATE TABLE chat_messages
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '메시지 PK',
    room_id    BIGINT      NOT NULL COMMENT '채팅방 FK',
    sender_id  BIGINT               COMMENT '발송자 FK (SYSTEM 메시지는 null)',
    content    TEXT                 COMMENT '메시지 내용',
    type       VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '메시지 유형 (TEXT | IMAGE | FILE | SYSTEM)',
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE | DELETED)',
    created_at DATETIME    NOT NULL COMMENT '발송일시',
    PRIMARY KEY (id),
    FOREIGN KEY (room_id)   REFERENCES chat_rooms (id),
    FOREIGN KEY (sender_id) REFERENCES members (id)
);

-- 채팅방 참여자. last_read_message_id로 읽지 않은 메시지 카운트를 계산.
CREATE TABLE chat_room_members
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '참여자 PK',
    room_id              BIGINT      NOT NULL COMMENT '채팅방 FK',
    member_id            BIGINT      NOT NULL COMMENT '참여 회원 FK',
    status               VARCHAR(20) NOT NULL DEFAULT 'JOINED' COMMENT '상태 (INVITED | JOINED | LEFT | BANNED)',
    notification_enabled TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '알림 수신 여부',
    last_read_message_id BIGINT               COMMENT '마지막 읽은 메시지 FK',
    joined_at            DATETIME    NOT NULL COMMENT '입장일시',
    updated_at           DATETIME    NOT NULL COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_cp (room_id, member_id),
    FOREIGN KEY (room_id)              REFERENCES chat_rooms (id),
    FOREIGN KEY (member_id)            REFERENCES members (id),
    FOREIGN KEY (last_read_message_id) REFERENCES chat_messages (id)
);

-- ============================================================
-- [알림 도메인]
-- Redis Stream 기반 비동기 알림 파이프라인의 DB 레이어를 담당한다.
-- notification_settings: 회원별 알림 유형 ON/OFF (1회원 1행)
-- notification_channels: 회원이 등록한 외부 발송 채널 (EMAIL/MQTT/SLACK, 유형당 1개 제한)
-- notification_events: Redis에서 수신한 이벤트 원장.
--   scheduled_at이 있으면 예약 발송, null이면 즉시 발송.
--   stream_message_id로 PEL 재처리 시 멱등성 보장.
-- notifications: 회원별 인박스 알림 (event_id + member_id UNIQUE로 중복 방지)
-- notification_history: 채널 발송 결과 이력 (최대 3회 재시도 후 최종 SENT/FAILED 기록)
-- ============================================================

-- 회원별 알림 ON/OFF 설정. 회원 1명당 1행 (UNIQUE).
CREATE TABLE notification_settings
(
    id                 BIGINT     NOT NULL AUTO_INCREMENT COMMENT '설정 PK',
    member_id          BIGINT     NOT NULL COMMENT '회원 FK',
    ticket_open_alert  TINYINT(1) NOT NULL DEFAULT 1 COMMENT '티켓 오픈 알림 여부',
    game_start_alert   TINYINT(1) NOT NULL DEFAULT 1 COMMENT '경기 시작 알림 여부',
    payment_alert      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '결제 완료 알림 여부',
    chat_mention_alert TINYINT(1) NOT NULL DEFAULT 1 COMMENT '채팅 멘션 알림 여부',
    updated_at         DATETIME            COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_ns_member (member_id),
    FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 회원이 등록한 외부 발송 채널 (이메일, MQTT 등). 채널 타입별 1개 제한.
CREATE TABLE notification_channels
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '채널 PK',
    member_id      BIGINT       NOT NULL COMMENT '회원 FK',
    channel_type   VARCHAR(20)  NOT NULL COMMENT '채널 유형 (EMAIL | MQTT | SLACK)',
    channel_target VARCHAR(500) NOT NULL COMMENT '발송 대상 (이메일 주소 | 웹훅 URL 등)',
    is_enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '활성 여부',
    created_at     DATETIME     NOT NULL COMMENT '등록일시',
    updated_at     DATETIME              COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_nc (member_id, channel_type),
    FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 알림 이벤트 원장. Redis 장애 시 재처리 근거. scheduled_at이 있으면 예약 발송.
-- stream_message_id: Redis Stream 메시지 ID (PEL 재처리 멱등성 보장).
CREATE TABLE notification_events
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '이벤트 PK',
    event_type        VARCHAR(50) NOT NULL COMMENT '이벤트 유형 (TICKET_OPEN | GAME_START | PAYMENT_COMPLETED | CHAT_MENTION)',
    payload           JSON                 COMMENT '이벤트 상세 데이터 (JSON)',
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태 (PENDING | PROCESSING | PUBLISHED | FAILED | CANCELLED)',
    stream_message_id VARCHAR(100)         COMMENT 'Redis Stream 메시지 ID (재처리 멱등성)',
    created_at        DATETIME    NOT NULL COMMENT '생성일시',
    updated_at        DATETIME             COMMENT '상태 변경일시',
    published_at      DATETIME             COMMENT '발행 완료일시',
    scheduled_at      DATETIME             COMMENT '예약 발송 시각 (null = 즉시 발송)',
    PRIMARY KEY (id),
    UNIQUE KEY uq_ne_stream_message_id (stream_message_id)
);

-- 회원별 알림 인박스. event_id + member_id UNIQUE로 중복 수신 방지.
CREATE TABLE notifications
(
    id         BIGINT     NOT NULL AUTO_INCREMENT COMMENT '인박스 알림 PK',
    member_id  BIGINT     NOT NULL COMMENT '수신 회원 FK',
    event_id   BIGINT     NOT NULL COMMENT '알림 이벤트 FK',
    is_read    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '읽음 여부',
    created_at DATETIME   NOT NULL COMMENT '수신일시',
    PRIMARY KEY (id),
    UNIQUE KEY uq_noti (event_id, member_id),
    FOREIGN KEY (member_id) REFERENCES members (id),
    FOREIGN KEY (event_id)  REFERENCES notification_events (id)
);

-- 채널별 발송 결과 이력. 재시도 결과를 포함해 SENT/FAILED 단 1건 기록.
CREATE TABLE notification_history
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '발송이력 PK',
    notification_id BIGINT      NOT NULL COMMENT '인박스 알림 FK',
    channel_type    VARCHAR(20) NOT NULL COMMENT '발송 채널 (EMAIL | MQTT | SLACK)',
    status          VARCHAR(20) NOT NULL COMMENT '발송 결과 (SENT | FAILED)',
    error_message   TEXT                 COMMENT '실패 사유 (최대 3회 재시도 후 마지막 에러)',
    created_at      DATETIME    NOT NULL COMMENT '기록일시',
    PRIMARY KEY (id),
    FOREIGN KEY (notification_id) REFERENCES notifications (id)
);

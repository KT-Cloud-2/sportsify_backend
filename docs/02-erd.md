# ERD (Entity Relationship Diagram)

> 이 문서는 각 도메인의 핵심 엔티티와 관계를 정의한다.  
> 상세 DDL 및 마이그레이션 스크립트는 별도 관리한다.

---

## 전체 ERD 다이어그램

## 도메인별 핵심 엔티티

---

### Member 도메인

```
members
├── id          BIGSERIAL PK                    -- 회원 고유 ID (자동 증가)
├── email       VARCHAR(255) UNIQUE NOT NULL    -- 로그인용 이메일 (중복 불가)
├── nickname    VARCHAR(50) NOT NULL            -- 서비스 내 표시 이름
├── created_at  TIMESTAMP NOT NULL              -- 가입 시각
└── updated_at  TIMESTAMP NOT NULL              -- 정보 수정 시각

member_favorite_teams
├── id          BIGSERIAL PK                -- 고유 ID
├── member_id   BIGINT FK → members.id      -- 어떤 회원의 선호인지
├── team_id     BIGINT FK → teams.id        -- 어떤 팀을 좋아하는지
├── priority    INTEGER DEFAULT 1           -- 선호 순위 (1=최애, 2=차애...)
└── created_at  TIMESTAMP NOT NULL          -- 등록 시각

teams
├── id          BIGSERIAL PK                -- 팀 고유 ID (자동 증가)
├── name        VARCHAR(100) NOT NULL       -- 팀 정식 명칭
├── sport_type  VARCHAR(30) NOT NULL        -- 종목 구분
├── short_name  VARCHAR(20)                 -- 팀 약칭 (UI 표시용)
├── is_active   BOOLEAN DEFAULT true        -- 활동 중인 팀 여부
└── created_at  TIMESTAMP NOT NULL          -- 데이터 생성 시각

UNIQUE(member_id, team_id)                  -- 같은 팀 중복 등록 방지
```

---

### Ticketing 도메인

```

seats
├── id           BIGSERIAL PK               -- 좌석 고유 ID
├── game_id      BIGINT FK → games.id       -- 어떤 경기 좌석인지
├── grade        VARCHAR(20) NOT NULL       -- 좌석 등급 (VIP | R석 | S석 | A석 | 외야)
├── section      VARCHAR(50)                -- 구역 위치
├── row_number   VARCHAR(10)                -- 열 번호
├── seat_number  INTEGER                    -- 좌석 번호
├── price        INTEGER NOT NULL           -- 가격 (원)
└── status       VARCHAR(20) NOT NULL       -- 좌석 상태 (AVAILABLE | RESERVED | SOLD)

tickets
├── id            BIGSERIAL PK              -- 티켓 고유 ID
├── game_id       BIGINT FK → games.id      -- 어떤 경기
├── seat_id       BIGINT FK → seats.id      -- 어떤 좌석
├── member_id     BIGINT FK → members.id    -- 누가 예매했는지
├── buyer_name    VARCHAR(50) NOT NULL      -- 구매자 이름
├── buyer_phone   VARCHAR(20) NOT NULL      -- 구매자 연락처
├── status        VARCHAR(20) NOT NULL      -- 티켓 상태 (PENDING | CONFIRMED | CANCELLED | TRANSFERRED)
├── reserved_at   TIMESTAMP                 -- 좌석 선점 시각
├── confirmed_at  TIMESTAMP                 -- 결제 완료 시각
└── cancelled_at  TIMESTAMP                 -- 취소 시각


games
├── id               BIGSERIAL PK               -- 경기 고유 ID (자동 증가)
├── sport_type       VARCHAR(30) NOT NULL       -- 종목
├── team1_id         BIGINT FK → teams.id       -- 첫 번째 팀
├── team2_id         BIGINT FK → teams.id       -- 두 번째 팀
├── team1_is_home    BOOLEAN                    -- team1이 홈팀인지
├── game_time        TIMESTAMP NOT NULL         -- 경기 시작 시각
├── venue            VARCHAR(100) NOT NULL      -- 경기장 장소
├── status           VARCHAR(20) NOT NULL       -- 경기 상태
├── total_seats      INTEGER NOT NULL           -- 전체 좌석 수
├── available_seats  INTEGER NOT NULL           -- 남은 좌석 수
└── is_rival_match   BOOLEAN DEFAULT false      -- 라이벌전 여부
```

---

### Payment 도메인

```
payments
├── id                 BIGSERIAL PK                    -- 결제 고유 ID
├── ticket_id          BIGINT FK → tickets.id          -- 어떤 티켓 결제인지
├── member_id          BIGINT FK → members.id          -- 누가 결제했는지
├── amount             INTEGER NOT NULL                -- 원래 금액
├── discount_amount    INTEGER DEFAULT 0               -- 할인 금액
├── final_amount       INTEGER NOT NULL                -- 최종 결제 금액
├── method             VARCHAR(20) NOT NULL            -- 결제 수단 (CARD | KAKAO_PAY | TOSS_PAY)
├── status             VARCHAR(20) NOT NULL            -- 결제 상태 (PENDING | COMPLETED | FAILED | REFUNDED)
├── pg_transaction_id  VARCHAR(100)                    -- PG사 거래 ID
├── idempotency_key    VARCHAR(100) UNIQUE NOT NULL    -- 중복 결제 방지 키
├── paid_at            TIMESTAMP                       -- 결제 완료 시각
└── refunded_at        TIMESTAMP                       -- 환불 시각
```

---

### Chat 도메인

```
chat_rooms
├── id                   BIGSERIAL PK              -- 채팅방 고유 ID
├── type                 VARCHAR(20) NOT NULL      -- 채팅방 종류 (GAME | TEAM | GENERAL)
├── game_id              BIGINT FK → games.id      -- 경기 연결 (GAME 타입)
├── team_id              BIGINT FK → teams.id      -- 팀 연결 (TEAM 타입)
├── max_participants     INTEGER                   -- 최대 참여 인원
├── current_participants INTEGER DEFAULT 0         -- 현재 참여 인원
└── created_at           TIMESTAMP NOT NULL        -- 생성 시각

chat_messages
├── id          BIGSERIAL PK                    -- 메시지 고유 ID
├── room_id     BIGINT FK → chat_rooms.id       -- 어느 채팅방 메시지인지
├── sender_id   BIGINT FK → members.id          -- 누가 보냈는지
├── content     VARCHAR(80) NOT NULL            -- 메시지 내용
├── type        VARCHAR(20) NOT NULL            -- 메시지 종류 (MESSAGE | CHEER | SYSTEM)
├── is_filtered BOOLEAN DEFAULT false           -- 필터링 여부
└── created_at  TIMESTAMP NOT NULL              -- 전송 시각

chat_warnings
├── id          BIGSERIAL PK                    -- 고유 ID
├── member_id   BIGINT FK → members.id          -- 경고받은 회원
├── room_id     BIGINT FK → chat_rooms.id       -- 어느 채팅방에서
├── reason      VARCHAR(200)                    -- 경고 사유
└── warned_at   TIMESTAMP NOT NULL              -- 경고 시각
```

---

### Notification 도메인

```
notification_settings
├── id                  BIGSERIAL PK                   -- 고유 ID
├── member_id           BIGINT FK → members.id UNIQUE  -- 어떤 회원 설정인지 (1:1)
├── ticket_open_alert   BOOLEAN DEFAULT true           -- 티켓 오픈 알림 ON/OFF
├── game_start_alert    BOOLEAN DEFAULT true           -- 경기 시작 알림 ON/OFF
├── payment_alert       BOOLEAN DEFAULT true           -- 결제 관련 알림 ON/OFF
└── created_at          TIMESTAMP NOT NULL             -- 설정 생성 시각

notification_channels
├── id              BIGSERIAL PK                -- 고유 ID
├── member_id       BIGINT FK → members.id      -- 어떤 회원의 채널인지
├── channel         VARCHAR(20) NOT NULL        -- 채널 종류 (EMAIL | SMS | DISCORD | PUSH)
├── is_enabled      BOOLEAN DEFAULT true        -- 이 채널 사용 여부
└── channel_target  VARCHAR(200)                -- 수신 대상 (이메일, 번호, 웹훅 URL 등)

UNIQUE(member_id, channel)                      -- 회원당 채널 종류별 1개

notification_histories
├── id          BIGSERIAL PK                    -- 이력 고유 ID
├── member_id   BIGINT FK → members.id          -- 누구에게 보냈는지
├── channel     VARCHAR(20) NOT NULL            -- 어떤 채널로 보냈는지
├── type        VARCHAR(50) NOT NULL            -- 알림 종류
├── title       VARCHAR(200)                    -- 알림 제목
├── content     TEXT                            -- 알림 내용
├── status      VARCHAR(20) NOT NULL            -- 발송 결과 (SENT | FAILED)
├── sent_at     TIMESTAMP                       -- 실제 발송 시각
└── created_at  TIMESTAMP NOT NULL              -- 이력 생성 시각
```

---

## 주요 관계 요약

```
teams           1 ── N  games (team1_id)
teams           1 ── N  games (team2_id)
teams           1 ── N  member_favorite_teams
teams           1 ── N  chat_rooms (TEAM 타입)

members         1 ── N  member_favorite_teams
members         1 ── 1  notification_settings
members         1 ── N  notification_channels
members         1 ── N  notification_histories
members         1 ── N  tickets
members         1 ── N  payments

games           1 ── N  seats
games           1 ── N  tickets
games           1 ── N  chat_rooms (GAME 타입)

seats           1 ── 1  tickets
tickets         1 ── 1  payments
chat_rooms      1 ── N  chat_messages
```

---

## Redis 저장 구조 (비관계형)

| Key 패턴 | 타입 | 용도 | TTL |
|---------|------|------|-----|
| `seat:lock:{seatId}` | String | 좌석 선점 분산 락 | 10분 |
| `reservation:{ticketId}` | String | 좌석 결제 대기 | 10분 |
| `queue:{gameId}` | Sorted Set | 티켓 대기열 (score = timestamp) | 경기 종료 후 삭제 |
| `chat:intensity:{gameId}` | Sorted Set | 채팅 열기 측정 | 경기 종료 후 삭제 |
| `cache:game:{gameId}` | String (JSON) | 경기 정보 캐시 | 5분 |

---

## 미결 사항

- [ ] 채팅 이력 저장소: PostgreSQL 통합 vs MongoDB 분리
- [ ] 좌석 정보 캐시 전략: L1(Caffeine) + L2(Redis) 3단 구조 적용 범위
- [ ] 이벤트 소싱 적용 여부 (티켓 예매 전 과정 이벤트 로그)

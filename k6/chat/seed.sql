-- ── k6 부하 테스트 시드 데이터 ──────────────────────────────────
-- 실행: psql $DATABASE_URL -f k6/seed.sql
-- 정리: psql $DATABASE_URL -f k6/seed_cleanup.sql
--
-- 스크립트별 범위  (N = run.sh 에서 -v 로 주입되는 psql 변수)
--   constant.js  : members 30000~(30000+N-1) / rooms 9001~9020  (N = :constant_vus)
--   ramping.js   : members 20000~(20000+N-1) / rooms 9021~9040  (N = :ramping_vus)
--   broadcast.js : members 40000~(40000+N-1) / rooms 9041~9045  (N = :broadcast_vus)
--     senders   : 40000~40004 (방당 1명)
--     receivers : 40005~(40000+N-1) (5개 방 라운드로빈)
-- ─────────────────────────────────────────────────────────────────

-- ══ constant.js ══════════════════════════════════════════════════

-- 1-A. Members (30000 ~ 30000+:constant_vus-1)
INSERT INTO members (id, email, nickname, provider, provider_id, status, role, created_at)
SELECT
    30000 + i,
    'k6-' || (30000 + i) || '@test.com',
    'k6-' || (30000 + i),
    'KAKAO',
    'k6-provider-' || (30000 + i),
    'ACTIVE',
    'USER',
    NOW()
FROM generate_series(0, :constant_vus - 1) AS i
ON CONFLICT (id) DO NOTHING;

-- 1-B. Chat rooms (9001 ~ 9020) — GAME, ACTIVE
-- game_id=NULL: fk_chat_game FK가 살아있는 환경에서도 안전하게 삽입 (game_id nullable)
INSERT INTO chat_rooms (id, name, type, game_id, created_at, updated_at, status, created_by)
OVERRIDING SYSTEM VALUE
SELECT
    9001 + i,
    'k6-constant-' || i,
    'GAME',
    NULL,
    NOW(),
    NOW(),
    'ACTIVE',
    30000 + i
FROM generate_series(0, 19) AS i
ON CONFLICT (id) DO NOTHING;

-- 1-C. Chat room members — 멤버 i → 방 (9001 + i % 20)
INSERT INTO chat_room_members (room_id, member_id, status, notification_enabled, joined_at, updated_at)
SELECT
    9001 + (i % 20),
    30000 + i,
    'JOINED',
    true,
    NOW(),
    NOW()
FROM generate_series(0, :constant_vus - 1) AS i
ON CONFLICT ON CONSTRAINT uq_cp DO NOTHING;

-- ══ ramping.js ═══════════════════════════════════════════════════

-- 2-A. Members (20000 ~ 20000+:ramping_vus-1)
INSERT INTO members (id, email, nickname, provider, provider_id, status, role, created_at)
SELECT
    20000 + i,
    'k6-' || (20000 + i) || '@test.com',
    'k6-' || (20000 + i),
    'KAKAO',
    'k6-provider-' || (20000 + i),
    'ACTIVE',
    'USER',
    NOW()
FROM generate_series(0, :ramping_vus - 1) AS i
ON CONFLICT (id) DO NOTHING;

-- 2-B. Chat rooms (9021 ~ 9040) — GAME, ACTIVE
-- game_id=NULL: fk_chat_game FK가 살아있는 환경에서도 안전하게 삽입 (game_id nullable)
INSERT INTO chat_rooms (id, name, type, game_id, created_at, updated_at, status, created_by)
OVERRIDING SYSTEM VALUE
SELECT
    9021 + i,
    'k6-ramping-' || i,
    'GAME',
    NULL,
    NOW(),
    NOW(),
    'ACTIVE',
    20000 + i
FROM generate_series(0, 19) AS i
ON CONFLICT (id) DO NOTHING;

-- 2-C. Chat room members — 멤버 i → 방 (9021 + i % 20)
INSERT INTO chat_room_members (room_id, member_id, status, notification_enabled, joined_at, updated_at)
SELECT
    9021 + (i % 20),
    20000 + i,
    'JOINED',
    true,
    NOW(),
    NOW()
FROM generate_series(0, :ramping_vus - 1) AS i
ON CONFLICT ON CONSTRAINT uq_cp DO NOTHING;

-- ══ broadcast.js ═════════════════════════════════════════════════
-- :broadcast_vus 변수로 VU 수 결정 (run.sh 가 -v broadcast_vus=N 으로 주입)

-- 3-A. Members — senders (40000~40004) + receivers (40005~40000+:broadcast_vus-1)
INSERT INTO members (id, email, nickname, provider, provider_id, status, role, created_at)
SELECT
    40000 + i,
    'k6-' || (40000 + i) || '@test.com',
    'k6-' || (40000 + i),
    'KAKAO',
    'k6-provider-' || (40000 + i),
    'ACTIVE',
    'USER',
    NOW()
FROM generate_series(0, :broadcast_vus - 1) AS i
ON CONFLICT (id) DO NOTHING;

-- 3-B. Chat rooms (9041~9045)
INSERT INTO chat_rooms (id, name, type, game_id, created_at, updated_at, status, created_by)
OVERRIDING SYSTEM VALUE
SELECT
    9041 + i,
    'k6-broadcast-' || i,
    'GAME',
    NULL,
    NOW(),
    NOW(),
    'ACTIVE',
    40000 + i
FROM generate_series(0, 4) AS i
ON CONFLICT (id) DO NOTHING;

-- 3-C. Chat room members
--   senders (40000~40004): 방 9041+i  (1명씩 전담)
--   receivers (40005~40000+:broadcast_vus-1): 방 9041 + ((i-5) % 5)  (5개 방 라운드로빈)
INSERT INTO chat_room_members (room_id, member_id, status, notification_enabled, joined_at, updated_at)
SELECT
    CASE
        WHEN i < 5 THEN 9041 + i
        ELSE 9041 + ((i - 5) % 5)
    END,
    40000 + i,
    'JOINED',
    true,
    NOW(),
    NOW()
FROM generate_series(0, :broadcast_vus - 1) AS i
ON CONFLICT ON CONSTRAINT uq_cp DO NOTHING;

-- ══ 시퀀스 충돌 방지 (이후 자동 발급 ID가 9046 이상임을 보장) ═══
SELECT setval(
    pg_get_serial_sequence('chat_rooms', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM chat_rooms), 9046)
);

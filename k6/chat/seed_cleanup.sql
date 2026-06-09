-- ── k6 시드 데이터 정리 ───────────────────────────────────────
-- 실행: psql $DATABASE_URL -f k6/seed_cleanup.sql
-- ─────────────────────────────────────────────────────────────────

DELETE FROM chat_room_members WHERE member_id BETWEEN 20000 AND 20519;
DELETE FROM chat_room_members WHERE member_id BETWEEN 30000 AND 30519;
DELETE FROM chat_rooms        WHERE id        BETWEEN 9001  AND 9040;
DELETE FROM members           WHERE id        BETWEEN 20000 AND 20519;
DELETE FROM members           WHERE id        BETWEEN 30000 AND 30519;

-- ── k6 시드 데이터 정리 ───────────────────────────────────────
-- 실행: psql $DATABASE_URL -f k6/seed_cleanup.sql
-- 삭제 순서 (FK 의존 순):
--   chat_room_members → chat_messages → chat_rooms → members
--   (room_members.last_read_message_id → messages,
--    messages.room_id → rooms, messages.sender_id → members,
--    rooms.created_by → members)
-- ─────────────────────────────────────────────────────────────────

DELETE FROM chat_room_members WHERE member_id BETWEEN 20000 AND 20000 + :ramping_vus - 1;
DELETE FROM chat_room_members WHERE member_id BETWEEN 30000 AND 30000 + :constant_vus - 1;
DELETE FROM chat_room_members WHERE member_id BETWEEN 40000 AND 40000 + :broadcast_vus - 1;
DELETE FROM chat_messages     WHERE room_id   BETWEEN 9001  AND 9045;
DELETE FROM chat_rooms        WHERE id        BETWEEN 9001  AND 9045;
DELETE FROM members           WHERE id        BETWEEN 20000 AND 20000 + :ramping_vus - 1;
DELETE FROM members           WHERE id        BETWEEN 30000 AND 30000 + :constant_vus - 1;
DELETE FROM members           WHERE id        BETWEEN 40000 AND 40000 + :broadcast_vus - 1;

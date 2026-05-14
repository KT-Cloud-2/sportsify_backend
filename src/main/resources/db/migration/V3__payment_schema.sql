-- ============================================================
-- V3 — payment schema hotfix
-- payments.amount 타입을 BIGINT로 변경
-- ============================================================

ALTER TABLE payments
ALTER COLUMN amount TYPE BIGINT USING amount::BIGINT;

-- V3 — payments 테이블 엔티티 정합성 수정

ALTER TABLE payments
ALTER COLUMN amount TYPE BIGINT;

-- order_id FK 제거 후 VARCHAR로 변경 (엔티티에 FK 관계 없음)
ALTER TABLE payments DROP CONSTRAINT IF EXISTS fk_payment_order;
ALTER TABLE payments ALTER COLUMN order_id TYPE VARCHAR(50) USING order_id::TEXT;
ALTER TABLE payments ADD CONSTRAINT uq_payment_order_id UNIQUE (order_id);

-- 누락 컬럼 추가
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS user_id       BIGINT,
    ADD COLUMN IF NOT EXISTS match_id      BIGINT,
    ADD COLUMN IF NOT EXISTS seat_id       BIGINT,
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS canceled_at   TIMESTAMP;

-- NOT NULL 제약 추가
ALTER TABLE payments
    ALTER COLUMN payment_method  SET NOT NULL,
ALTER COLUMN status          SET NOT NULL,
    ALTER COLUMN requested_at    SET NOT NULL,
    ALTER COLUMN updated_at      SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE orders
    ADD COLUMN total_amount BIGINT NOT NULL DEFAULT 0;

DROP INDEX IF EXISTS idx_orders_status_paying;

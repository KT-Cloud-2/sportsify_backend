-- payments.order_id를 티케팅 Order PK로 사용하고,
-- Toss 결제용 주문번호는 toss_order_id로 분리한다.

ALTER TABLE payments
    RENAME COLUMN order_id TO toss_order_id;

ALTER TABLE payments
    ADD COLUMN order_id BIGINT;

ALTER TABLE payments
    ALTER COLUMN order_id SET NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_order_id_long UNIQUE (order_id);

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_order_id_orders
        FOREIGN KEY (order_id)
            REFERENCES orders (id);

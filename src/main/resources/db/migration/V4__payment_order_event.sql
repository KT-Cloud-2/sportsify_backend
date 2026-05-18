-- payments.order_id를 Toss 결제용 주문번호에서 티케팅 Order PK로 분리한다.
-- 기존 order_id는 toss_order_id로 변경하고,
-- 새 order_id는 orders.id를 참조하는 FK로 추가한다.
-- 기존 payments 데이터가 있을 수 있으므로 order_id는 nullable로 추가한다.
-- NOT NULL 제약은 기존 데이터 백필 정책 확정 후 별도 마이그레이션에서 적용한다.

ALTER TABLE payments
    RENAME COLUMN order_id TO toss_order_id;

ALTER TABLE payments
    ADD COLUMN order_id BIGINT;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_order_id_long UNIQUE (order_id);

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_order_id_orders
        FOREIGN KEY (order_id)
            REFERENCES orders (id);

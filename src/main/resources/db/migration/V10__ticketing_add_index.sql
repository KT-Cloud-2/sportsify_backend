-- 1. orders: PENDING 상태 주문의 id를 빠르게 조회
CREATE INDEX idx_orders_status_pending ON orders (id) WHERE status = 'PENDING';

-- 2. payments: order_id + status 복합 (EXISTS 서브쿼리 최적화)
CREATE INDEX idx_payments_order_id_status ON payments (order_id, status);

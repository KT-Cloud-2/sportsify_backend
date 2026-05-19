-- PAYING 상태 주문의 결제 실패 조회 최적화를 위한 Partial Index
-- OrderExpirationScheduler.findPayingOrdersWithFailedPayment() 쿼리에서
-- orders full scan 대신 PAYING 건만 index scan하도록 개선
CREATE INDEX idx_orders_status_paying ON orders (id) WHERE status = 'PAYING';

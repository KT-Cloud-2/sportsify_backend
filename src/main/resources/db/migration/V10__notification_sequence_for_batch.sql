-- notifications, notification_history IDENTITY → SEQUENCE 전환 (JDBC 배치 INSERT 활성화)
-- BIGSERIAL의 기존 backing sequence(notifications_id_seq 등)를 직접 교체한다.
-- allocationSize=50 에 맞춰 INCREMENT BY 50 으로 설정하여 Hibernate 선점 방식과 동기화한다.

-- notifications
CREATE SEQUENCE IF NOT EXISTS notifications_seq
    START WITH 10000
    INCREMENT BY 50
    NO CYCLE;

-- notification_history
CREATE SEQUENCE IF NOT EXISTS notification_history_seq
    START WITH 10000
    INCREMENT BY 50
    NO CYCLE;

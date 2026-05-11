package com.sportsify.chat.infrastructure.persistence.lock;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL Advisory Lock 어댑터
 */
@Repository
public class AdvisoryLockAdaptor {

    @PersistenceContext
    private EntityManager em;

    /**
     * 트랜잭션 종료 시까지 advisory lock 을 보유
     */
    public void acquireXactLock(String lockKey) {
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("lockKey must not be blank");
        }
        em.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(" +
                                "  ('x' || substr(md5(:k), 1, 16))::bit(64)::bigint" +
                                ")"
                )
                .setParameter("k", lockKey)
                .getSingleResult();
    }

    /**
     * 즉시 시도 후 실패 시 false 반환 (대기하지 않음)
     */
    public boolean tryAcquireXactLock(String lockKey) {
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("lockKey must not be blank");
        }
        Object result = em.createNativeQuery(
                        "SELECT pg_try_advisory_xact_lock(" +
                                "  ('x' || substr(md5(:k), 1, 16))::bit(64)::bigint" +
                                ")"
                )
                .setParameter("k", lockKey)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    /**
     * 현재 트랜잭션의 lock_timeout 설정.
     */
    public void setLockTimeout(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("lock_timeout value must not be blank");
        }
        em.createNativeQuery("SET LOCAL lock_timeout = '" + sanitize(value) + "'")
                .executeUpdate();
    }

    /**
     * SQL injection 방지를 위한 최소 화이트리스트 검증.
     */
    private String sanitize(String value) {
        if (!value.matches("^[0-9]+(ms|s|min)?$")) {
            throw new IllegalArgumentException(
                    "Invalid lock_timeout value: " + value + " (expected like '3s', '500ms')");
        }
        return value;
    }
}
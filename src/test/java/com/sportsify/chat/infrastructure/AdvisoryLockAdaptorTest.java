package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.persistence.lock.AdvisoryLockAdaptor;
import com.sportsify.config.TestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;


@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("[통합] AdvisoryLockAdaptor PostgreSQL Advisory Lock 테스트")
class AdvisoryLockAdaptorTest {

    @Autowired
    private AdvisoryLockAdaptor advisoryLockAdaptor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // ──────────────────────── tryAcquireXactLock ────────────────────────

    @Test
    @Transactional
    @DisplayName("경합이 없으면 true를 반환한다")
    void tryAcquireXactLock_경합_없음_true() {
        boolean result = advisoryLockAdaptor.tryAcquireXactLock("test:lock:free");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("다른 트랜잭션이 락을 보유 중이면 false를 반환한다")
    void tryAcquireXactLock_경합_발생_false() throws Exception {
        String lockKey = "test:lock:concurrent";
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() ->
                transactionTemplate.execute(status -> {
                    advisoryLockAdaptor.tryAcquireXactLock(lockKey);
                    lockHeld.countDown();
                    try {
                        testDone.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                })
        );

        lockHeld.await(5, TimeUnit.SECONDS);

        boolean result = transactionTemplate.execute(status ->
                advisoryLockAdaptor.tryAcquireXactLock(lockKey)
        );

        testDone.countDown();
        future.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(result).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("null lockKey는 예외를 던진다")
    void tryAcquireXactLock_null키_예외() {
        assertThatThrownBy(() -> advisoryLockAdaptor.tryAcquireXactLock(null))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("lockKey must not be blank");
    }

    @Test
    @Transactional
    @DisplayName("공백 lockKey는 예외를 던진다")
    void tryAcquireXactLock_공백키_예외() {
        assertThatThrownBy(() -> advisoryLockAdaptor.tryAcquireXactLock("   "))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("lockKey must not be blank");
    }

    // ──────────────────────── setLockTimeout / sanitize ────────────────────────

    @Test
    @Transactional
    @DisplayName("유효한 형식(숫자+단위)의 lock_timeout 설정은 성공한다")
    void setLockTimeout_유효한_형식_성공() {
        assertThatNoException().isThrownBy(() -> advisoryLockAdaptor.setLockTimeout("3s"));
    }

    @Test
    @Transactional
    @DisplayName("ms 단위도 유효하다")
    void setLockTimeout_ms단위_성공() {
        assertThatNoException().isThrownBy(() -> advisoryLockAdaptor.setLockTimeout("500ms"));
    }

    @Test
    @Transactional
    @DisplayName("허용되지 않는 형식은 sanitize에서 예외를 던진다 (SQL injection 방지)")
    void setLockTimeout_잘못된_형식_예외() {
        assertThatThrownBy(() -> advisoryLockAdaptor.setLockTimeout("'; DROP TABLE chat_messages; --"))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    @Transactional
    @DisplayName("null 값은 예외를 던진다")
    void setLockTimeout_null_예외() {
        assertThatThrownBy(() -> advisoryLockAdaptor.setLockTimeout(null))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }
}

package com.sportsify.notification.infrastructure;

import com.sportsify.notification.infrastructure.consumer.PelMessageProcessor;
import com.sportsify.notification.infrastructure.consumer.StreamMaintenanceScheduler;
import com.sportsify.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
@DisplayName("StreamMaintenanceScheduler 백오프 필터")
class StreamMaintenanceSchedulerTest {

    @Mock private StringRedisTemplate redisTemplate;

    private PelMessageProcessor processor;
    private StreamMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        processor = new PelMessageProcessor(
                redisTemplate, null, null, null, null, null,
                NotificationIntegrationTestSupport.defaultProperties(), null);
        scheduler = new StreamMaintenanceScheduler(redisTemplate, processor,
                NotificationIntegrationTestSupport.defaultProperties());
    }

    // ─── 백오프 미경과: claim 하면 안 됨 ────────────────────────────────

    @Nested
    @DisplayName("백오프 시간이 아직 안 지났을 때 — skip 보장")
    class 백오프미경과_skip {

        @Test
        @DisplayName("1회 실패 후 59초 경과 시 1분 백오프 미충족 → skip")
        void 첫번째실패_59초경과_skip() {
            // GIVEN: deliveryCount=1 → backoff=1분, elapsed=59초
            PendingMessage msg = pendingMessage("1-0", 59, 1);

            // WHEN
            boolean elapsed = isBackoffElapsed(msg);

            // THEN: 1분이 안 지났으므로 claim 대상에서 제외
            assertThat(elapsed).isFalse();
        }

        @Test
        @DisplayName("2회 실패 후 2분 59초 경과 시 3분 백오프 미충족 → skip")
        void 두번째실패_2분59초경과_skip() {
            // GIVEN: deliveryCount=2 → backoff=3분, elapsed=2분59초
            PendingMessage msg = pendingMessage("2-0", 179, 2);

            // WHEN
            boolean elapsed = isBackoffElapsed(msg);

            // THEN
            assertThat(elapsed).isFalse();
        }

        @Test
        @DisplayName("3회 실패 후 4분 59초 경과 시 5분 백오프 미충족 → skip")
        void 세번째실패_4분59초경과_skip() {
            // GIVEN: deliveryCount=3 → backoff=5분, elapsed=4분59초
            PendingMessage msg = pendingMessage("3-0", 299, 3);

            // WHEN
            boolean elapsed = isBackoffElapsed(msg);

            // THEN
            assertThat(elapsed).isFalse();
        }

        @Test
        @DisplayName("4회 실패 후 9분 59초 경과 시 10분 백오프 미충족 → skip")
        void 네번째실패_9분59초경과_skip() {
            // GIVEN: deliveryCount=4 → backoff=10분, elapsed=9분59초
            PendingMessage msg = pendingMessage("4-0", 599, 4);

            // WHEN
            boolean elapsed = isBackoffElapsed(msg);

            // THEN
            assertThat(elapsed).isFalse();
        }
    }

    // ─── 백오프 경과: claim 해야 함 ─────────────────────────────────────

    @Nested
    @DisplayName("백오프 시간이 지났을 때 — claim 허용")
    class 백오프경과_claim허용 {

        @ParameterizedTest(name = "deliveryCount={0}, elapsed={1}초 → 백오프 통과")
        @CsvSource({
                "1, 60",    // 1회 실패, 정확히 1분
                "1, 120",   // 1회 실패, 2분 경과 (스케줄러 지연)
                "2, 180",   // 2회 실패, 정확히 3분
                "2, 210",   // 2회 실패, 3분30초 경과
                "3, 300",   // 3회 실패, 정확히 5분
                "4, 600",   // 4회 실패, 정확히 10분
                "4, 720",   // 4회 실패, 12분 경과 (스케줄러 지연)
        })
        @DisplayName("경과 시간이 백오프 이상이면 claim 허용")
        void 백오프경과_claim허용(int deliveryCount, int elapsedSeconds) {
            // GIVEN
            PendingMessage msg = pendingMessage("1-0", elapsedSeconds, deliveryCount);

            // WHEN
            boolean elapsed = isBackoffElapsed(msg);

            // THEN
            assertThat(elapsed).isTrue();
        }
    }

    // ─── 경계값 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("경계값 — 정확히 백오프 시간")
    class 경계값 {

        @Test
        @DisplayName("elapsed == backoff 이면 claim 허용 (>=)")
        void elapsed_equals_backoff_허용() {
            // GIVEN: deliveryCount=1 → backoff=1분, elapsed=정확히 60초
            PendingMessage msg = pendingMessage("1-0", 60, 1);

            assertThat(isBackoffElapsed(msg)).isTrue();
        }

        @Test
        @DisplayName("elapsed == backoff - 1초 이면 skip (<)")
        void elapsed_backoff_minus1초_skip() {
            // GIVEN: deliveryCount=1 → backoff=1분, elapsed=59초
            PendingMessage msg = pendingMessage("1-0", 59, 1);

            assertThat(isBackoffElapsed(msg)).isFalse();
        }
    }

    // ─── 연속 순서 보장: 1성공후 3 미실행, 3성공후 5 미실행 ─────────────

    @Nested
    @DisplayName("순서 보장 — 이전 단계 성공 직후 다음 단계 미실행")
    class 순서보장 {

        @Test
        @DisplayName("1분 백오프 통과 직후 → 3분 백오프는 아직 skip")
        void 백오프_1분통과후_3분은skip() {
            // GIVEN: 1번 재시도가 방금 처리됨 → elapsed=1분1초
            // 다음 재시도(deliveryCount=2)는 3분 백오프가 필요
            PendingMessage afterFirst = pendingMessage("1-0", 61, 2);

            // WHEN
            boolean elapsed = isBackoffElapsed(afterFirst);

            // THEN: 3분이 안 지났으므로 다음 순서 실행 안 됨
            assertThat(elapsed).isFalse();
        }

        @Test
        @DisplayName("3분 백오프 통과 직후 → 5분 백오프는 아직 skip")
        void 백오프_3분통과후_5분은skip() {
            // GIVEN: elapsed=3분1초, deliveryCount=3 → backoff=5분
            PendingMessage afterSecond = pendingMessage("2-0", 181, 3);

            boolean elapsed = isBackoffElapsed(afterSecond);

            assertThat(elapsed).isFalse();
        }

        @Test
        @DisplayName("5분 백오프 통과 직후 → 10분 백오프는 아직 skip")
        void 백오프_5분통과후_10분은skip() {
            // GIVEN: elapsed=5분1초, deliveryCount=4 → backoff=10분
            PendingMessage afterThird = pendingMessage("3-0", 301, 4);

            boolean elapsed = isBackoffElapsed(afterThird);

            assertThat(elapsed).isFalse();
        }

        @Test
        @DisplayName("여러 메시지 혼재 시 백오프 미경과 메시지만 필터링됨")
        void 혼재_필터링() {
            // GIVEN: 1분 경과(통과)와 30초 경과(skip) 메시지가 함께 있음
            PendingMessage shouldClaim = pendingMessage("1-0", 60, 1);   // elapsed=1분, backoff=1분 → 통과
            PendingMessage shouldSkip  = pendingMessage("2-0", 30, 1);   // elapsed=30초, backoff=1분 → skip

            List<PendingMessage> filtered = List.of(shouldClaim, shouldSkip)
                    .stream()
                    .filter(this::isBackoffElapsed)
                    .toList();

            // THEN: 1분 경과한 메시지만 claim 대상
            assertThat(filtered).containsExactly(shouldClaim);
        }

        private boolean isBackoffElapsed(PendingMessage msg) {
            return StreamMaintenanceSchedulerTest.this.isBackoffElapsed(msg);
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private boolean isBackoffElapsed(PendingMessage msg) {
        int deliveryCount = (int) msg.getTotalDeliveryCount();
        Duration backoff = processor.resolveBackoff(deliveryCount - 1);
        return msg.getElapsedTimeSinceLastDelivery().compareTo(backoff) >= 0;
    }

    private PendingMessage pendingMessage(String id, int elapsedSeconds, long deliveryCount) {
        return new PendingMessage(
                RecordId.of(id),
                Consumer.from("notification-group", "consumer-1"),
                Duration.ofSeconds(elapsedSeconds),
                deliveryCount
        );
    }
}

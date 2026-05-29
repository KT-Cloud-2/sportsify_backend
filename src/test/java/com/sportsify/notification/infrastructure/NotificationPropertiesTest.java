package com.sportsify.notification.infrastructure;

import com.sportsify.notification.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(NotificationProperties.class)
@TestPropertySource(properties = {
        "notification.pel.claim-min-idle=10m",
        "notification.pel.batch-size=100",
        "notification.pel.stuck-timeout=10m",
        "notification.pel.backoff-minutes=3,5,10",
        "notification.stream.max-len=10000",
        "notification.scheduler.reserved-dispatch-cron=0 0/5 * * * *",
        "notification.scheduler.stream-trim-cron=0 0 3 * * *",
        "notification.scheduler.stuck-recovery-cron=0 0/10 * * * *",
        "notification.scheduler.pel-reclaim-cron=0 0/1 * * * *",
        "notification.scheduler.leader-lock-ttl=310s",
        "notification.slack.webhook-url=",
        "notification.slack.signing-secret=",
        "notification.slack.suppress-ttl=10m"
})
class NotificationPropertiesTest {

    @Autowired
    private NotificationProperties properties;

    @Test
    @DisplayName("pel.backoffMinutes가 [3,5,10]으로 바인딩된다")
    void pel_backoffMinutes_바인딩() {
        assertThat(properties.pel().backoffMinutes()).containsExactly(3, 5, 10);
    }

    @Test
    @DisplayName("pel.batchSize가 100으로 바인딩된다")
    void pel_batchSize_바인딩() {
        assertThat(properties.pel().batchSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("stream.maxLen이 10000으로 바인딩된다")
    void stream_maxLen_바인딩() {
        assertThat(properties.stream().maxLen()).isEqualTo(10000);
    }

    @Test
    @DisplayName("scheduler cron 표현식이 모두 바인딩된다")
    void scheduler_cron_바인딩() {
        assertThat(properties.scheduler().reservedDispatchCron()).isEqualTo("0 0/5 * * * *");
        assertThat(properties.scheduler().pelReclaimCron()).isEqualTo("0 0/1 * * * *");
        assertThat(properties.scheduler().streamTrimCron()).isEqualTo("0 0 3 * * *");
    }

    @Test
    @DisplayName("pel.backoffMinutes는 비어있지 않아야 한다")
    void pel_backoffMinutes_비어있지않음() {
        assertThat(properties.pel().backoffMinutes()).isNotEmpty();
    }

    @Test
    @DisplayName("pel.backoffMinutes 항목은 모두 양수여야 한다")
    void pel_backoffMinutes_양수검증() {
        assertThat(properties.pel().backoffMinutes())
                .allSatisfy(minutes -> assertThat(minutes).isGreaterThan(0));
    }

    @Test
    @DisplayName("stream.maxLen은 1 이상이어야 한다")
    void stream_maxLen_범위검증() {
        assertThat(properties.stream().maxLen()).isGreaterThan(0);
    }

    @Test
    @DisplayName("pel.batchSize는 1 이상이어야 한다")
    void pel_batchSize_범위검증() {
        assertThat(properties.pel().batchSize()).isGreaterThan(0);
    }

    @Test
    @DisplayName("scheduler.leaderLockTtl은 양수여야 한다")
    void scheduler_leaderLockTtl_범위검증() {
        assertThat(properties.scheduler().leaderLockTtl().isPositive()).isTrue();
    }

    @Test
    @DisplayName("pel.claimMinIdle은 양수여야 한다")
    void pel_claimMinIdle_범위검증() {
        assertThat(properties.pel().claimMinIdle().isPositive()).isTrue();
    }
}

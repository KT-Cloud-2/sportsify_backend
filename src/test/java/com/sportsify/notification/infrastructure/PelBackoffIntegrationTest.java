package com.sportsify.notification.infrastructure;

import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.consumer.PelMessageProcessor;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PelBackoffIntegrationTest extends RepositoryTestSupport {

    @Autowired
    private PelMessageProcessor pelMessageProcessor;

    @Autowired
    private NotificationProperties properties;

    @Test
    @DisplayName("yml backoff-minutes 배열(1,3,5,10)이 그대로 로딩된다")
    void yml_백오프_배열_로딩() {
        List<Integer> backoff = properties.pel().backoffMinutes();

        assertThat(backoff).containsExactly(1, 3, 5, 10);
    }

    @Test
    @DisplayName("retryCount 0~3이 yml 배열 순서대로 백오프를 반환한다")
    void resolveBackoff_배열순서대로_반환() {
        assertThat(pelMessageProcessor.resolveBackoff(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(pelMessageProcessor.resolveBackoff(1)).isEqualTo(Duration.ofMinutes(3));
        assertThat(pelMessageProcessor.resolveBackoff(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(pelMessageProcessor.resolveBackoff(3)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("retryCount가 배열 마지막 인덱스를 초과해도 마지막 백오프 값을 반환하고 그 이상 증가하지 않는다")
    void resolveBackoff_배열초과시_마지막값_고정() {
        Duration last = Duration.ofMinutes(10);

        assertThat(pelMessageProcessor.resolveBackoff(4)).isEqualTo(last);
        assertThat(pelMessageProcessor.resolveBackoff(99)).isEqualTo(last);
        assertThat(pelMessageProcessor.resolveBackoff(Integer.MAX_VALUE)).isEqualTo(last);
    }
}

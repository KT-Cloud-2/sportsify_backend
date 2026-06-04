package com.sportsify.notification.support;

import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

public abstract class NotificationIntegrationTestSupport extends RepositoryTestSupport {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanUpBefore() {
        cleanNotificationTables();
    }

    @AfterEach
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanUpAfter() {
        cleanNotificationTables();
    }

    private void cleanNotificationTables() {
        jdbcTemplate.execute("DELETE FROM notification_history");
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM notification_events");
        jdbcTemplate.execute("DELETE FROM notification_channels");
    }

    public static NotificationProperties defaultProperties() {
        return new NotificationProperties(
                new NotificationProperties.Pel(Duration.ofMinutes(10), 100, Duration.ofMinutes(10), List.of(1, 3, 5, 10), 3),
                new NotificationProperties.Stream(10000),
                new NotificationProperties.Scheduler("0 0/5 * * * *", "0 0 3 * * *", "0 0/10 * * * *", "0 0/1 * * * *", Duration.ofSeconds(310)),
                new NotificationProperties.Channel(2),
                new NotificationProperties.Fanout(500),
                new NotificationProperties.Payload(30),
                new NotificationProperties.Sse(1800000),
                new NotificationProperties.Slack("", "", Duration.ofMinutes(10))
        );
    }
}

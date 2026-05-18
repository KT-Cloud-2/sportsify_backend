package com.sportsify.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public AtomicReference<Clock> clockReference() {
        return new AtomicReference<>(Clock.systemDefaultZone());
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(AtomicReference<Clock> clockReference) {
        return () -> Optional.of(LocalDateTime.now(clockReference.get()));
    }
}

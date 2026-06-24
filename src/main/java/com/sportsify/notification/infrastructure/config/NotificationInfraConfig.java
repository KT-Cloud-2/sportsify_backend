package com.sportsify.notification.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@EnableCaching
@Configuration
public class NotificationInfraConfig {

    private static final int NOTIFICATION_DOMAIN_CONCURRENCY_LIMIT = 500;

    private final Semaphore notificationDomainSemaphore = new Semaphore(NOTIFICATION_DOMAIN_CONCURRENCY_LIMIT);

    @Bean
    public Executor sseVirtualThreadExecutor() {
        return boundedVirtualThreadExecutor();
    }

    @Bean("notificationAsyncExecutor")
    public Executor notificationAsyncExecutor() {
        return boundedVirtualThreadExecutor();
    }

    private Executor boundedVirtualThreadExecutor() {
        return task -> Thread.ofVirtual().start(() -> {
            notificationDomainSemaphore.acquireUninterruptibly();
            try {
                task.run();
            } finally {
                notificationDomainSemaphore.release();
            }
        });
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("notificationSettings");
    }
}

package com.sportsify.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableAsync
@EnableResilientMethods
public class AsyncConfig implements AsyncConfigurer {

    @Bean("chatEventExecutor")
    public Executor chatEventExecutor(MeterRegistry meterRegistry) {
        Timer queueWaitTimer = Timer.builder("chat_event_executor_queue_wait")
                .description("chatEventExecutor 큐 대기시간 (AFTER_COMMIT 발행 → 스레드 픽업)")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        Timer execTimer = Timer.builder("chat_event_executor_exec_duration")
                .description("chatEventExecutor 스레드 실행시간 (ChatEventHandler.sendEvent 전체)")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(20000);
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("chatEventExecutor rejected. pool={}, queue={}", e.getPoolSize(), e.getQueue().size())
        );
        executor.setThreadNamePrefix("chat-event-");
        executor.setTaskDecorator(runnable -> {
            long enqueueNanos = System.nanoTime();
            return () -> {
                queueWaitTimer.record(System.nanoTime() - enqueueNanos, TimeUnit.NANOSECONDS);
                long execStart = System.nanoTime();
                try {
                    runnable.run();
                } finally {
                    execTimer.record(System.nanoTime() - execStart, TimeUnit.NANOSECONDS);
                }
            };
        });
        executor.initialize();
        return executor;
    }

//    @Bean("broadcastExecutor")
//    public Executor broadcastExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(1);
//        executor.setMaxPoolSize(2);
//        executor.setQueueCapacity(2000);
//        executor.setRejectedExecutionHandler((r, e) ->
//                log.warn("broadcastExecutor rejected. pool={}, queue={}", e.getPoolSize(), e.getQueue().size())
//        );
//        executor.setThreadNamePrefix("broadcast-");
//        executor.initialize();
//        return executor;
//    }

    @Bean("wsEventExecutor")
    public Executor wsEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20000);
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("wsEventExecutor rejected. pool={}, queue={}", e.getPoolSize(), e.getQueue().size())
        );
        executor.setThreadNamePrefix("ws-event-");
        executor.initialize();
        return executor;
    }

    @Bean("defaultAsyncExecutor")
    public Executor defaultAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(4000);
        executor.setThreadNamePrefix("async-default-");
        executor.initialize();
        return executor;
    }

    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor(MeterRegistry meterRegistry) {
        Timer queueWaitTimer = Timer.builder("virtual_thread_executor_queue_wait")
                .description("virtualThreadExecutor 큐 대기시간 (STOMP SEND 수신 → DB 처리 시작)")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);

        // OS thread 버전 — core=4 초과 시 큐 선 적재로 max(8)까지 늦게 확장됨
//        return new ThreadPoolExecutor(
//                4, 8, 0L, TimeUnit.MILLISECONDS,
//                new LinkedBlockingQueue<>(2000),
//                Executors.defaultThreadFactory()
//        ) {
//            @Override
//            public void execute(Runnable command) {
//                long enqueueNanos = System.nanoTime();
//                super.execute(() -> {
//                    queueWaitTimer.record(System.nanoTime() - enqueueNanos, TimeUnit.NANOSECONDS);
//                    command.run();
//                });
//            }
//        };

        // Virtual thread 버전 — I/O 대기 시 carrier OS thread 반납, 큐 대기 없이 즉시 실행
        return new ThreadPoolExecutor(
                200, 200, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(20000),
                Thread.ofVirtual().factory()
        ) {
            @Override
            public void execute(Runnable command) {
                long enqueueNanos = System.nanoTime();
                super.execute(() -> {
                    queueWaitTimer.record(System.nanoTime() - enqueueNanos, TimeUnit.NANOSECONDS);
                    command.run();
                });
            }
        };
    }

    @Bean("chatEventTxTemplate")
    public TransactionTemplate chatEventTxTemplate(PlatformTransactionManager txManager) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }


    @Override
    public Executor getAsyncExecutor() {
        return defaultAsyncExecutor();
    }
}

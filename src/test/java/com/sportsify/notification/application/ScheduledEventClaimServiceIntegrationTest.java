package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.ScheduledEventClaimService;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventStatus;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledEventClaimServiceIntegrationTest extends RepositoryTestSupport {

    @Autowired private ScheduledEventClaimService claimService;
    @Autowired private NotificationEventRepository eventRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("동시에 두 스레드가 claim해도 각 이벤트는 한 번만 claim된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void claimDueEvents_동시claim_중복없음() throws InterruptedException {
        // GIVEN: 만기 예약 이벤트 3건 저장
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        transactionTemplate.execute(status -> {
            for (int i = 0; i < 3; i++) {
                eventRepository.save(NotificationEvent.createScheduled(
                        NotificationEventType.TICKET_OPEN, "{}", past));
            }
            return null;
        });

        // WHEN: 두 스레드가 동시에 claim
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger totalClaimed = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(); // 두 스레드가 동시에 진입하도록 대기
                    List<NotificationEvent> claimed = claimService.claimDueEvents();
                    totalClaimed.addAndGet(claimed.size());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // THEN: 두 스레드 합산 claimed 수 = 3 (중복 없음)
        assertThat(totalClaimed.get()).isEqualTo(3);

        // 모든 이벤트가 PROCESSING 상태
        transactionTemplate.execute(status -> {
            List<NotificationEvent> all = eventRepository.findAllById(
                    eventRepository.findDueScheduledEventsForUpdate(LocalDateTime.now().plusHours(1))
                            .stream().map(NotificationEvent::getId).toList()
            );
            // findDueScheduledEventsForUpdate는 PENDING만 조회 — PROCESSING 상태면 0건
            assertThat(all).isEmpty();
            return null;
        });
    }

    @Test
    @DisplayName("PROCESSING stuck 이벤트가 PENDING으로 복구된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void claimStuckEvents_PROCESSING_PENDING으로복구() {
        // GIVEN: 15분 전에 PROCESSING 상태가 된 stuck 이벤트
        NotificationEvent event = transactionTemplate.execute(status ->
                eventRepository.save(NotificationEvent.createScheduled(
                        NotificationEventType.TICKET_OPEN, "{}", LocalDateTime.now().minusMinutes(20)))
        );

        transactionTemplate.execute(status -> {
            NotificationEvent found = eventRepository.findById(event.getId()).orElseThrow();
            found.markProcessing();
            return eventRepository.save(found);
        });

        // WHEN: 10분 타임아웃 기준으로 stuck 이벤트 복구
        LocalDateTime stuckBefore = LocalDateTime.now().minusMinutes(10);
        transactionTemplate.execute(status -> {
            List<NotificationEvent> recovered = claimService.claimStuckEvents(stuckBefore);
            assertThat(recovered).hasSize(1);
            return null;
        });

        // THEN: PENDING으로 복구됨
        transactionTemplate.execute(status -> {
            NotificationEvent recovered = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(recovered.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
            return null;
        });
    }
}

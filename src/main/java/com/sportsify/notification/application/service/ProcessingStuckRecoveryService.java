package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingStuckRecoveryService {

    private static final long STUCK_TIMEOUT_MINUTES = 10;
    private static final long RECOVERY_INTERVAL_MS = STUCK_TIMEOUT_MINUTES * 60 * 1_000;

    private final ScheduledEventClaimService claimService;

    @Scheduled(fixedDelay = RECOVERY_INTERVAL_MS)
    public void recoverStuckEvents() {
        LocalDateTime stuckBefore = LocalDateTime.now().minusMinutes(STUCK_TIMEOUT_MINUTES);
        List<NotificationEvent> stuck = claimService.claimStuckEvents(stuckBefore);
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("PROCESSING stuck 이벤트 감지 count={} — PENDING으로 복구", stuck.size());
        stuck.forEach(event -> log.warn("stuck 이벤트 복구 eventId={} updatedAt={}", event.getId(), event.getUpdatedAt()));
    }
}

package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledNotificationProcessor {

    private final ScheduledEventClaimService claimService;
    private final FanoutService fanoutService;
    private final RedisStreamNotificationEventPublisher streamPublisher;

    // 매 5분 단위 정각 실행 (0, 5, 10, 15 ... 분)
    // saleStartAt이 과거 시각이면 다음 tick에 최대 5분 지연 발송됨 — 의도된 동작
    @Scheduled(cron = "0 0/5 * * * *")
    public void processDue() {
        List<NotificationEvent> claimed = claimService.claimDueEvents();
        if (claimed.isEmpty()) {
            return;
        }
        log.info("예약 알림 처리 시작 count={}", claimed.size());
        for (NotificationEvent event : claimed) {
            try {
                boolean failed = fanoutService.fanout(event, event.getEventType(), event.getPayload());
                if (failed) {
                    streamPublisher.republish(event.getEventType(), event.getPayload());
                    log.warn("예약 알림 fanout 실패, PEL로 재발행 eventId={}", event.getId());
                }
            } catch (Exception e) {
                log.error("예약 알림 처리 실패 eventId={}", event.getId(), e);
                streamPublisher.republish(event.getEventType(), event.getPayload());
            }
        }
    }
}

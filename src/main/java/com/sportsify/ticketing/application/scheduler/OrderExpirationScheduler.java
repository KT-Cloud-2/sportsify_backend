package com.sportsify.ticketing.application.scheduler;

import com.sportsify.ticketing.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationScheduler {

    private final OrderService orderService;
    private final AtomicInteger activeSaleCount = new AtomicInteger(0);

    public void onSaleStarted() {
        activeSaleCount.incrementAndGet();
    }

    public void onSaleEnded() {
        activeSaleCount.decrementAndGet();
    }

    @Scheduled(fixedDelay = 1000)
    public void releaseUnpaidOrders() {
        if (activeSaleCount.get() <= 0) {
            return;
        }

        try {
            orderService.expireUnpaidOrdersBulk();
        } catch (RuntimeException e) {
            log.error("[ORDER_SCHEDULER] 미결제 만료 처리 실패", e);
        }

        try {
            orderService.cancelFailedPaymentOrdersBulk();
        } catch (RuntimeException e) {
            log.error("[ORDER_SCHEDULER] 결제 실패 건 취소 처리 실패", e);
        }
    }
}

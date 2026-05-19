package com.sportsify.ticketing.application.scheduler;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseUnpaidOrders() {
        List<Order> expiredOrders = orderRepository
                .findExpiredPendingOrdersWithoutPayment(LocalDateTime.now());

        List<Order> failedOrders = orderRepository.findPayingOrdersWithFailedPayment();

        expiredOrders.forEach(Order::expire);
        failedOrders.forEach(Order::cancel);
    }
}

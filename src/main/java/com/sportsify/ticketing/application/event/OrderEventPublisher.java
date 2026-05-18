package com.sportsify.ticketing.application.event;


import com.sportsify.common.event.OrderCreatedEvent;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishOrderCreated(Order order) {
        Long amount = order.getOrderSeats().stream().mapToLong(OrderSeat::getPrice).sum();

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), order.getMemberId(), amount, order.getCreatedAt())
        );
    }
}

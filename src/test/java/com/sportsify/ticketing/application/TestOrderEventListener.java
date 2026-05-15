package com.sportsify.ticketing.application;

import com.sportsify.common.event.OrderCreatedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestOrderEventListener {

    private OrderCreatedEvent lastEvent;

    @EventListener
    public void handle(OrderCreatedEvent event) {
        this.lastEvent = event;
    }

    public OrderCreatedEvent getLastEvent() {
        return lastEvent;
    }

    public void clear() {
        lastEvent = null;
    }
}

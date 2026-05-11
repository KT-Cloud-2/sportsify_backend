package com.sportsify.chat.domain.model.config;

import com.sportsify.chat.domain.model.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseAggregateRoot {
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}

package com.sportsify.chat.domain.model.event;

import java.time.Instant;

public interface DomainEvent {
    String event();

    Long roomId();

    Instant occurredAt();
}

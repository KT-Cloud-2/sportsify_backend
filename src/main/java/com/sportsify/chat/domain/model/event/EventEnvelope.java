package com.sportsify.chat.domain.model.event;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

import java.time.Instant;

public record EventEnvelope<T>(
        String event,
        Long roomId,
        Instant occurredAt,
        T payload
) implements DomainEvent, ResolvableTypeProvider {

    public static <T> EventEnvelope<T> of(EventType event, ChatRoomId roomId, Instant now, T payload) {
        return new EventEnvelope<>(event.name(), roomId.value(), now, payload);
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(EventEnvelope.class, payload.getClass());
    }
}

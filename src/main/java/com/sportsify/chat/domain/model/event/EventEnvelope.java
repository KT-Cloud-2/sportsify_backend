package com.sportsify.chat.domain.model.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

import java.time.Instant;

public record EventEnvelope<T>(
        String event,
        Long roomId,
        Instant occurredAt,
        T payload,
        Long alertMessageId
) implements DomainEvent, ResolvableTypeProvider {

    public static <T> EventEnvelope<T> of(EventType event, ChatRoomId roomId, Instant now, T payload) {
        return new EventEnvelope<>(event.name(), roomId.value(), now, payload, null);
    }

    public EventEnvelope<T> withAlertMessageId(Long id) {
        return new EventEnvelope<>(event, roomId, occurredAt, payload, id);
    }

    @Override
    @JsonIgnore
    public ResolvableType getResolvableType() {
        if (payload == null) return ResolvableType.forClass(EventEnvelope.class);
        return ResolvableType.forClassWithGenerics(EventEnvelope.class, payload.getClass());
    }
}

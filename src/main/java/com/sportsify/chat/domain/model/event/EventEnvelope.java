package com.sportsify.chat.domain.model.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

import java.time.Instant;

public record EventEnvelope<T>(
        String event,
        Long roomId,
        Instant occurredAt,
        T payload,
        Long alertMessageId,
        ChatRoomType roomType,
        String roomName
) implements DomainEvent, ResolvableTypeProvider {

    public static <T> EventEnvelope<T> of(EventType event, ChatRoomId roomId, Instant now, T payload) {
        return new EventEnvelope<>(event.name(), roomId.value(), now, payload, null, null, null);
    }

    public EventEnvelope<T> withAlertMessageId(Long id) {
        return new EventEnvelope<>(event, roomId, occurredAt, payload, id, roomType, roomName);
    }

    public EventEnvelope<T> withRoomType(ChatRoomType type) {
        return new EventEnvelope<>(event, roomId, occurredAt, payload, alertMessageId, type, roomName);
    }

    public EventEnvelope<T> withRoomContext(ChatRoomType type, String name) {
        return new EventEnvelope<>(event, roomId, occurredAt, payload, alertMessageId, type, name);
    }

    @Override
    @JsonIgnore
    public ResolvableType getResolvableType() {
        if (payload == null) return ResolvableType.forClass(EventEnvelope.class);
        return ResolvableType.forClassWithGenerics(EventEnvelope.class, payload.getClass());
    }
}

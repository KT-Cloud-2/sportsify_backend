package com.sportsify.chat.domain.model.message.event;

import com.sportsify.chat.domain.model.message.Message;

import java.time.LocalDateTime;

public record MessageDeleteEvent(
        Long messageId,
        Long roomId,
        LocalDateTime occurredAt
) implements DomainEvent {
    public static MessageDeleteEvent from(Message message, LocalDateTime now) {
        return new MessageDeleteEvent(message.getId().value(), message.getRoomId().value(), now);
    }
}

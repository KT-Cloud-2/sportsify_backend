package com.sportsify.chat.domain.model.message.event;

import com.sportsify.chat.domain.model.message.Message;

import java.time.LocalDateTime;

public record MessageSentEvent(
        Message message,
        LocalDateTime occurredAt
) implements DomainEvent {
    public static MessageSentEvent from(Message message, LocalDateTime now) {
        return new MessageSentEvent(message, now);
    }
}

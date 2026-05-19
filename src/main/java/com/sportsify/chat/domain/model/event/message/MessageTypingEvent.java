package com.sportsify.chat.domain.model.event.message;

import com.sportsify.chat.domain.model.event.DomainEvent;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.presentation.message.dto.ChatTypingPayload;

import java.time.Instant;

public record MessageTypingEvent(
        String event,
        Long roomId,
        Long userId,
        boolean typing,
        Instant occurredAt
) implements DomainEvent {
    public static MessageTypingEvent from(ChatTypingPayload payload, Long memberId, boolean isTyping, Instant now) {
        return new MessageTypingEvent(
                EventType.TYPING.name(),
                payload.roomId(),
                memberId,
                isTyping,
                now
        );
    }
}

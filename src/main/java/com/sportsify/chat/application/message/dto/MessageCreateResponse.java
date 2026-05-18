package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.domain.model.message.Message;

import java.time.Instant;

public record MessageCreateResponse(
        Long messageId,
        Instant createdAt
) {
    public static MessageCreateResponse from(Message message) {
        return new MessageCreateResponse(
                message.getId().value(),
                message.getCreatedAt()
        );
    }
}

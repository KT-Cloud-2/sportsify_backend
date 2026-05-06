package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.domain.model.message.Message;

import java.time.LocalDateTime;

public record MessageCreateResponse(
        Long messageId,
        LocalDateTime createdAt
) {
    public static MessageCreateResponse from(Message message) {
        return new MessageCreateResponse(
                message.getId().value(),
                message.getCreatedAt()
        );
    }
}

package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.domain.model.message.Message;

import java.time.Instant;

public record MessageResponse(
        Long messageId,
        Long senderId,
        String type,
        String content,
        Instant createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId().value(),
                message.getSenderId().value(),
                message.getType().name(),
                message.getContent().value(),
                message.getCreatedAt()
        );
    }
}

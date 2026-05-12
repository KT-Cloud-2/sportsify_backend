package com.sportsify.chat.application.chatRoom.dto;

import com.sportsify.chat.domain.model.message.Message;

import java.time.Instant;

public record ChatMessageResponse(
        Long messageId,
        String content,
        String type,
        Instant createdAt
) {
    public static ChatMessageResponse of(Message message) {
        if (message == null) return null;
        return new ChatMessageResponse(
                message.getId().value(),
                message.getContent().value(),
                message.getType().name(),
                message.getCreatedAt()
        );
    }
}

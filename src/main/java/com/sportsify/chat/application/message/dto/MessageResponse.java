package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageStatus;

import java.time.Instant;

public record MessageResponse(
        Long messageId,
        Long senderId,
        String type,
        String status,
        String content,
        Instant createdAt
) {
    private static final String DELETED_CONTENT = "삭제된 메시지입니다.";

    public static MessageResponse from(Message message) {
        boolean deleted = message.getStatus() == MessageStatus.DELETED;
        return new MessageResponse(
                message.getId().value(),
                message.getSenderId() != null ? message.getSenderId().value() : null,
                message.getType().name(),
                message.getStatus().name(),
                deleted ? DELETED_CONTENT : message.getContent().value(),
                message.getCreatedAt()
        );
    }
}

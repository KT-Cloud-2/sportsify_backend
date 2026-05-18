package com.sportsify.chat.domain.model.event.message;

import com.sportsify.chat.domain.model.message.Message;

public record MessageSentPayload(
        Long messageId,
        String clientMessageId,
        Long senderId,
        String type,
        String content
) {
    public static MessageSentPayload from(Message message, String clientMessageId) {
        return new MessageSentPayload(
                message.getId() != null ? message.getId().value() : null,
                clientMessageId,
                message.getSenderId().value(),
                message.getType().name(),
                message.getContent().value()
        );
    }
}

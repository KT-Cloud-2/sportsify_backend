package com.sportsify.chat.domain.model.event.message;

import com.sportsify.chat.domain.model.message.Message;

public record MessageSentPayload(
        String clientMessageId,
        Long senderId,
        String type,
        String content
) {
    public static MessageSentPayload from(Message message, String clientMessageId) {
        return new MessageSentPayload(
                clientMessageId,
                message.getSenderId().value(),
                message.getType().name(),
                message.getContent().value()
        );
    }
}

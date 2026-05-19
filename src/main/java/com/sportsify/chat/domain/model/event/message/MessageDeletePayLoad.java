package com.sportsify.chat.domain.model.event.message;

import com.sportsify.chat.domain.model.message.Message;

public record MessageDeletePayLoad(
        Long messageId
) {
    public static MessageDeletePayLoad from(Message message) {
        return new MessageDeletePayLoad(
                message.getId().value());
    }
}

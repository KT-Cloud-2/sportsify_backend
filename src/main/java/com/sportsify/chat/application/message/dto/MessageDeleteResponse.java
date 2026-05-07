package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.domain.model.message.Message;

public record MessageDeleteResponse(
        Long messageId,
        Long roomId,
        String status
) {
    public static MessageDeleteResponse from(Message message) {
        return new MessageDeleteResponse(
                message.getId().value(),
                message.getRoomId().value(),
                message.getStatus().name()
        );
    }
}

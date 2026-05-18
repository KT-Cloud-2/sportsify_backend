package com.sportsify.chat.application.message.dto;

import com.sportsify.chat.presentation.message.dto.ChatSendPayload;

public record MessageCreateRequest(
        String clientMessageId,
        Long roomId,
        String type,
        String content
) {
    public static MessageCreateRequest from(ChatSendPayload payload) {
        return new MessageCreateRequest(
                payload.clientMessageId(),
                payload.roomId(),
                payload.type(),
                payload.content()
        );
    }
}

package com.sportsify.chat.presentation.message.dto;

public record ChatTypingPayload(
        Long roomId,
        Boolean typing
) {
}

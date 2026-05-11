package com.sportsify.chat.presentation.message.dto;

import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.common.exception.BusinessException;

public record ErrorResponse(
        String event,
        String clientMessageId,
        String errorCode,
        String message
) {
    public static ErrorResponse from(ErrorEventType event, BusinessException e, String clientMessageId) {
        return new ErrorResponse(
                event.name(),
                clientMessageId,
                e.getErrorCode().toString(),
                e.getMessage()
        );
    }

    public static ErrorResponse from(ErrorEventType event, String message, String clientMessageId) {
        return new ErrorResponse(
                event.name(),
                clientMessageId,
                null,
                message
        );
    }
}

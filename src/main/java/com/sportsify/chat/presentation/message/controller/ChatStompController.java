package com.sportsify.chat.presentation.message.controller;

import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.domain.model.event.message.MessageTypingEvent;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.StompAuthChannelInterceptor.StompPrincipal;
import com.sportsify.chat.presentation.message.dto.ChatReadPayload;
import com.sportsify.chat.presentation.message.dto.ChatSendPayload;
import com.sportsify.chat.presentation.message.dto.ChatTypingPayload;
import com.sportsify.chat.presentation.message.dto.ErrorResponse;
import com.sportsify.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;

@Controller
@Slf4j
@RequiredArgsConstructor
public class ChatStompController {
    private final MessageService messageService;
    private final ChatEventPublisher chatEventPublisher;
    private final Clock clock;

    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendPayload payload, Principal principal) {
        long id = ((StompPrincipal) principal).memberId();
        try {
            messageService.send(MessageCreateRequest.from(payload), id);
        } catch (BusinessException e) {
            chatEventPublisher.publishToUser(
                    id,
                    ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, e, payload.clientMessageId()),
                    payload.clientMessageId()
            );
        }
    }


    @MessageMapping("/chat.read")
    public void markRead(@Payload ChatReadPayload payload, Principal principal) {
        long memberId = ((StompPrincipal) principal).memberId();
        messageService.read(payload.roomId(), memberId, payload.lastReadMessageId());
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload ChatTypingPayload payload, Principal principal) {
        long memberId = ((StompPrincipal) principal).memberId();
        chatEventPublisher.publishToRoomTyping(payload.roomId(), MessageTypingEvent.from(
                payload, memberId, true, Instant.now(clock)
        ));
    }


}

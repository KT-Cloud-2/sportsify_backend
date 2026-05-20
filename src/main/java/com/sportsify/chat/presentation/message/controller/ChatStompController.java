package com.sportsify.chat.presentation.message.controller;

import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.domain.model.event.message.MessageTypingEvent;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
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

    /**
     * 5-17-3-1. 메시지 전송
     *
     * @param payload
     * @param principal
     */
    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendPayload payload, Principal principal) {
        long id = Long.parseLong(principal.getName());
        try {
            messageService.send(MessageCreateRequest.from(payload), id);
        } catch (BusinessException e) {
            chatEventPublisher.publishToUser(
                    id,
                    ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, e, payload.clientMessageId()),
                    "/queue/errors"
            );
        }
    }


    /**
     * 5-17-3-2. 읽음 상태 갱신
     *
     * @param payload
     * @param principal
     */
    @MessageMapping("/chat.read")
    public void markRead(@Payload ChatReadPayload payload, Principal principal) {
        long memberId = Long.parseLong(principal.getName());
        messageService.read(payload.roomId(), memberId, payload.lastReadMessageId(), true);
    }

    /**
     * 5-17-3-3. 타이핑 인디케이터
     *
     * @param payload
     * @param principal
     */
    @MessageMapping("/chat.typing")
    public void typing(@Payload ChatTypingPayload payload, Principal principal) {
        long memberId = Long.parseLong(principal.getName());
        chatEventPublisher.publishToRoomTyping(payload.roomId(), MessageTypingEvent.from(
                payload, memberId, payload.typing(), Instant.now(clock)
        ));
    }


}

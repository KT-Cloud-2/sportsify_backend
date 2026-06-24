package com.sportsify.chat.presentation.message.controller;

import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.domain.model.event.message.MessageTypingEvent;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketMetrics;
import com.sportsify.chat.presentation.message.dto.ChatReadPayload;
import com.sportsify.chat.presentation.message.dto.ChatSendPayload;
import com.sportsify.chat.presentation.message.dto.ChatTypingPayload;
import com.sportsify.chat.presentation.message.dto.ErrorResponse;
import com.sportsify.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;

@Controller
@Slf4j
@RequiredArgsConstructor
public class ChatStompController {

    private final MessageService messageService;
    private final ChatEventPublisher chatEventPublisher;
    private final Clock clock;
    private final WebSocketMetrics webSocketMetrics;
    private final Executor virtualThreadExecutor;

    /**
     * 5-17-3-1. 메시지 전송
     *
     * @param payload
     * @param principal
     */
    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendPayload payload, Principal principal) {
        long id = Long.parseLong(principal.getName());
        webSocketMetrics.recordMessageIn();
        virtualThreadExecutor.execute(() -> {
            try {
                webSocketMetrics.recordMessageDuration(() -> messageService.send(MessageCreateRequest.from(payload), id));
            } catch (BusinessException e) {
                chatEventPublisher.publishToUser(id, ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, e, payload.clientMessageId()), "/user/queue/errors");
            } catch (Exception e) {
                chatEventPublisher.publishToUser(id, ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, "메시지 전송에 실패했습니다.", payload.clientMessageId()), "/user/queue/errors");
            }
        });
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

    // send 함수가 virtual thread를 통해 실행됨에 따라 해당 예외를 잡지 못해 죽은 코드가 되어 주석처리 했습니다.
//    @MessageExceptionHandler(MessageSendException.class)
//    @SendToUser("/queue/errors")
//    public ErrorResponse handleMessageSendException(MessageSendException e) {
//        if (e.getCause() instanceof BusinessException be) {
//            return ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, be, e.clientMessageId());
//        }
//        return ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, "메시지 전송에 실패했습니다.", e.clientMessageId());
//    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleBusinessException(BusinessException e) {
        return ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, e, null);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleException() {
        return ErrorResponse.from(ErrorEventType.MESSAGE_FAILED, "메시지 전송에 실패했습니다.", null);
    }
}

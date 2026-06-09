package com.sportsify.chat.presentation;

import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.presentation.message.controller.ChatStompController;
import com.sportsify.chat.presentation.message.dto.ChatReadPayload;
import com.sportsify.chat.presentation.message.dto.ChatSendPayload;
import com.sportsify.chat.presentation.message.dto.ChatTypingPayload;
import com.sportsify.chat.presentation.message.dto.ErrorResponse;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-12T00:00:00Z");
    private static final long MEMBER_ID = 42L;
    private static final long ROOM_ID = 1L;
    private static final String CLIENT_MESSAGE_ID = "client-uuid-1";

    @Mock
    MessageService messageService;
    @Mock
    ChatEventPublisher chatEventPublisher;

    ChatStompController controller;
    Principal principal = () -> String.valueOf(MEMBER_ID);

    @BeforeEach
    void setUp() {
        controller = new ChatStompController(messageService, chatEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ── send ──────────────────────────────────────────────────

    @Test
    @DisplayName("send 성공 시 messageService.send가 호출되고 에러 이벤트는 발행되지 않는다")
    void send_성공_에러미발행() {
        ChatSendPayload payload = new ChatSendPayload(CLIENT_MESSAGE_ID, ROOM_ID, "TEXT", "안녕하세요");

        controller.send(payload, principal);

        verify(messageService).send(any(), eq(MEMBER_ID));
        verifyNoInteractions(chatEventPublisher);
    }

    @Test
    @DisplayName("handleBusinessException은 MESSAGE_FAILED 이벤트와 에러 코드·메시지를 담은 ErrorResponse를 반환한다")
    void handleBusinessException_에러응답_구조검증() {
        BusinessException ex = new BusinessException(ErrorCode.FORBIDDEN, "전송 불가");

        ErrorResponse result = controller.handleBusinessException(ex);

        assertThat(result.event()).isEqualTo(ErrorEventType.MESSAGE_FAILED.name());
        assertThat(result.errorCode()).isEqualTo(ErrorCode.FORBIDDEN.toString());
        assertThat(result.message()).isEqualTo("전송 불가");
        assertThat(result.clientMessageId()).isNull();
    }

    @Test
    @DisplayName("handleException은 MESSAGE_FAILED 이벤트와 고정 메시지를 담은 ErrorResponse를 반환한다")
    void handleException_에러응답_구조검증() {
        ErrorResponse result = controller.handleException();

        assertThat(result.event()).isEqualTo(ErrorEventType.MESSAGE_FAILED.name());
        assertThat(result.message()).isEqualTo("메시지 전송에 실패했습니다.");
        assertThat(result.errorCode()).isNull();
        assertThat(result.clientMessageId()).isNull();
    }

    // ── markRead ──────────────────────────────────────────────

    @Test
    @DisplayName("markRead는 messageService.read를 올바른 인자로 호출한다")
    void markRead_올바른인자로호출() {
        long lastReadMessageId = 100L;
        ChatReadPayload payload = new ChatReadPayload(ROOM_ID, lastReadMessageId);

        controller.markRead(payload, principal);

        verify(messageService).read(ROOM_ID, MEMBER_ID, lastReadMessageId, true);
    }

    // ── typing ────────────────────────────────────────────────

    @Test
    @DisplayName("typing은 올바른 방에 MessageTypingEvent를 발행한다")
    void typing_타이핑이벤트발행() {
        ChatTypingPayload payload = new ChatTypingPayload(ROOM_ID, true);

        controller.typing(payload, principal);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(chatEventPublisher).publishToRoomTyping(eq(ROOM_ID), eventCaptor.capture());

        var event = (com.sportsify.chat.domain.model.event.message.MessageTypingEvent) eventCaptor.getValue();
        assertThat(event.roomId()).isEqualTo(ROOM_ID);
        assertThat(event.userId()).isEqualTo(MEMBER_ID);
        assertThat(event.typing()).isTrue();
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }
}

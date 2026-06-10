package com.sportsify.chat.infrastructure;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.domain.model.message.MessageContent;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.model.message.MessageStatus;
import com.sportsify.chat.domain.model.message.MessageType;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.MissedMessageReplayer;
import com.sportsify.chat.infrastructure.webSocket.dto.ReplayBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissedMessageReplayerTest {

    private static final long ROOM_ID = 42L;
    private static final String SESSION_ID = "sess-1";
    private static final long SESSION_MEMBER_ID = 1L;
    private static final String VALID_DESTINATION = "/topic/rooms/" + ROOM_ID;

    @InjectMocks
    private MissedMessageReplayer replayer;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatEventPublisher chatEventPublisher;

    // ──────────────────────── 가드 절 ────────────────────────

    @Test
    @DisplayName("destination이 null이면 아무 것도 하지 않는다")
    void onSubscribe_destination_null_무시() {
        replayer.onSubscribe(event(null, "0", SESSION_ID));

        verify(messageRepository, never()).findByRoomAfter(any(), any(), anyInt());
        verify(chatEventPublisher, never()).publishToUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("destination이 ROOM_TOPIC_PREFIX로 시작하지 않으면 무시한다")
    void onSubscribe_잘못된_prefix_무시() {
        replayer.onSubscribe(event("/queue/other/42", "0", SESSION_ID));

        verify(messageRepository, never()).findByRoomAfter(any(), any(), anyInt());
        verify(chatEventPublisher, never()).publishToUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("lastMessageId 헤더가 없으면 무시한다")
    void onSubscribe_lastMessageId_헤더_없음_무시() {
        replayer.onSubscribe(event(VALID_DESTINATION, null, SESSION_ID));

        verify(messageRepository, never()).findByRoomAfter(any(), any(), anyInt());
        verify(chatEventPublisher, never()).publishToUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("destination 목적지가 없으면 무시한다")
    void onSubscribe_parts_부족_무시() {
        replayer.onSubscribe(event("/topic/rooms", "0", SESSION_ID));

        verify(messageRepository, never()).findByRoomAfter(any(), any(), anyInt());
        verify(chatEventPublisher, never()).publishToUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("destination 목적지가 roomId가 아니면 무시한다")
    void onSubscribe_roomId_파싱_실패_무시() {
        replayer.onSubscribe(event("/topic/rooms/abc", "0", SESSION_ID));

        verify(messageRepository, never()).findByRoomAfter(any(), any(), anyInt());
        verify(chatEventPublisher, never()).publishToUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("lastMessageId가 숫자가 아니면 무시한다")
    void onSubscribe_afterMessageId_파싱_실패_무시() {
        replayer.onSubscribe(event(VALID_DESTINATION, "notANumber", SESSION_ID));

        verify(messageRepository, never()).findByRoomAfter(any(), any(), anyInt());
        verify(chatEventPublisher, never()).publishToUser(anyLong(), any(), any());
    }

    // ──────────────────────── 성공 경로 ────────────────────────

    @Test
    @DisplayName("미수신 메시지가 없으면 publish하지 않는다")
    void onSubscribe_미수신_없음_publish_안함() {
        given(messageRepository.findByRoomAfter(ChatRoomId.of(ROOM_ID), 0L, 11))
                .willReturn(Collections.emptyList());

        replayer.onSubscribe(event(VALID_DESTINATION, "0", SESSION_ID));

        verify(messageRepository).findByRoomAfter(ChatRoomId.of(ROOM_ID), 0L, 11);
        verify(chatEventPublisher, never()).publishToUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("REPLAY_LIMIT 이하의 메시지는 모두 REPLAY_MESSAGE로 발행된다")
    void onSubscribe_REPLAY_LIMIT_이하_모두_REPLAY_MESSAGE() {
        List<com.sportsify.chat.domain.model.message.Message> messages = messages(3, ROOM_ID);
        given(messageRepository.findByRoomAfter(ChatRoomId.of(ROOM_ID), 0L, 11))
                .willReturn(messages);

        replayer.onSubscribe(event(VALID_DESTINATION, "0", SESSION_ID));

        ArgumentCaptor<ReplayBatch> captor = ArgumentCaptor.forClass(ReplayBatch.class);
        verify(chatEventPublisher).publishToUser(eq(SESSION_MEMBER_ID), captor.capture(), eq("/queue/replay"));

        List<com.sportsify.chat.domain.model.event.EventEnvelope<MessageSentPayload>> envelopes = captor.getValue().messages();
        assertThat(envelopes).hasSize(3);
        assertThat(envelopes).allMatch(e -> EventType.REPLAY_MESSAGE.name().equals(e.event()));
    }

    @Test
    @DisplayName("정확히 REPLAY_LIMIT(10)개 메시지도 모두 REPLAY_MESSAGE다")
    void onSubscribe_REPLAY_LIMIT_정확히_모두_REPLAY_MESSAGE() {
        List<com.sportsify.chat.domain.model.message.Message> messages = messages(10, ROOM_ID);
        given(messageRepository.findByRoomAfter(ChatRoomId.of(ROOM_ID), 0L, 11))
                .willReturn(messages);

        replayer.onSubscribe(event(VALID_DESTINATION, "0", SESSION_ID));

        ArgumentCaptor<ReplayBatch> captor = ArgumentCaptor.forClass(ReplayBatch.class);
        verify(chatEventPublisher).publishToUser(eq(SESSION_MEMBER_ID), captor.capture(), eq("/queue/replay"));

        List<com.sportsify.chat.domain.model.event.EventEnvelope<MessageSentPayload>> envelopes = captor.getValue().messages();
        assertThat(envelopes).hasSize(10);
        assertThat(envelopes).allMatch(e -> EventType.REPLAY_MESSAGE.name().equals(e.event()));
    }

    @Test
    @DisplayName("REPLAY_LIMIT 초과 시 마지막 메시지는 REPLAY_OVERFLOW로 발행된다")
    void onSubscribe_REPLAY_LIMIT_초과_마지막_REPLAY_OVERFLOW() {
        List<com.sportsify.chat.domain.model.message.Message> messages = messages(11, ROOM_ID);
        given(messageRepository.findByRoomAfter(ChatRoomId.of(ROOM_ID), 0L, 11))
                .willReturn(messages);

        replayer.onSubscribe(event(VALID_DESTINATION, "0", SESSION_ID));

        ArgumentCaptor<ReplayBatch> captor = ArgumentCaptor.forClass(ReplayBatch.class);
        verify(chatEventPublisher).publishToUser(eq(SESSION_MEMBER_ID), captor.capture(), eq("/queue/replay"));

        List<com.sportsify.chat.domain.model.event.EventEnvelope<MessageSentPayload>> envelopes = captor.getValue().messages();
        assertThat(envelopes).hasSize(10);

        List<String> eventTypes = envelopes.stream().map(com.sportsify.chat.domain.model.event.EventEnvelope::event).toList();
        assertThat(eventTypes.subList(0, 9)).allMatch(t -> EventType.REPLAY_MESSAGE.name().equals(t));
        assertThat(eventTypes.getLast()).isEqualTo(EventType.REPLAY_OVERFLOW.name());
    }

    @Test
    @DisplayName("accessor에서 추출한 memberId로 publish한다")
    void onSubscribe_올바른_memberId로_publish() {
        String customSid = "custom-session-99";
        List<com.sportsify.chat.domain.model.message.Message> messages = messages(1, ROOM_ID);
        given(messageRepository.findByRoomAfter(ChatRoomId.of(ROOM_ID), 0L, 11))
                .willReturn(messages);

        replayer.onSubscribe(event(VALID_DESTINATION, "0", customSid));

        verify(chatEventPublisher).publishToUser(eq(SESSION_MEMBER_ID), any(ReplayBatch.class), eq("/queue/replay"));
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private SessionSubscribeEvent event(String destination, String lastMessageId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setUser(() -> String.valueOf(SESSION_MEMBER_ID));
        if (destination != null) accessor.setDestination(destination);
        if (lastMessageId != null) accessor.addNativeHeader("lastMessageId", lastMessageId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionSubscribeEvent(new Object(), message);
    }

    private List<com.sportsify.chat.domain.model.message.Message> messages(int count, long roomId) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> com.sportsify.chat.domain.model.message.Message.restore(
                        MessageId.of((long) i),
                        ChatRoomId.of(roomId),
                        MemberId.of(1L),
                        MessageContent.of("메시지 " + i),
                        MessageType.TEXT,
                        MessageStatus.ACTIVE,
                        Instant.now()
                ))
                .toList();
    }
}

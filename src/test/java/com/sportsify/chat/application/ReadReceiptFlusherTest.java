package com.sportsify.chat.application;

import com.sportsify.chat.application.message.service.ReadReceiptFlusher;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.message.ReadReceiptPayload;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ReadCache;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadReceiptFlusherTest {

    private static final Instant NOW = Instant.parse("2026-05-12T00:00:00Z");

    private static final ReadCache.ReadEntry ENTRY_1 = new ReadCache.ReadEntry(
            ChatRoomId.of(1L), MemberId.of(2L), MessageId.of(100L));
    private static final ReadCache.ReadEntry ENTRY_2 = new ReadCache.ReadEntry(
            ChatRoomId.of(2L), MemberId.of(3L), MessageId.of(200L));

    @Mock ReadCache readCache;
    @Mock ChatRoomMemberRepository chatRoomMemberRepo;
    @Mock ChatEventPublisher chatEventPublisher;
    @Mock TransactionTemplate transactionTemplate;

    ReadReceiptFlusher flusher;

    @BeforeEach
    void setUp() {
        flusher = new ReadReceiptFlusher(readCache, chatRoomMemberRepo, chatEventPublisher, transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenTransactionInvokesCallback() {
        given(transactionTemplate.execute(any())).willAnswer(inv -> {
            TransactionCallback<Boolean> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
    }

    // ── 정상 흐름 ───────────────────────────────────────────

    @Test
    @DisplayName("DB 갱신 성공 시 READ_RECEIPT 이벤트를 방에 발행한다")
    void flush_DB갱신성공_이벤트발행() {
        given(readCache.drainAll()).willReturn(List.of(ENTRY_1));
        givenTransactionInvokesCallback();
        given(chatRoomMemberRepo.updateLastReadMessageIfGreater(
                eq(ChatRoomId.of(1L)), eq(MemberId.of(2L)), eq(MessageId.of(100L)), eq(NOW)))
                .willReturn(true);

        flusher.flush();

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(chatEventPublisher).publishToRoom(eq(1L), captor.capture());

        EventEnvelope event = captor.getValue();
        assertThat(event.event()).isEqualTo(EventType.READ_RECEIPT.name());
        ReadReceiptPayload payload = (ReadReceiptPayload) event.payload();
        assertThat(payload.memberId()).isEqualTo(2L);
        assertThat(payload.lastReadMessageId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("DB 갱신 대상 없음(false) 시 이벤트를 발행하지 않는다")
    void flush_DB갱신불필요_이벤트미발행() {
        given(readCache.drainAll()).willReturn(List.of(ENTRY_1));
        givenTransactionInvokesCallback();
        given(chatRoomMemberRepo.updateLastReadMessageIfGreater(any(), any(), any(), any()))
                .willReturn(false);

        flusher.flush();

        verify(chatEventPublisher, never()).publishToRoom(anyLong(), any());
    }

    @Test
    @DisplayName("여러 항목을 각각 처리한다")
    void flush_복수항목_각각처리() {
        given(readCache.drainAll()).willReturn(List.of(ENTRY_1, ENTRY_2));
        givenTransactionInvokesCallback();
        given(chatRoomMemberRepo.updateLastReadMessageIfGreater(any(), any(), any(), any()))
                .willReturn(false);

        flusher.flush();

        verify(chatRoomMemberRepo, times(2)).updateLastReadMessageIfGreater(any(), any(), any(), any());
    }

    // ── 조기 리턴 케이스 ────────────────────────────────────

    @Test
    @DisplayName("drainAll 결과가 없으면 아무 작업도 하지 않는다")
    void flush_빈목록_아무동작없음() {
        given(readCache.drainAll()).willReturn(List.of());

        flusher.flush();

        verifyNoInteractions(transactionTemplate, chatEventPublisher);
    }

    // ── 예외 처리 ───────────────────────────────────────────

    @Test
    @DisplayName("트랜잭션 실행 중 예외가 발생해도 나머지 항목을 계속 처리한다")
    void flush_트랜잭션예외_계속진행() {
        given(readCache.drainAll()).willReturn(List.of(ENTRY_1, ENTRY_2));
        given(transactionTemplate.execute(any()))
                .willThrow(new RuntimeException("DB 오류"))
                .willReturn(false);

        assertThatNoException().isThrownBy(() -> flusher.flush());
        verify(transactionTemplate, times(2)).execute(any());
    }

    @Test
    @DisplayName("drainAll 자체가 실패해도 예외가 전파되지 않는다")
    void flush_drainAll예외_예외미전파() {
        given(readCache.drainAll()).willThrow(new RuntimeException("Redis 연결 실패"));

        assertThatNoException().isThrownBy(() -> flusher.flush());
        verifyNoInteractions(transactionTemplate, chatEventPublisher);
    }
}

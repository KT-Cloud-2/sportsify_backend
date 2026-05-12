package com.sportsify.chat.application;

import com.sportsify.chat.application.message.service.ReadReceiptFlusher;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.message.ReadReceiptPayload;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadReceiptFlusherTest {

    private static final Instant NOW = Instant.parse("2026-05-12T00:00:00Z");
    private static final String VALID_KEY = "chat:read:1:2";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ChatRoomMemberRepository chatRoomMemberRepo;
    @Mock ChatEventPublisher chatEventPublisher;
    @Mock TransactionTemplate transactionTemplate;
    @Mock ValueOperations<String, String> valueOps;

    ReadReceiptFlusher flusher;

    @BeforeEach
    void setUp() {
        flusher = new ReadReceiptFlusher(redisTemplate, chatRoomMemberRepo, chatEventPublisher, transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── 헬퍼 ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Cursor<String> cursorOf(String... keys) {
        Cursor<String> cursor = mock(Cursor.class);
        // Mockito는 인터페이스 default 메서드를 by default no-op 처리한다.
        // forEachRemaining은 Iterator의 default 메서드이므로 직접 mock해야 flushOne이 호출된다.
        doAnswer(inv -> {
            Consumer<String> consumer = inv.getArgument(0);
            for (String key : keys) {
                consumer.accept(key);
            }
            return null;
        }).when(cursor).forEachRemaining(any());
        return cursor;
    }

    private void givenScan(Cursor<String> cursor) {
        // cursor를 미리 생성한 뒤 전달해야 한다.
        // given(redisTemplate.scan(any())).willReturn(cursorOf(...)) 처럼 인수 안에서
        // cursorOf를 호출하면, scan stub이 열린 상태에서 cursor 내부 mock 메서드가 호출돼
        // UnfinishedStubbingException이 발생한다.
        given(redisTemplate.scan(any())).willReturn(cursor);
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
        Cursor<String> cursor = cursorOf(VALID_KEY);
        givenScan(cursor);
        given(valueOps.getAndDelete(VALID_KEY)).willReturn("100");
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
        Cursor<String> cursor = cursorOf(VALID_KEY);
        givenScan(cursor);
        given(valueOps.getAndDelete(VALID_KEY)).willReturn("100");
        givenTransactionInvokesCallback();
        given(chatRoomMemberRepo.updateLastReadMessageIfGreater(any(), any(), any(), any()))
                .willReturn(false);

        flusher.flush();

        verify(chatEventPublisher, never()).publishToRoom(anyLong(), any());
    }

    @Test
    @DisplayName("여러 키를 순서대로 처리한다")
    void flush_복수키_각각처리() {
        String key2 = "chat:read:2:3";
        Cursor<String> cursor = cursorOf(VALID_KEY, key2);
        givenScan(cursor);
        given(valueOps.getAndDelete(VALID_KEY)).willReturn("10");
        given(valueOps.getAndDelete(key2)).willReturn("20");
        givenTransactionInvokesCallback();
        given(chatRoomMemberRepo.updateLastReadMessageIfGreater(any(), any(), any(), any()))
                .willReturn(false);

        flusher.flush();

        verify(valueOps).getAndDelete(VALID_KEY);
        verify(valueOps).getAndDelete(key2);
        verify(chatRoomMemberRepo, times(2)).updateLastReadMessageIfGreater(any(), any(), any(), any());
    }

    // ── 조기 리턴 케이스 ────────────────────────────────────

    @Test
    @DisplayName("스캔 결과가 없으면 아무 작업도 하지 않는다")
    void flush_빈스캔_아무동작없음() {
        Cursor<String> cursor = cursorOf();
        givenScan(cursor);

        flusher.flush();

        verifyNoInteractions(valueOps, transactionTemplate, chatEventPublisher);
    }

    @Test
    @DisplayName("Redis에서 키가 이미 삭제된 경우(getAndDelete=null) DB를 호출하지 않는다")
    void flush_키이미삭제_DB미호출() {
        Cursor<String> cursor = cursorOf(VALID_KEY);
        givenScan(cursor);
        given(valueOps.getAndDelete(VALID_KEY)).willReturn(null);

        flusher.flush();

        verifyNoInteractions(transactionTemplate, chatEventPublisher);
    }

    @Test
    @DisplayName("키 포맷이 잘못된 경우(파트 수 불일치) DB를 호출하지 않는다")
    void flush_키포맷오류_DB미호출() {
        String malformedKey = "chat:read:1";
        Cursor<String> cursor = cursorOf(malformedKey);
        givenScan(cursor);
        given(valueOps.getAndDelete(malformedKey)).willReturn("100");

        flusher.flush();

        verifyNoInteractions(transactionTemplate, chatEventPublisher);
    }

    // ── 예외 처리 ───────────────────────────────────────────

    @Test
    @DisplayName("키에 숫자가 아닌 값이 있어도 예외가 전파되지 않는다")
    void flush_키파싱실패_예외미전파() {
        String badKey = "chat:read:abc:def";
        Cursor<String> cursor = cursorOf(badKey);
        givenScan(cursor);
        given(valueOps.getAndDelete(badKey)).willReturn("100");

        assertThatNoException().isThrownBy(() -> flusher.flush());
        verifyNoInteractions(transactionTemplate, chatEventPublisher);
    }

    @Test
    @DisplayName("트랜잭션 실행 중 예외가 발생해도 flush가 중단되지 않는다")
    void flush_트랜잭션예외_계속진행() {
        String key2 = "chat:read:2:3";
        Cursor<String> cursor = cursorOf(VALID_KEY, key2);
        givenScan(cursor);
        given(valueOps.getAndDelete(VALID_KEY)).willReturn("100");
        given(valueOps.getAndDelete(key2)).willReturn("200");
        given(transactionTemplate.execute(any()))
                .willThrow(new RuntimeException("DB 오류"))
                .willReturn(false);

        assertThatNoException().isThrownBy(() -> flusher.flush());
        verify(valueOps).getAndDelete(key2);
    }

    @Test
    @DisplayName("Redis scan 자체가 실패해도 예외가 전파되지 않는다")
    void flush_스캔예외_예외미전파() {
        given(redisTemplate.scan(any())).willThrow(new RuntimeException("Redis 연결 실패"));

        assertThatNoException().isThrownBy(() -> flusher.flush());
        verifyNoInteractions(valueOps, transactionTemplate, chatEventPublisher);
    }
}

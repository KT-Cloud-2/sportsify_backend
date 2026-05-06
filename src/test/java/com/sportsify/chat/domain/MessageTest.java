package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.*;
import com.sportsify.chat.domain.model.message.event.DomainEvent;
import com.sportsify.chat.domain.model.message.event.MessageDeleteEvent;
import com.sportsify.chat.domain.model.message.event.MessageSentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    private static final ChatRoomId ROOM_ID = ChatRoomId.of(1L);
    private static final MemberId SENDER = MemberId.of(1L);
    private static final MessageContent CONTENT = MessageContent.of("안녕하세요");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);
    private static final LocalDateTime LATER = NOW.plusHours(1);

    // ──────────────────────── send ────────────────────────

    @Test
    @DisplayName("send는 ACTIVE 상태의 메시지를 생성하고 MessageSentEvent를 추가한다")
    void send_초기상태_검증() {
        Message msg = Message.send(ROOM_ID, SENDER, CONTENT, MessageType.TEXT, NOW);

        assertThat(msg.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(msg.getSenderId()).isEqualTo(SENDER);
        assertThat(msg.getContent()).isEqualTo(CONTENT);
        assertThat(msg.getType()).isEqualTo(MessageType.TEXT);
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.ACTIVE);
        assertThat(msg.getCreatedAt()).isEqualTo(NOW);
        assertThat(msg.getId()).isNull();
        assertThat(msg.getDomainEvents())
                .hasSize(1)
                .first().isInstanceOf(MessageSentEvent.class);
    }

    // ──────────────────────── system ────────────────────────

    @Test
    @DisplayName("system은 SYSTEM 타입의 ACTIVE 메시지를 생성하고 도메인 이벤트가 없다")
    void system_초기상태_검증() {
        Message msg = Message.system(ROOM_ID, SENDER, CONTENT, NOW);

        assertThat(msg.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.ACTIVE);
        assertThat(msg.getId()).isNull();
        assertThat(msg.getDomainEvents()).isEmpty();
    }

    // ──────────────────────── restore ────────────────────────

    @Test
    @DisplayName("restore는 전달된 값으로 메시지를 복원하고 도메인 이벤트가 없다")
    void restore_복원검증() {
        Message msg = Message.restore(
                MessageId.of(100L), ROOM_ID, SENDER, CONTENT,
                MessageType.TEXT, MessageStatus.ACTIVE, NOW);

        assertThat(msg.getId()).isEqualTo(MessageId.of(100L));
        assertThat(msg.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(msg.getSenderId()).isEqualTo(SENDER);
        assertThat(msg.getContent()).isEqualTo(CONTENT);
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.ACTIVE);
        assertThat(msg.getCreatedAt()).isEqualTo(NOW);
        assertThat(msg.getDomainEvents()).isEmpty();
    }

    // ──────────────────────── assignId ────────────────────────

    @Test
    @DisplayName("새 메시지에 id를 부여한다")
    void assignId_부여성공() {
        Message msg = Message.send(ROOM_ID, SENDER, CONTENT, MessageType.TEXT, NOW);

        msg.assignId(MessageId.of(50L));

        assertThat(msg.getId()).isEqualTo(MessageId.of(50L));
    }

    // ──────────────────────── pullDomainEvents ────────────────────────

    @Test
    @DisplayName("pullDomainEvents는 이벤트를 반환하고 내부 목록을 비운다")
    void pullDomainEvents_반환후_초기화() {
        Message msg = Message.send(ROOM_ID, SENDER, CONTENT, MessageType.TEXT, NOW);

        List<DomainEvent> events = msg.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(msg.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("pullDomainEvents를 두 번 호출하면 두 번째는 빈 목록을 반환한다")
    void pullDomainEvents_두번호출_멱등() {
        Message msg = Message.send(ROOM_ID, SENDER, CONTENT, MessageType.TEXT, NOW);
        msg.pullDomainEvents();

        List<DomainEvent> second = msg.pullDomainEvents();

        assertThat(second).isEmpty();
    }

    // ──────────────────────── softDelete ────────────────────────

    @Test
    @DisplayName("ACTIVE 메시지를 softDelete하면 DELETED 상태로 변경되고 MessageDeleteEvent가 추가된다")
    void softDelete_상태변경() {
        Message msg = restored(MessageStatus.ACTIVE);

        msg.softDelete(SENDER, LATER);

        assertThat(msg.getStatus()).isEqualTo(MessageStatus.DELETED);
        assertThat(msg.isDeleted()).isTrue();
        assertThat(msg.getDomainEvents())
                .hasSize(1)
                .first().isInstanceOf(MessageDeleteEvent.class);
    }

    @Test
    @DisplayName("이미 DELETED 메시지를 softDelete해도 도메인 이벤트가 추가되지 않는다")
    void softDelete_DELETED_멱등() {
        Message msg = restored(MessageStatus.DELETED);

        msg.softDelete(SENDER, LATER);

        assertThat(msg.getStatus()).isEqualTo(MessageStatus.DELETED);
        assertThat(msg.getDomainEvents()).isEmpty();
    }

    // ──────────────────────── isSentBy / isDeleted ────────────────────────

    @Test
    @DisplayName("isSentBy는 같은 senderId이면 true, 다른 senderId이면 false를 반환한다")
    void isSentBy_검증() {
        Message msg = restored(MessageStatus.ACTIVE);

        assertThat(msg.isSentBy(SENDER)).isTrue();
        assertThat(msg.isSentBy(MemberId.of(999L))).isFalse();
    }

    @Test
    @DisplayName("isDeleted는 DELETED 상태이면 true, ACTIVE이면 false를 반환한다")
    void isDeleted_검증() {
        Message active = restored(MessageStatus.ACTIVE);
        Message deleted = restored(MessageStatus.DELETED);

        assertThat(active.isDeleted()).isFalse();
        assertThat(deleted.isDeleted()).isTrue();
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private Message restored(MessageStatus status) {
        return Message.restore(
                MessageId.of(1L), ROOM_ID, SENDER, CONTENT,
                MessageType.TEXT, status, NOW);
    }
}

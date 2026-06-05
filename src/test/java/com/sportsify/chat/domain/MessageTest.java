package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.message.MessageDeletePayLoad;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.domain.model.message.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTest {

    private static final ChatRoomId ROOM_ID = ChatRoomId.of(1L);
    private static final MemberId SENDER = MemberId.of(1L);
    private static final MessageContent CONTENT = MessageContent.of("안녕하세요");
    private static final Instant INSTANT_NOW = Instant.parse("2026-05-06T12:00:00Z");

    // ──────────────────────── send ────────────────────────

    @Test
    @DisplayName("send는 ACTIVE 상태의 메시지를 생성하고 id와 이벤트는 아직 없다")
    void send_초기상태_검증() {
        String clientId = UUID.randomUUID().toString();
        Message msg = Message.send(ROOM_ID, SENDER, CONTENT, MessageType.TEXT, INSTANT_NOW, clientId);

        assertThat(msg.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(msg.getSenderId()).isEqualTo(SENDER);
        assertThat(msg.getContent()).isEqualTo(CONTENT);
        assertThat(msg.getType()).isEqualTo(MessageType.TEXT);
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.ACTIVE);
        assertThat(msg.getCreatedAt()).isEqualTo(INSTANT_NOW);
        assertThat(msg.getId()).isNull();
        assertThat(msg.getEvents()).isEmpty();
    }

    // ──────────────────────── system ────────────────────────

    @Test
    @DisplayName("system은 SYSTEM 타입의 ACTIVE 메시지를 생성하고 도메인 이벤트가 없다")
    void system_초기상태_검증() {
        Message msg = Message.system(ROOM_ID, SENDER, CONTENT, INSTANT_NOW);

        assertThat(msg.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.ACTIVE);
        assertThat(msg.getId()).isNull();
        assertThat(msg.getEvents()).isEmpty();
    }

    // ──────────────────────── restore ────────────────────────

    @Test
    @DisplayName("restore는 전달된 값으로 메시지를 복원하고 도메인 이벤트가 없다")
    void restore_복원검증() {
        Message msg = Message.restore(
                MessageId.of(100L), ROOM_ID, SENDER, CONTENT,
                MessageType.TEXT, MessageStatus.ACTIVE, INSTANT_NOW);

        assertThat(msg.getId()).isEqualTo(MessageId.of(100L));
        assertThat(msg.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(msg.getSenderId()).isEqualTo(SENDER);
        assertThat(msg.getContent()).isEqualTo(CONTENT);
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.ACTIVE);
        assertThat(msg.getCreatedAt()).isEqualTo(INSTANT_NOW);
        assertThat(msg.getEvents()).isEmpty();
    }

    // ──────────────────────── assignId ────────────────────────

    @Test
    @DisplayName("assignId 호출 시 MessageSentPayload 이벤트가 등록된다")
    void assignId_이벤트등록() {
        String clientId = UUID.randomUUID().toString();
        Message msg = Message.send(ROOM_ID, SENDER, CONTENT, MessageType.TEXT, INSTANT_NOW, clientId);

        msg.assignId(MessageId.of(99L));

        assertThat(msg.getId()).isEqualTo(MessageId.of(99L));
        assertThat(msg.getEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(EventEnvelope.class)
                .satisfies(e -> assertThat(((EventEnvelope<?>) e).payload())
                        .isInstanceOf(MessageSentPayload.class));
    }

    @Test
    @DisplayName("system 메시지에 assignId를 호출하면 이벤트가 등록되지 않는다")
    void assignId_system메시지_이벤트_없음() {
        Message msg = Message.system(ROOM_ID, SENDER, CONTENT, INSTANT_NOW);

        msg.assignId(MessageId.of(99L));

        assertThat(msg.getId()).isEqualTo(MessageId.of(99L));
        assertThat(msg.getEvents()).isEmpty();
    }

    @Test
    @DisplayName("이미 id가 부여된 메시지에 재부여하면 예외가 발생한다")
    void assignId_중복_예외() {
        Message msg = restored(MessageStatus.ACTIVE);

        assertThatThrownBy(() -> msg.assignId(MessageId.of(99L)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ──────────────────────── softDelete ────────────────────────

    @Test
    @DisplayName("ACTIVE 메시지를 softDelete하면 DELETED 상태로 변경되고 MessageDeleteEvent가 추가된다")
    void softDelete_상태변경() {
        Message msg = restored(MessageStatus.ACTIVE);

        msg.softDelete(INSTANT_NOW);

        assertThat(msg.getStatus()).isEqualTo(MessageStatus.DELETED);
        assertThat(msg.isDeleted()).isTrue();
        assertThat(msg.getEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(EventEnvelope.class)
                .satisfies(e -> assertThat(((EventEnvelope<?>) e).payload())
                        .isInstanceOf(MessageDeletePayLoad.class));
    }

    @Test
    @DisplayName("이미 DELETED 메시지를 softDelete해도 도메인 이벤트가 추가되지 않는다")
    void softDelete_DELETED_멱등() {
        Message msg = restored(MessageStatus.DELETED);

        msg.softDelete(INSTANT_NOW);

        assertThat(msg.getStatus()).isEqualTo(MessageStatus.DELETED);
        assertThat(msg.getEvents()).isEmpty();
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

    // ──────────────────────── createAlert ────────────────────────

    @Test
    @DisplayName("createAlert는 senderId가 null인 SYSTEM 타입 ACTIVE 메시지를 생성한다")
    void createAlert_초기상태_검증() {
        Message msg = Message.createAlert(ROOM_ID, CONTENT, INSTANT_NOW);

        assertThat(msg.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(msg.getSenderId()).isNull();
        assertThat(msg.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.ACTIVE);
        assertThat(msg.getId()).isNull();
        assertThat(msg.getEvents()).isEmpty();
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private Message restored(MessageStatus status) {
        return Message.restore(
                MessageId.of(1L), ROOM_ID, SENDER, CONTENT,
                MessageType.TEXT, status, INSTANT_NOW);
    }
}

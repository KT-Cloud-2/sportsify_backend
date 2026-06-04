package com.sportsify.chat.domain.model.message;


import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.message.MessageDeletePayLoad;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import lombok.Getter;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.DomainEvents;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/**
 * 메시지 Aggregate Root
 */
@Getter
public class Message extends AbstractAggregateRoot<Message> {

    private final ChatRoomId roomId;
    private final MemberId senderId;
    private final MessageType type; //TEXT, IMAGE, FILE, SYSTEM
    private final Instant createdAt;
    private final MessageContent content;
    private MessageId id;
    private MessageStatus status;   // ACTIVE, DELETED
    private String pendingClientMessageId;  // assignId 후 이벤트 등록에 사용

    private Message(MessageId id,
                    ChatRoomId roomId,
                    MemberId senderId,
                    MessageContent content,
                    MessageType type,
                    MessageStatus status,
                    Instant createdAt) {
        this.id = id;
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.senderId = senderId; // nullable — system/alert messages have no sender
        this.content = Objects.requireNonNull(content, "content");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 새 메시지 발송
     */
    public static Message send(ChatRoomId roomId,
                               MemberId senderId,
                               MessageContent content,
                               MessageType type,
                               Instant now,
                               String clientMessageId) {
        Message msg = new Message(null, roomId, senderId, content, type, MessageStatus.ACTIVE, now);
        msg.pendingClientMessageId = clientMessageId; // ID 할당 후 이벤트 등록
        return msg;
    }

    /**
     * 알림 메시지 생성
     */
    public static Message createAlert(ChatRoomId roomId,
                                      MessageContent content,
                                      Instant now) {
        return new Message(null, roomId, null, content, MessageType.SYSTEM, MessageStatus.ACTIVE, now);
    }

    /**
     * 시스템 메시지 생성
     */
    public static Message system(ChatRoomId roomId,
                                 MemberId systemSenderId,
                                 MessageContent content,
                                 Instant now) {
        return new Message(null, roomId, systemSenderId, content, MessageType.SYSTEM,
                MessageStatus.ACTIVE, now);
    }

    /**
     * 영속 데이터로부터 복원 (Infrastructure 매퍼 전용)
     */
    public static Message restore(MessageId id,
                                  ChatRoomId roomId,
                                  MemberId senderId,
                                  MessageContent content,
                                  MessageType type,
                                  MessageStatus status,
                                  Instant createdAt) {
        Objects.requireNonNull(id, "id");
        return new Message(id, roomId, senderId, content, type, status, createdAt);
    }

    /**
     * id 저장
     */
    public void assignId(MessageId id) {
        if (this.id != null) {
            throw new IllegalStateException("MessageId already assigned");
        }
        this.id = Objects.requireNonNull(id, "id");
        if (pendingClientMessageId != null) {
            registerEvent(EventEnvelope.of(EventType.MESSAGE_SENT, roomId, createdAt,
                    MessageSentPayload.from(this, pendingClientMessageId)));
            pendingClientMessageId = null;
        }
    }

    /**
     * 메시지를 논리 삭제
     */
    public void softDelete(Instant now) {
        if (this.status == MessageStatus.DELETED) {
            return;
        }
        this.status = MessageStatus.DELETED;
        registerEvent(EventEnvelope.of(EventType.MESSAGE_DELETED, this.roomId, now, MessageDeletePayLoad.from(this)));
    }



    /* -------------------- 상태값 체크 -------------------- */

    public boolean isSentBy(MemberId memberId) {
        return this.senderId.equals(memberId);
    }

    public boolean isDeleted() {
        return status == MessageStatus.DELETED;
    }

    @DomainEvents
    public Collection<Object> getEvents() {
        return super.domainEvents();
    }


}

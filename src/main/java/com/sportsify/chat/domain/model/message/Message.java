package com.sportsify.chat.domain.model.message;


import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 메시지 Aggregate Root
 */
@Getter
public class Message {

    private final ChatRoomId roomId;
    private final MemberId senderId;
    private final MessageType type; //TEXT, IMAGE, FILE, SYSTEM
    private final LocalDateTime createdAt;
    private MessageId id;
    private MessageContent content;
    private MessageStatus status;   // ACTIVE, DELETED

    private Message(MessageId id,
                    ChatRoomId roomId,
                    MemberId senderId,
                    MessageContent content,
                    MessageType type,
                    MessageStatus status,
                    LocalDateTime createdAt) {
        this.id = id;
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
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
                               LocalDateTime now) {
        return new Message(null, roomId, senderId, content, type, MessageStatus.ACTIVE, now);
    }

    /**
     * 시스템 메시지 생성
     */
    public static Message system(ChatRoomId roomId,
                                 MemberId systemSenderId,
                                 MessageContent content,
                                 LocalDateTime now) {
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
                                  LocalDateTime createdAt) {
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
    }

    /**
     * 메시지를 논리 삭제
     */
    public void softDelete(MemberId senderId) {
        Objects.requireNonNull(senderId, "senderId");
        if (!senderId.equals(this.senderId)) {
            throw new IllegalStateException("Cannot delete message because senderId does not match");
        }
        if (this.status == MessageStatus.DELETED) {
            return;
        }
        this.status = MessageStatus.DELETED;
    }

    /**
     * 본문 수정
     */
    public void edit(MessageContent newContent, MemberId senderId) {
        if (!senderId.equals(this.senderId)) {
            throw new IllegalStateException("Cannot edit message because senderId does not match");
        }
        if (this.type == MessageType.SYSTEM) {
            throw new IllegalStateException("System message cannot be edited");
        }
        if (this.status == MessageStatus.DELETED) {
            throw new IllegalStateException("Deleted message cannot be edited");
        }
        Objects.requireNonNull(newContent, "newContent");
        this.content = newContent;
    }

    /* -------------------- 상태값 체크 -------------------- */

    public boolean isSentBy(MemberId memberId) {
        return this.senderId.equals(memberId);
    }

    public boolean isDeleted() {
        return status == MessageStatus.DELETED;
    }


}
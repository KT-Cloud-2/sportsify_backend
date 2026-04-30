package com.sportsify.chat.domain.model.chatRoom;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 채팅방 Aggregate Root.
 */
@Getter
public class ChatRoom {

    private final ChatRoomType type;    // DIRECT, GAME;
    private final GameId gameId;
    private final LocalDateTime createdAt;
    private final MemberId createdBy;
    private ChatRoomId id;
    private ChatRoomName name;
    private String imageUrl;    // nullable
    private LocalDateTime updatedAt;
    private ChatRoomStatus status;  // ACTIVE, ARCHIVED, DELETED

    private ChatRoom(ChatRoomId id,
                     ChatRoomName name,
                     ChatRoomType type,
                     String imageUrl,
                     GameId gameId,
                     LocalDateTime createdAt,
                     LocalDateTime updatedAt,
                     ChatRoomStatus status,
                     MemberId createdBy) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.imageUrl = imageUrl;
        this.gameId = gameId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    }

    /**
     * 새 채팅방 생성
     */
    public static ChatRoom create(ChatRoomName name,
                                  ChatRoomType type,
                                  String imageUrl,
                                  GameId gameId,
                                  MemberId createdBy,
                                  LocalDateTime now) {
        if (type == ChatRoomType.GAME && gameId == null) {
            throw new IllegalArgumentException("GAME type requires gameId");
        }
        return new ChatRoom(
                null, name, type, imageUrl, gameId,
                now, now, ChatRoomStatus.ACTIVE,
                createdBy
        );
    }

    /**
     * 영속 저장된 데이터 복원 (Infrastructure 매퍼 전용)
     */
    public static ChatRoom restore(ChatRoomId id,
                                   ChatRoomName name,
                                   ChatRoomType type,
                                   String imageUrl,
                                   GameId gameId,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt,
                                   ChatRoomStatus status,
                                   MemberId createdBy) {
        Objects.requireNonNull(id, "id");
        return new ChatRoom(
                id, name, type, imageUrl, gameId,
                createdAt, updatedAt, status, createdBy
        );
    }

    /**
     * chatRoom ID 부여. (Infrastructure 전용)
     */
    public void assignId(ChatRoomId id) {
        if (this.id != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "ChatRoomId already assigned");
        }
        this.id = Objects.requireNonNull(id, "id");
    }


    /**
     * 채팅방 이름 변경
     */
    public void rename(ChatRoomName newName, LocalDateTime now, MemberId createdBy) {
        ensureActive();
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(createdBy, "createdBy");
        if (!createdBy.equals(this.createdBy)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only room leader can change name");
        }
        if (this.name.equals(newName)) {
            return;
        }
        this.name = newName;
        this.updatedAt = now;
    }

    /**
     * 채팅방 이미지 변경
     */
    public void changeImage(String imageUrl, LocalDateTime now, MemberId createdBy) {
        ensureActive();
        Objects.requireNonNull(createdBy, "createdBy");
        if (!createdBy.equals(this.createdBy)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot edit message because leaderId does not match");
        }
        if (Objects.equals(this.imageUrl, imageUrl)) {
            return;
        }
        this.imageUrl = imageUrl;
        this.updatedAt = now;
    }

    /**
     * 데이터 영속화
     */
    public void archive(LocalDateTime now) {
        if (this.status == ChatRoomStatus.ARCHIVED) {
            return;
        }
        if (this.status == ChatRoomStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Cannot archive deleted room");
        }
        this.status = ChatRoomStatus.ARCHIVED;
        this.updatedAt = now;
    }

    /**
     * 채팅방 삭제
     */
    public void delete(LocalDateTime now, MemberId createdBy) {
        Objects.requireNonNull(createdBy, "createdBy");
        if (!createdBy.equals(this.createdBy)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot edit message because leaderId does not match");
        }
        if (this.status == ChatRoomStatus.DELETED) {
            return;
        }
        this.status = ChatRoomStatus.DELETED;
        this.updatedAt = now;
    }

    /* -------------------- 상태값 체크 -------------------- */

    private void ensureActive() {
        if (status != ChatRoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ChatRoom is not active: status=" + status);
        }
    }


}
package com.sportsify.chat.domain.model.chatRoomMember;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.MessageId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 채팅방 멤버 Aggregate Root
 */
@Getter
public class ChatRoomMember {

    private final ChatRoomId roomId;
    private final MemberId memberId;
    private final LocalDateTime joinedAt;
    private Long id;
    private MemberStatus status;    // INVITED, JOINED, LEFT, BANNED
    private boolean notificationEnabled;
    private LocalDateTime updatedAt;
    private Long lastReadMessageId;

    private ChatRoomMember(Long id,
                           ChatRoomId roomId,
                           MemberId memberId,
                           MemberStatus status,
                           boolean notificationEnabled,
                           LocalDateTime joinedAt,
                           LocalDateTime updatedAt,
                           Long lastReadMessageId) {
        this.id = id;
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.status = Objects.requireNonNull(status, "status");
        this.notificationEnabled = notificationEnabled;
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.lastReadMessageId = lastReadMessageId;
    }

    /**
     * 새 입장(공개 방 또는 자기 의지로 join)
     */
    public static ChatRoomMember newJoin(ChatRoomId roomId, MemberId memberId, LocalDateTime now) {
        return new ChatRoomMember(
                null, roomId, memberId, MemberStatus.JOINED,
                true, now, now, null
        );
    }

    /**
     * 비공개 방으로 초대 발송
     */
    public static ChatRoomMember newInvited(ChatRoomId roomId, MemberId memberId, LocalDateTime now) {
        return new ChatRoomMember(
                null, roomId, memberId, MemberStatus.INVITED,
                true, now, now, null
        );
    }

    /**
     * 영속 저장된 데이터 복원 (Infrastructure 매퍼 전용)
     */
    public static ChatRoomMember restore(Long id,
                                         ChatRoomId roomId,
                                         MemberId memberId,
                                         MemberStatus status,
                                         boolean notificationEnabled,
                                         LocalDateTime joinedAt,
                                         LocalDateTime updatedAt,
                                         Long lastReadMessageId) {
        Objects.requireNonNull(id, "id");
        return new ChatRoomMember(
                id, roomId, memberId, status,
                notificationEnabled, joinedAt, updatedAt, lastReadMessageId
        );
    }

    /**
     * 채팅방 멤버 정보 ID 저장
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ChatRoomMember id already assigned");
        }
        this.id = Objects.requireNonNull(id, "id");
    }


    /**
     * 초대 수락 또는 LEFT 상태에서 재입장
     */
    public void accept(LocalDateTime now) {
        if (this.status == MemberStatus.BANNED || this.status == MemberStatus.DELETED) {
            throw new IllegalStateException("this member cannot join");
        }
        if (this.status == MemberStatus.JOINED) {
            return;
        }
        this.status = MemberStatus.JOINED;
        this.updatedAt = now;
    }

    /**
     * 채팅방 퇴장
     */
    public void leave(LocalDateTime now) {
        if (this.status == MemberStatus.LEFT) {
            return;
        }
        if (this.status == MemberStatus.BANNED || this.status == MemberStatus.DELETED) {
            throw new IllegalStateException("this member cannot leave");
        }
        this.status = MemberStatus.LEFT;
        this.updatedAt = now;
    }

    /**
     * 사용자 일괄 퇴장(roomId)
     */
    public void delete(LocalDateTime now) {
        if (this.status == MemberStatus.DELETED) {
            return;
        }
        this.status = MemberStatus.DELETED;
        this.updatedAt = now;
    }

    /**
     * 사용자 BAN 처리
     */
    public void ban(LocalDateTime now) {
        if (this.status == MemberStatus.BANNED) {
            return;
        }
        this.status = MemberStatus.BANNED;
        this.updatedAt = now;
    }

    /**
     * 사용자 알림 상태 변경
     */
    public void changeNotification(boolean enabled, LocalDateTime now) {
        if (this.notificationEnabled == enabled) {
            return;
        }
        this.notificationEnabled = enabled;
        this.updatedAt = now;
    }

    /**
     * 마지막으로 읽은 메시지 갱신
     */
    public void updateLastReadMessage(MessageId messageId, LocalDateTime now) {
        if (!isJoined()) {
            throw new IllegalStateException(
                    "Only joined member can mark read: status=" + status);
        }
        if (this.lastReadMessageId != null && messageId.value() <= this.lastReadMessageId) {
            return;
        }
        this.lastReadMessageId = messageId.value();
        this.updatedAt = now;
    }

    /**
     * 사용자의 status 변경
     */
    public void changeStatusToInvite(LocalDateTime now) {
        if (this.status == MemberStatus.BANNED) {
            throw new IllegalStateException("Banned user cannot be reinvited");
        }
        if (this.status == MemberStatus.JOINED) {
            throw new IllegalStateException("Already joined member");
        }
        if (this.status == MemberStatus.INVITED) return;
        this.status = MemberStatus.INVITED;
        this.updatedAt = now;
    }

    /* -------------------- 상태값 체크 -------------------- */

    public boolean isJoined() {
        return status == MemberStatus.JOINED;
    }

    public boolean isInvited() {
        return status == MemberStatus.INVITED;
    }

    public boolean isBanned() {
        return status == MemberStatus.BANNED;
    }

    public boolean belongsTo(ChatRoomId roomId) {
        return this.roomId.equals(roomId);
    }

    public boolean is(MemberId memberId) {
        return this.memberId.equals(memberId);
    }

    /* -------------------- Enums -------------------- */

}
package com.sportsify.chat.infrastructure.persistence.chatRoomMember;


import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * chat_room_members 테이블 매핑 영속 엔티티.
 */
@Getter
@Entity
@Table(name = "chat_room_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_room_members_room_member",
                        columnNames = {"room_id", "member_id"})
        },
        indexes = {
                @Index(name = "idx_chat_room_members_room", columnList = "room_id"),
                @Index(name = "idx_chat_room_members_member", columnList = "member_id") //유용성 검사 필요
        })
public class ChatRoomMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    protected ChatRoomMemberJpaEntity() {
        // for JPA
    }

    public ChatRoomMemberJpaEntity(Long id,
                                   Long roomId,
                                   Long memberId,
                                   String status,
                                   boolean notificationEnabled,
                                   LocalDateTime joinedAt,
                                   LocalDateTime updatedAt,
                                   Long lastReadMessageId) {
        this.id = id;
        this.roomId = roomId;
        this.memberId = memberId;
        this.status = status;
        this.notificationEnabled = notificationEnabled;
        this.joinedAt = joinedAt;
        this.updatedAt = updatedAt;
        this.lastReadMessageId = lastReadMessageId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setNotificationEnabled(boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setLastReadMessageId(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }
}
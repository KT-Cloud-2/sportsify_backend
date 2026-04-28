package com.sportsify.chat.infrastructure.persistence.chatRoomMember;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.chatRoomMember.MemberStatus;
import org.springframework.stereotype.Component;

@Component
public class ChatRoomMemberMapper {

    public ChatRoomMember toDomain(ChatRoomMemberJpaEntity entity) {
        return ChatRoomMember.restore(
                entity.getId(),
                ChatRoomId.of(entity.getRoomId()),
                MemberId.of(entity.getMemberId()),
                MemberStatus.valueOf(entity.getStatus()),
                entity.isNotificationEnabled(),
                entity.getJoinedAt(),
                entity.getUpdatedAt(),
                entity.getLastReadMessageId()
        );
    }

    public ChatRoomMemberJpaEntity toNewJpaEntity(ChatRoomMember domain) {
        return new ChatRoomMemberJpaEntity(
                null,
                domain.getRoomId().value(),
                domain.getMemberId().value(),
                domain.getStatus().name(),
                domain.isNotificationEnabled(),
                domain.getJoinedAt(),
                domain.getUpdatedAt(),
                domain.getLastReadMessageId()
        );
    }

    public void applyToJpa(ChatRoomMemberJpaEntity entity, ChatRoomMember domain) {
        entity.setStatus(domain.getStatus().name());
        entity.setNotificationEnabled(domain.isNotificationEnabled());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setLastReadMessageId(domain.getLastReadMessageId());
        // roomId, memberId, joinedAt 은 변경 불가
    }
}

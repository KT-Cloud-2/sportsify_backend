package com.sportsify.chat.application.webSocket;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChatRoomSubscribeAccessChecker implements ChatRoomAccessChecker {

    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean canSubscribe(ChatRoom chatRoom, Optional<MemberId> memberId) {
        switch (chatRoom.getStatus()) {
            case ACTIVE, EMPTY -> {
            }
            case ARCHIVED, DELETED -> {
                return false;
            }
            default -> {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unknown room status");
            }
        }
        if (chatRoom.getType() == ChatRoomType.GAME) return true;
        return memberId.filter(id -> chatRoomMemberRepository.existsJoinedByRoomAndMember(chatRoom.getId(), id)).isPresent();
    }

    @Override
    @Transactional
    public boolean canSubscribeForUpdate(ChatRoomId roomId, Optional<MemberId> memberId) {
        return memberId.filter(id ->
                chatRoomMemberRepository.existsJoinedByRoomAndMemberForUpdate(roomId, id)
        ).isPresent();
    }

}

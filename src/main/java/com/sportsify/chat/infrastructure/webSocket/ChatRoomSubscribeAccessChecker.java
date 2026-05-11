package com.sportsify.chat.infrastructure.webSocket;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.infrastructure.webSocket.StompAuthChannelInterceptor.ChatRoomAccessChecker;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatRoomSubscribeAccessChecker implements ChatRoomAccessChecker {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean canSubscribe(ChatRoom chatRoom, MemberId memberId) {
        if (chatRoom == null) return false;
        switch (chatRoom.getStatus()) {
            case ACTIVE -> {
            }
            case ARCHIVED, DELETED -> {
                return false;
            }
            default -> {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unknown room status");
            }
        }
        if (chatRoom.getType() == ChatRoomType.GAME) return true;
        return chatRoomMemberRepository.existsJoinedByRoomAndMember(chatRoom.getId(), memberId);
    }


}

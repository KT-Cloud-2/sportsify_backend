package com.sportsify.chat.application.webSocket;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.MemberId;

import java.util.Optional;

public interface ChatRoomAccessChecker {
    boolean canSubscribe(ChatRoom room, Optional<MemberId> memberId);
}

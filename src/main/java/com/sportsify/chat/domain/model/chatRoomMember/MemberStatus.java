package com.sportsify.chat.domain.model.chatRoomMember;

public enum MemberStatus {
    INVITED, JOINED, LEFT, BANNED, DELETED;

    public boolean isJoined() {
        return this == JOINED;
    }

    public boolean isActive() {
        return this == JOINED || this == INVITED;
    }
}

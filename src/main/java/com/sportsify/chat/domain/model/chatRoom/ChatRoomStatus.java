package com.sportsify.chat.domain.model.chatRoom;

public enum ChatRoomStatus {
    ACTIVE, ARCHIVED, DELETED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}

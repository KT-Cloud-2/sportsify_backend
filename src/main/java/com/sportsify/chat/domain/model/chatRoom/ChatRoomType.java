package com.sportsify.chat.domain.model.chatRoom;

public enum ChatRoomType {
    DIRECT, GAME;

    public boolean isDirect() {
        return this == DIRECT;
    }
}
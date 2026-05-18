package com.sportsify.chat.domain.model.message;


public enum MessageStatus {
    ACTIVE, DELETED;

    public boolean isDeleted() {
        return this == DELETED;
    }
}

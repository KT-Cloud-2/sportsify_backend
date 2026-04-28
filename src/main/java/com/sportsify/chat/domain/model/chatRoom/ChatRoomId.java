package com.sportsify.chat.domain.model.chatRoom;


import java.util.Objects;

/**
 * 채팅방 id VO
 * - 채팅 id는 null과 음수는 될 수 없다.
 */
public final class ChatRoomId {

    private final Long value;

    private ChatRoomId(Long value) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("ChatRoomId must be positive");
        }
        this.value = value;
    }

    public static ChatRoomId of(Long value) {
        return new ChatRoomId(value);
    }

    public Long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRoomId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
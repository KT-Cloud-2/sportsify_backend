package com.sportsify.chat.domain.model.chatRoom;


import java.util.Objects;

/**
 * 채팅방 이름(제목) VO
 * - 비어있을 수 없으며 최대 50자
 */
public final class ChatRoomName {

    public static final int MAX_LENGTH = 50;

    private final String value;

    private ChatRoomName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ChatRoomName must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "ChatRoomName must be <= " + MAX_LENGTH +
                            " characters but " + trimmed.length());
        }
        this.value = trimmed;
    }

    public static ChatRoomName of(String value) {
        return new ChatRoomName(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRoomName that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
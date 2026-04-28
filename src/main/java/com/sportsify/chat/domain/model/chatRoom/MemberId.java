package com.sportsify.chat.domain.model.chatRoom;

import java.util.Objects;

public final class MemberId {
    private final Long value;

    private MemberId(Long value) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("ChatRoomId must be positive");
        }
        this.value = value;
    }

    public static MemberId of(Long value) {
        return new MemberId(value);
    }

    public Long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

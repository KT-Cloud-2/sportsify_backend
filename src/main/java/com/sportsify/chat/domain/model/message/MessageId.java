package com.sportsify.chat.domain.model.message;

import java.util.Objects;


/**
 * 메시지 id VO
 * - 메시지 id는 null과 음수는 될 수 없다.
 */
public final class MessageId {

    private final Long value;

    private MessageId(Long value) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("MessageId must be positive");
        }
        this.value = value;
    }

    public static MessageId of(Long value) {
        return new MessageId(value);
    }

    public Long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

}
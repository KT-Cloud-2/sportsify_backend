package com.sportsify.chat.domain.model.message;


import java.util.Objects;

/**
 * 메시지 본문 VO.
 * - TEXT 메시지의 경우 비어있을 수 없으며 최대 300자
 */
public final class MessageContent {

    public static final int MAX_LENGTH = 500;

    private final String value;

    private MessageContent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MessageContent must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "MessageContent must be <= " + MAX_LENGTH + " characters but" + value.length());
        }
        this.value = value;
    }

    public static MessageContent of(String value) {
        return new MessageContent(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageContent that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

}
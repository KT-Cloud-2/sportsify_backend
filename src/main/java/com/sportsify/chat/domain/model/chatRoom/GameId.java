package com.sportsify.chat.domain.model.chatRoom;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;

import java.util.Objects;

public final class GameId {

    private final Long value;

    private GameId(Long value) {
        if (value <= 0L) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "MessageId must be positive");
        }
        this.value = value;
    }

    public static GameId of(Long value) {
        return new GameId(value);
    }

    public Long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

}

package com.sportsify.ticketing.presentation.dto;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.List;

public record ReservationSeatsRequestDto(
        @NotNull Long gameId,
        @NotEmpty List<Long> seatIds,
        @NotNull Long buyerId
) {
    public static ReservationSeatsRequestDto from(Long gameId, List<Long> seatIds, Long buyerId) {

        if (seatIds.size() != new HashSet<>(seatIds).size())
            throw new BusinessException(ErrorCode.SEAT_DUPLICATED);

        return new ReservationSeatsRequestDto(gameId, seatIds, buyerId);
    }

}

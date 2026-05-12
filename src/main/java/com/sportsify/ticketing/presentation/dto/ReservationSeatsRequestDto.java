package com.sportsify.ticketing.presentation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReservationSeatsRequestDto(
        @NotNull Long gameId,
        @NotEmpty List<Long> seatIds,
        @NotNull Long buyerId
) {
    public static ReservationSeatsRequestDto from(Long gameId, List<Long> seatIds, Long buyerId) {
        return new ReservationSeatsRequestDto(gameId, seatIds, buyerId);
    }

}

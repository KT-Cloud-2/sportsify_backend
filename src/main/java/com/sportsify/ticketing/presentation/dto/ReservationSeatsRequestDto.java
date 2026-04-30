package com.sportsify.ticketing.presentation.dto;

import java.util.List;

public record ReservationSeatsRequestDto(
        Long gameId,
        List<Long> seatIds,
        Long buyerId
) {
    public static ReservationSeatsRequestDto from(Long gameId, List<Long> seatIds, Long buyerId) {
        return new ReservationSeatsRequestDto(gameId, seatIds, buyerId);
    }

}

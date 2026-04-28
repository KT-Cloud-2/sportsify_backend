package com.sportsify.game.presentation.dto;

import com.sportsify.game.domain.model.GameSeat;

public record GameSeatListResponseDto(
        Long seatId,
        String grade,
        String section,
        String rowNumber,
        String seatNumber,
        Integer price,
        String status
) {
    public static GameSeatListResponseDto from(GameSeat gameSeat) {
        return new GameSeatListResponseDto(
                gameSeat.getId(),
                gameSeat.getSeat().getZoneGrade().getName(),
                gameSeat.getSeat().getSection().getName(),
                gameSeat.getSeat().getRowNumber(),
                gameSeat.getSeat().getSeatNumber(),
                gameSeat.getPrice(),
                gameSeat.getSeatStatus().name()
        );
    }
}
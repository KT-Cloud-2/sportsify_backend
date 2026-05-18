package com.sportsify.game.application.dto;

public record SeatGradeSummary(
        String grade,
        Integer price,
        Integer available
) {
    public static SeatGradeSummary of(String grade, Integer price, Integer available) {
        return new SeatGradeSummary(grade, price, available);
    }
}
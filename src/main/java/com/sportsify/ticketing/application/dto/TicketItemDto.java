package com.sportsify.ticketing.application.dto;

import com.sportsify.ticketing.domain.model.Ticket;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record TicketItemDto(
        Long ticketId,
        String ticketNumber,
        Long gameId,
        String sportType,
        String team1Name,
        String team2Name,
        LocalDateTime gameTime,
        String venue,
        String seatGrade,
        String seatSection,
        String seatNumber,
        Integer price,
        String status,
        LocalDateTime issuedAt
) {
    public static TicketItemDto from(Ticket ticket) {
        return TicketItemDto.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .gameId(ticket.getGameId())
                .sportType(ticket.getSportType())
                .team1Name(ticket.getHomeTeamName())
                .team2Name(ticket.getAwayTeamName())
                .gameTime(ticket.getStartAt())
                .venue(ticket.getStadium())
                .seatGrade(ticket.getSeatGrade())
                .seatSection(ticket.getSeatSection())
                .seatNumber(ticket.getSeatNumber())
                .price(ticket.getPrice())
                .status(ticket.getStatusName())
                .issuedAt(ticket.getIssuedAt())
                .build();
    }
}

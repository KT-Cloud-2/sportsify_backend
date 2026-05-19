package com.sportsify.ticketing.application.dto;

import com.sportsify.ticketing.domain.model.Ticket;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public class TicketItemDto {

    private Long ticketId;
    private String ticketNumber;
    private Long gameId;
    private String sportType;
    private String team1Name;
    private String team2Name;
    private LocalDateTime gameTime;
    private String venue;
    private String seatGrade;
    private String seatSection;
    private String seatNumber;
    private Integer price;
    private String status;
    private LocalDateTime issuedAt;

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

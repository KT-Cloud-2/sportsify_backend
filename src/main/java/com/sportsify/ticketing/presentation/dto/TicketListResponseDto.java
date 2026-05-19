package com.sportsify.ticketing.presentation.dto;

import com.sportsify.ticketing.application.dto.TicketItemDto;
import com.sportsify.ticketing.domain.model.Ticket;
import org.springframework.data.domain.Page;

import java.util.List;

public record TicketListResponseDto(
        List<TicketItemDto> items,
        int currentPage,
        int totalPages,
        long totalCount,
        boolean hasNext

) {
    public static TicketListResponseDto from(Page<Ticket> ticketPage) {
        List<TicketItemDto> items = ticketPage.getContent().stream()
                .map(TicketItemDto::from)
                .toList();

        return new TicketListResponseDto(
                items,
                ticketPage.getNumber(),
                ticketPage.getTotalPages(),
                ticketPage.getTotalElements(),
                ticketPage.hasNext()
        );
    }
}

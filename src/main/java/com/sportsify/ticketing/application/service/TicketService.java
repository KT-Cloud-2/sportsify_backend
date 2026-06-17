package com.sportsify.ticketing.application.service;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.Ticket;
import com.sportsify.ticketing.domain.repository.TicketRepository;
import com.sportsify.ticketing.presentation.dto.TicketListResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    final private TicketRepository ticketRepository;

    public void createTickets(Order order) {
        List<Ticket> tickets = order.getOrderSeats().stream()
                .map(seat -> Ticket.create(seat, order.getMember(), seat.getPrice()))
                .toList();
        ticketRepository.saveAll(tickets);
    }

    @Transactional(readOnly = true)
    public TicketListResponseDto getMyTickets(Long memberId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Ticket> ticketPage = ticketRepository.findByMemberId(memberId, pageable);
        return TicketListResponseDto.from(ticketPage);
    }
}

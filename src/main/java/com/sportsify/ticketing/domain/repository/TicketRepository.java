package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketRepository {
    Ticket save(Ticket ticket);

    Page<Ticket> findByMemberId(Long memberId, Pageable pageable);

}

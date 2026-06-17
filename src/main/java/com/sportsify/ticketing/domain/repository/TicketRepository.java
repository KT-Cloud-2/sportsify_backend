package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TicketRepository {
    Ticket save(Ticket ticket);

    List<Ticket> saveAll(List<Ticket> tickets);

    Page<Ticket> findByMemberId(Long memberId, Pageable pageable);

}

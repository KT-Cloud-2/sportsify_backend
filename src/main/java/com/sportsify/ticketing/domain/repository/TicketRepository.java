package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Ticket;

public interface TicketRepository {
    Ticket save(Ticket ticket);
}

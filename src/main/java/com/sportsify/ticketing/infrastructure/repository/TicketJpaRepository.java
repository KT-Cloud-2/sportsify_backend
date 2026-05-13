package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
}
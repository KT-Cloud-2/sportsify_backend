package com.sportsify.ticketing.infrastructure.repository;


import com.sportsify.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TicketRepositoryAdapter implements TicketRepository {

    private final TicketJpaRepository jpaRepository;
}
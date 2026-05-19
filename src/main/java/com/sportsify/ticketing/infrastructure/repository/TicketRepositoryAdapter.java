package com.sportsify.ticketing.infrastructure.repository;


import com.sportsify.ticketing.domain.model.Ticket;
import com.sportsify.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TicketRepositoryAdapter implements TicketRepository {

    private final TicketJpaRepository jpaRepository;

    @Override
    public Ticket save(Ticket ticket) {
        return jpaRepository.save(ticket);
    }

    @Override
    public Page<Ticket> findByMemberId(Long memberId, Pageable pageable) {
        return jpaRepository.findByMemberIdWithGame(memberId, pageable);
    }

}

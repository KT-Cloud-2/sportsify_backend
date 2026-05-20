package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
    @Query(value = "SELECT t FROM Ticket t " +
            "JOIN FETCH t.orderSeat os " +
            "JOIN FETCH os.gameSeat gs " +
            "JOIN FETCH gs.game g " +
            "WHERE t.member.id = :memberId",
            countQuery = "SELECT COUNT(t) FROM Ticket t WHERE t.member.id = :memberId")
    Page<Ticket> findByMemberIdWithGame(@Param("memberId") Long memberId, Pageable pageable);
}

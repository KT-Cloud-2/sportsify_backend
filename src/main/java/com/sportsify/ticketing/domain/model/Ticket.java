package com.sportsify.ticketing.domain.model;

import com.sportsify.member.domain.model.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_seat_id", nullable = false, unique = true)
    private OrderSeat orderSeat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 36)
    private String ticketNumber;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    private Ticket(OrderSeat orderSeat, Member member, Integer price) {
        this.orderSeat = orderSeat;
        this.member = member;
        this.ticketNumber = UUID.randomUUID().toString();
        this.price = price;
        this.status = TicketStatus.CONFIRMED;
        this.issuedAt = LocalDateTime.now();
    }

    public static Ticket create(OrderSeat orderSeat, Member member, Integer price) {
        return new Ticket(orderSeat, member, price);
    }

    // === 디미터 법칙 위임 메서드 ===

    public Long getGameId() {
        return orderSeat.getGameId();
    }

    public String getSportType() {
        return orderSeat.getSportType();
    }

    public String getHomeTeamName() {
        return orderSeat.getHomeTeamName();
    }

    public String getAwayTeamName() {
        return orderSeat.getAwayTeamName();
    }

    public LocalDateTime getStartAt() {
        return orderSeat.getStartAt();
    }

    public String getStadium() {
        return orderSeat.getStadiumName();
    }

    public String getSeatGrade() {
        return orderSeat.getSeatGradeName();
    }

    public String getSeatSection() {
        return orderSeat.getSectionName();
    }

    public String getSeatNumber() {
        return orderSeat.getSeatNumber();
    }

    public String getStatusName() {
        return status.name();
    }

    // === 상태 변경 ===


    public void updateAsUsed(LocalDateTime usedAt) {
        this.status = TicketStatus.USED;
        this.usedAt = usedAt;
    }

    public void updateAsCancelled(LocalDateTime cancelledAt) {
        this.status = TicketStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

}

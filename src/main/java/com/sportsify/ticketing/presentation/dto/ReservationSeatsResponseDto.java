package com.sportsify.ticketing.presentation.dto;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;


public record ReservationSeatsResponseDto(
        Long orderId,
        Long gameId,
        Long memberId,
        OrderStatus status,
        LocalDateTime reservedAt,
        List<ReservationSeatDto> seats,
        LocalDateTime expiresAt
) {
    public static ReservationSeatsResponseDto from(Order order, Long gameId) {
        List<ReservationSeatDto> seats = order.getOrderSeats().stream()
                .map(ReservationSeatDto::from)
                .toList();

        return new ReservationSeatsResponseDto(
                order.getId(),
                gameId,
                order.getMember().getId(),
                order.getStatus(),
                order.getCreatedAt(),
                seats,
                order.getExpiresAt()
        );
    }


    public record ReservationSeatDto(
            Long seatId,
            String seatGrade,
            String seatSection,
            Integer price
    ) {
        public static ReservationSeatDto from(OrderSeat orderSeat) {
            return new ReservationSeatDto(
                    orderSeat.getSeatId(),
                    orderSeat.getSeatGradeName(),
                    orderSeat.getSectionName(),
                    orderSeat.getSeatPrice()
            );
        }
    }
}

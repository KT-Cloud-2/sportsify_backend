package com.sportsify.ticketing.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.repository.MemberRepository;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final GameSeatRepository gameSeatRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final GameRepository gameRepository;

    @Transactional
    public ReservationSeatsResponseDto reserveSeat(Long memberId, ReservationSeatsRequestDto reqDto) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Game game = gameRepository.findById(reqDto.gameId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));

        if (!game.isOnSale())
            throw new BusinessException(ErrorCode.GAME_NOT_ON_SALE);

        if (reqDto.seatIds().size() > game.getMaxTicketPerUser())
            throw new BusinessException(
                    ErrorCode.TICKET_LIMIT_EXCEEDED,
                    "요청: " + reqDto.seatIds().size() + "매, 최대: " + game.getMaxTicketPerUser() + "매"
            );

        if (reqDto.seatIds().size() != new HashSet<>(reqDto.seatIds()).size())
            throw new BusinessException(ErrorCode.SEAT_DUPLICATED);

        List<GameSeat> availableSeats = gameSeatRepository.findAllAvailableIdsWithLock(reqDto.seatIds());

        if (!availableSeats.stream().allMatch(gameSeat -> gameSeat.getGameId().equals(reqDto.gameId())))
            throw new BusinessException(ErrorCode.GAME_MISMATCH);

        if (availableSeats.size() != reqDto.seatIds().size())
            throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);

        Order createdOrder = Order.create(member);

        availableSeats.forEach(seat -> {
            seat.updateSeatStatus(SeatStatus.RESERVED);
            createdOrder.addOrderSeat(OrderSeat.create(createdOrder, seat, seat.getPrice()));
        });

        createdOrder.calculateTotalAmount();

        orderRepository.save(createdOrder);

        return ReservationSeatsResponseDto.from(createdOrder, reqDto.gameId());
    }

}

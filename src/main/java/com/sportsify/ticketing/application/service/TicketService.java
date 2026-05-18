package com.sportsify.ticketing.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.repository.MemberRepository;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.Ticket;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    final private MemberRepository memberRepository;
    final private OrderRepository orderRepository;
    final private TicketRepository ticketRepository;

    @Transactional
    public void createTickets(Long orderId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Order order = orderRepository.findByIdWithOrderSeats(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        
        order.getOrderSeats().forEach(orderSeat ->
                ticketRepository.save(Ticket.create(orderSeat, member, orderSeat.getPrice())));

    }
}

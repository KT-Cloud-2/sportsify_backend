package com.sportsify.ticketing.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.repository.MemberRepository;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.Ticket;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.domain.repository.TicketRepository;
import com.sportsify.ticketing.presentation.dto.TicketListResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
        Order order = orderRepository.findByIdWithOrderSeats(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_MEMBER_MISMATCH);
        }

        Member member = memberRepository.getReferenceById(memberId);

        order.getOrderSeats().forEach(orderSeat ->
                ticketRepository.save(Ticket.create(orderSeat, member, orderSeat.getPrice())));

    }

    @Transactional(readOnly = true)
    public TicketListResponseDto getMyTickets(Long memberId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Ticket> ticketPage = ticketRepository.findByMemberId(memberId, pageable);

        return TicketListResponseDto.from(ticketPage);
    }
}

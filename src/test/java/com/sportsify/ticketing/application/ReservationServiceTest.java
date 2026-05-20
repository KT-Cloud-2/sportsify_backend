package com.sportsify.ticketing.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.application.service.PricePolicyService;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.repository.MemberRepository;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Spy
    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private PricePolicyService pricePolicyService;

    @Mock
    private GameSeatRepository gameSeatRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private GameRepository gameRepository;

    @Test
    @DisplayName("존재하지 않는 회원 ID를 요청으로 받으면 예외를 반환한다.")
    public void notFoundMember() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L));

        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.reserveSeat(1L, reqDto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 게임 ID를 요청으로 받으면 예외를 반환한다.")
    public void notFoundGame() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L));
        Member mockMember = mock(Member.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.reserveSeat(1L, reqDto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GAME_NOT_FOUND);
    }

    @Test
    @DisplayName("요청된 게임이 판매 중 상태가 아니라면 예외를 반환한다.")
    public void notOnSaleGame() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(mockGame.isOnSale()).thenReturn(false);

        assertThatThrownBy(() -> reservationService.reserveSeat(1L, reqDto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GAME_NOT_ON_SALE);
    }

    @Test
    @DisplayName("최대 요청 가능 매수를 초과하면 예외를 반환한다.")
    public void exceedTicketCapacity() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L, 2L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(mockGame.isOnSale()).thenReturn(true);
        when(mockGame.getMaxTicketPerUser()).thenReturn(1);

        assertThatThrownBy(() -> reservationService.reserveSeat(1L, reqDto))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TICKET_LIMIT_EXCEEDED);
                    assertThat(ex.getDetail()).isEqualTo("요청: 2매, 최대: 1매");
                });
    }

    @Test
    @DisplayName("다른 게임의 좌석이 포함되어 있으면 예외를 반환한다.")
    public void gameMismatch() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L, 2L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);
        GameSeat seat1 = mock(GameSeat.class);
        GameSeat seat2 = mock(GameSeat.class);
        Game otherGame = mock(Game.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(mockGame.isOnSale()).thenReturn(true);
        when(mockGame.getMaxTicketPerUser()).thenReturn(4);
        when(mockGame.getId()).thenReturn(1L);

        when(gameSeatRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(seat1, seat2));
        when(seat1.getGame()).thenReturn(mockGame);
        when(seat2.getGame()).thenReturn(otherGame);
        when(otherGame.getId()).thenReturn(999L);

        assertThatThrownBy(() -> reservationService.reserveSeat(1L, reqDto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GAME_MISMATCH);
    }

    @Test
    @DisplayName("선점 가능한 좌석 수와 요청한 좌석 수가 일치하지 않으면 예외를 반환한다.")
    public void checkAvailableSeats() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);
        GameSeat seat1 = mock(GameSeat.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(mockGame.isOnSale()).thenReturn(true);
        when(mockGame.getMaxTicketPerUser()).thenReturn(2);
        when(mockGame.getId()).thenReturn(1L);

        when(gameSeatRepository.findAllById(List.of(1L))).thenReturn(List.of(seat1));
        when(seat1.getGame()).thenReturn(mockGame);

        when(gameSeatRepository.findAllAvailableByGameIdAndIdsWithLock(anyLong(), anyList())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> reservationService.reserveSeat(1L, reqDto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);
    }

    @Test
    @DisplayName("사용 가능한 좌석 수가 같을 때, 각 좌석이 선점된다.")
    public void updateSeatStatus() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L, 2L, 3L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);
        GameSeat seat1 = mock(GameSeat.class);
        GameSeat seat2 = mock(GameSeat.class);
        GameSeat seat3 = mock(GameSeat.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(mockGame.isOnSale()).thenReturn(true);
        when(mockGame.getMaxTicketPerUser()).thenReturn(4);
        when(mockGame.getId()).thenReturn(1L);

        when(gameSeatRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(seat1, seat2, seat3));
        when(seat1.getGame()).thenReturn(mockGame);
        when(seat2.getGame()).thenReturn(mockGame);
        when(seat3.getGame()).thenReturn(mockGame);

        when(gameSeatRepository.findAllAvailableByGameIdAndIdsWithLock(anyLong(), anyList()))
                .thenReturn(List.of(seat1, seat2, seat3));

        when(pricePolicyService.getPriceMap(any(Game.class), anyList()))
                .thenReturn(Map.of("STANDARD", 10000));

        when(seat1.getZoneGradeName()).thenReturn("STANDARD");
        when(seat2.getZoneGradeName()).thenReturn("STANDARD");
        when(seat3.getZoneGradeName()).thenReturn("STANDARD");

        reservationService.reserveSeat(1L, reqDto);

        verify(seat1).updateSeatStatus(SeatStatus.RESERVED);
        verify(seat2).updateSeatStatus(SeatStatus.RESERVED);
        verify(seat3).updateSeatStatus(SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("좌석이 선점되면서 최종 주문이 생성된다.")
    public void createOrder() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);
        GameSeat mockGameSeat = mock(GameSeat.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(mockGame.isOnSale()).thenReturn(true);
        when(mockGame.getMaxTicketPerUser()).thenReturn(2);
        when(gameSeatRepository.findAllAvailableByGameIdAndIdsWithLock(anyLong(), anyList())).thenReturn(List.of(mockGameSeat));

        when(pricePolicyService.getPriceMap(any(Game.class), anyList()))
                .thenReturn(Map.of("STANDARD", 10000));

        when(mockGameSeat.getZoneGradeName()).thenReturn("STANDARD");

        reservationService.reserveSeat(1L, reqDto);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(captor.capture());

        Order savedOrder = captor.getValue();
        assertThat(savedOrder.getOrderSeats()).hasSize(1);
        assertThat(savedOrder.getMember()).isEqualTo(mockMember);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("좌석이 선점되고 주문이 생성된 다음, 응답 Dto를 반환한다.")
    public void returnResponseDto() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L, 2L, 3L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);
        GameSeat seat1 = mock(GameSeat.class);
        GameSeat seat2 = mock(GameSeat.class);
        GameSeat seat3 = mock(GameSeat.class);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(mockGame.isOnSale()).thenReturn(true);
        when(mockGame.getMaxTicketPerUser()).thenReturn(4);
        when(mockGame.getId()).thenReturn(1L);

        when(gameSeatRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(seat1, seat2, seat3));
        when(seat1.getGame()).thenReturn(mockGame);
        when(seat2.getGame()).thenReturn(mockGame);
        when(seat3.getGame()).thenReturn(mockGame);

        when(gameSeatRepository.findAllAvailableByGameIdAndIdsWithLock(anyLong(), anyList()))
                .thenReturn(List.of(seat1, seat2, seat3));

        when(pricePolicyService.getPriceMap(any(Game.class), anyList()))
                .thenReturn(Map.of("STANDARD", 10000));

        when(seat1.getZoneGradeName()).thenReturn("STANDARD");
        when(seat2.getZoneGradeName()).thenReturn("STANDARD");
        when(seat3.getZoneGradeName()).thenReturn("STANDARD");

        ReservationSeatsResponseDto response = reservationService.reserveSeat(1L, reqDto);

        assertThat(response.gameId()).isEqualTo(1L);
        assertThat(response.seats()).hasSize(3);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.amount()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("주문 생성 시 각 좌석에 올바른 가격이 저장된다.")
    void reserveSeat_savesCorrectPriceToOrderSeat() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(1L, List.of(1L, 2L));

        Member mockMember = mock(Member.class);
        Game mockGame = mock(Game.class);
        GameSeat seat1 = mock(GameSeat.class);
        GameSeat seat2 = mock(GameSeat.class);

        when(mockGame.isOnSale()).thenReturn(true);
        when(mockGame.getMaxTicketPerUser()).thenReturn(4);
        when(mockGame.getId()).thenReturn(1L);

        when(seat1.getZoneGradeName()).thenReturn("STANDARD");
        when(seat2.getZoneGradeName()).thenReturn("STANDARD");
        when(seat1.getGame()).thenReturn(mockGame);
        when(seat2.getGame()).thenReturn(mockGame);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(mockMember));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(mockGame));
        when(gameSeatRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(seat1, seat2));
        when(gameSeatRepository.findAllAvailableByGameIdAndIdsWithLock(anyLong(), anyList()))
                .thenReturn(List.of(seat1, seat2));

        when(pricePolicyService.getPriceMap(any(Game.class), anyList()))
                .thenReturn(Map.of("STANDARD", 10000));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ReservationSeatsResponseDto response = reservationService.reserveSeat(1L, reqDto);

        Order savedOrder = orderCaptor.getValue();
        List<OrderSeat> orderSeats = savedOrder.getOrderSeats();

        assertThat(orderSeats).hasSize(2);
        assertThat(orderSeats.get(0).getPrice()).isEqualTo(10000);
        assertThat(orderSeats.get(1).getPrice()).isEqualTo(10000);
        assertThat(response.amount()).isEqualTo(20000L);
    }

}

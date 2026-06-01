package com.sportsify.ticketing.presentation.api;

import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import static com.sportsify.common.exception.ErrorCode.*;

@Tag(name = "좌석 선점", description = "좌석 선점(주문 생성) API")
@AuthRequiredApi
@CommonApiResponses
public interface ReservationApi {

    @SwaggerApi(summary = "좌석 선점 (주문 생성)",
            description = "선택한 좌석을 선점하고 주문을 생성합니다. 15분 내 결제 미완료 시 자동 만료됩니다. " +
                    "seatIds는 game_seat ID 목록이며 중복 불가, 최대 maxTicketPerUser매까지 가능합니다.",
            errors = {GAME_NOT_FOUND, GAME_NOT_ON_SALE, TICKET_LIMIT_EXCEEDED,
                    GAME_MISMATCH, SEAT_ALREADY_RESERVED, SEAT_DUPLICATED, MEMBER_NOT_FOUND})
    ResponseEntity<ReservationSeatsResponseDto> reserveSeats(
            Long memberId,
            @RequestBody ReservationSeatsRequestDto reqDto
    );
}

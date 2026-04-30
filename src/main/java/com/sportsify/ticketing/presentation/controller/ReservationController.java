package com.sportsify.ticketing.presentation.controller;

import com.sportsify.common.response.CommonResponse;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seats/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public CommonResponse<ReservationSeatsResponseDto> reserveSeats(@RequestBody ReservationSeatsRequestDto reqDto) {
        return CommonResponse.ok(reservationService.reserveSeat(reqDto));
    }
}

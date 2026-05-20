package com.sportsify.ticketing.presentation.controller;

import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.presentation.api.ReservationApi;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seats/reservations")
@RequiredArgsConstructor
public class ReservationController implements ReservationApi {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationSeatsResponseDto> reserveSeats(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ReservationSeatsRequestDto reqDto) {
        return ResponseEntity.ok(reservationService.reserveSeat(memberId, reqDto));
    }
}

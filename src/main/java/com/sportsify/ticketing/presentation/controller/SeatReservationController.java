package com.sportsify.ticketing.presentation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/tickets/reserve")
public class SeatReservationController {

    @PostMapping
    public String reserveSeat() {
        return "hello reservation!";
    }
}

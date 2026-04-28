package com.sportsify.game.presentation.controller;


import com.sportsify.game.application.service.GameService;
import com.sportsify.game.presentation.dto.GameListResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping
    public ResponseEntity<List<GameListResponseDto>> getGames(
            @RequestParam(required = false) String sportType,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to
    ) {
        return ResponseEntity.ok(gameService.getGames(sportType, teamId, status, from, to));
    }

}
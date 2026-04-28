package com.sportsify.game.presentation.controller;


import com.sportsify.game.application.service.GameService;
import com.sportsify.game.presentation.dto.GameDetailResponseDto;
import com.sportsify.game.presentation.dto.GameListResponseDto;
import com.sportsify.game.presentation.dto.GameSeatListResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{gameId}")
    public ResponseEntity<GameDetailResponseDto> getGameDetail(@PathVariable Long gameId) {
        return ResponseEntity.ok(gameService.getGameDetail(gameId));
    }

    @GetMapping("/{gameId}/seats")
    public ResponseEntity<List<GameSeatListResponseDto>> getGameSeats(
            @PathVariable Long gameId,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(gameService.getGameSeats(gameId, grade, status));
    }
}
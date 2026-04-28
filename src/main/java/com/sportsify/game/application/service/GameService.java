package com.sportsify.game.application.service;

import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.game.presentation.dto.GameListResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final GameSeatRepository gameSeatRepository;

    public List<GameListResponseDto> getGames(
            String sportType,
            Long teamId,
            String status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<Game> games = gameRepository.findByDeletedAtIsNullOrderByStartAt();

        return games.stream()
                .filter(game -> sportType == null || game.getSportType().name().equals(sportType))
                .filter(game -> status == null || game.getStatus().name().equals(status))
                .filter(game -> teamId == null ||
                        game.getHomeTeam().getId().equals(teamId) ||
                        game.getAwayTeam().getId().equals(teamId))
                .filter(game -> from == null || !game.getStartAt().isBefore(from))
                .filter(game -> to == null || !game.getStartAt().isAfter(to))
                .map(game -> {
                    int availableSeats = gameSeatRepository.countByGameIdAndSeatStatus(
                            game.getId(),
                            SeatStatus.AVAILABLE
                    );
                    return GameListResponseDto.from(game, availableSeats);
                })
                .toList();
    }
}
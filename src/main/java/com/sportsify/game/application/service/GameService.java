package com.sportsify.game.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.GameStartPayload;
import com.sportsify.game.application.dto.SeatGradeSummary;
import com.sportsify.game.application.scheduler.GameSaleTaskScheduler;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.model.Stadium;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.game.domain.repository.StadiumRepository;
import com.sportsify.game.presentation.dto.*;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.domain.repository.TeamRepository;
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
    private final StadiumRepository stadiumRepository;
    private final TeamRepository teamRepository;

    private final GameSaleTaskScheduler gameSaleTaskScheduler;

    private final NotificationEventPublisher notificationEventPublisher;

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

    public GameDetailResponseDto getGameDetail(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다. id=" + gameId));

        int availableSeats = gameSeatRepository.countByGameIdAndSeatStatus(
                gameId,
                SeatStatus.AVAILABLE
        );

        List<Object[]> summaryData = gameSeatRepository.findSeatGradeSummaryByGameId(gameId);
        List<SeatGradeSummary> seatGradeSummary = summaryData.stream()
                .map(row -> SeatGradeSummary.of(
                        (String) row[0],
                        (Integer) row[1],
                        ((Long) row[2]).intValue()
                ))
                .toList();

        return GameDetailResponseDto.of(game, availableSeats, seatGradeSummary);
    }

    public List<GameSeatListResponseDto> getGameSeats(
            Long gameId,
            String grade,
            String status
    ) {

        if (!gameRepository.existsById(gameId)) {
            throw new IllegalArgumentException("존재하지 않는 경기입니다. id=" + gameId);
        }

        List<GameSeat> gameSeats = gameSeatRepository.findByGameId(gameId);

        return gameSeats.stream()
                .filter(gs -> grade == null || gs.getSeat().getZoneGrade().getName().equals(grade))
                .filter(gs -> status == null || gs.getSeatStatus().name().equals(status))
                .map(GameSeatListResponseDto::from)
                .toList();
    }


    @Transactional
    public GameCreateResponseDto createGame(GameCreateRequestDto request) {
        Stadium stadium = stadiumRepository.findById(request.stadiumId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STADIUM_NOT_FOUND));

        Team homeTeam = null;
        if (request.homeTeamId() != null) {
            homeTeam = teamRepository.findById(request.homeTeamId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND, "id=" + request.homeTeamId()));
        }

        Team awayTeam = null;
        if (request.awayTeamId() != null) {
            awayTeam = teamRepository.findById(request.awayTeamId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND, "id=" + request.awayTeamId()));
        }

        Game game = Game.create(
                stadium,
                homeTeam,
                awayTeam,
                request.sportType(),
                request.startAt(),
                request.durationMinutes(),
                request.status(),
                request.dayType(),
                request.gameGrade(),
                request.maxTicketPerUser(),
                request.saleStartAt(),
                request.saleEndAt()
        );

        Game savedGame = gameRepository.save(game);

        scheduleGameSale(savedGame);

        notificationEventPublisher.publish(
                NotificationEventType.GAME_START,
                new GameStartPayload(
                        savedGame.getId(),
                        savedGame.getHomeTeamName(),
                        savedGame.getAwayTeamName(),
                        savedGame.getStartAt()));

        return GameCreateResponseDto.from(savedGame);
    }

    private void scheduleGameSale(Game game) {
        if (game.getSaleStartAt() != null) {
            gameSaleTaskScheduler.scheduleSaleStart(game.getId(), game.getSaleStartAt());
        }
        if (game.getSaleEndAt() != null) {
            gameSaleTaskScheduler.scheduleSaleEnd(game.getId(), game.getSaleEndAt());
        }
    }
}

package com.sportsify.game.infrastructure;

import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.*;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameSeatRepositoryTest extends RepositoryTestSupport {
    @Autowired
    private GameSeatRepository gameSeatRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private ZoneGradeRepository zoneGradeRepository;
    @Autowired
    private TeamJpaRepository teamRepository;

    private GameSeat gameSeat1;
    private GameSeat gameSeat2;
    private GameSeat gameSeat3;

    @BeforeEach
    void beforeEach() {
        // Stadium
        Stadium stadium = stadiumRepository.save(
                Stadium.builder()
                        .name("잠실경기장")
                        .address("서울")
                        .totalSeats(25000)
                        .build()
        );

        // ZoneGrade
        ZoneGrade zoneGrade = zoneGradeRepository.save(
                ZoneGrade.builder()
                        .stadium(stadium)
                        .name("VIP")
                        .build()
        );

        // Section
        Section section = sectionRepository.save(
                Section.builder()
                        .stadium(stadium)
                        .zoneGrade(zoneGrade)
                        .name("A구역")
                        .floor("1층")
                        .build()
        );

        // Seat
        Seat seat1 = seatRepository.save(
                Seat.builder().section(section).rowNumber("1").seatNumber("1").build()
        );
        Seat seat2 = seatRepository.save(
                Seat.builder().section(section).rowNumber("1").seatNumber("2").build()
        );
        Seat seat3 = seatRepository.save(
                Seat.builder().section(section).rowNumber("2").seatNumber("3").build()
        );

        // Team
        Team homeTeam = teamRepository.save(
                Team.createForTest("두산베어스", "두산", SportType.BASEBALL)
        );
        Team awayTeam = teamRepository.save(
                Team.createForTest("LG트윈스", "LG", SportType.BASEBALL)
        );

        // Game
        Game game = gameRepository.save(
                Game.builder()
                        .stadium(stadium)
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .sportType(SportType.BASEBALL)
                        .startAt(LocalDateTime.now().plusDays(7))
                        .status(GameStatus.SCHEDULED)
                        .build()
        );

        // GameSeat
        gameSeat1 = gameSeatRepository.save(
                GameSeat.builder().game(game).seat(seat1).price(15000).build()
        );

        gameSeat2 = gameSeatRepository.save(
                GameSeat.builder().game(game).seat(seat2).price(15000).build()
        );
        gameSeat2.updateSeatStatus(SeatStatus.RESERVED);
        gameSeatRepository.save(gameSeat2);

        gameSeat3 = gameSeatRepository.save(
                GameSeat.builder().game(game).seat(seat3).price(20000).build()
        );
    }

    @Test
    @DisplayName("요청한 ID 중 AVAILABLE 상태인 좌석만 정렬되어 조회된다")
    void findAvailableSeatsOnly() {

        List<GameSeat> result = gameSeatRepository.findAllAvailableByIdsWithLock(
                List.of(gameSeat3.getId(), gameSeat1.getId(), gameSeat2.getId())
        );

        assertThat(result).hasSize(2);
        assertThat(result).extracting("seatStatus")
                .containsOnly(SeatStatus.AVAILABLE);
        assertThat(result.get(0).getId()).isLessThan(result.get(1).getId());
    }

}

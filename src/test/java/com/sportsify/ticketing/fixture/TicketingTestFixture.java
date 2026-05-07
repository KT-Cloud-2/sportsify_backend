package com.sportsify.ticketing.fixture;

import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.*;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamJpaRepository;
import com.sportsify.ticketing.infrastructure.repository.OrderJpaRepository;
import com.sportsify.ticketing.infrastructure.repository.OrderSeatJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TicketingTestFixture {

    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private TeamJpaRepository teamRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private MemberJpaRepository memberRepository;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private ZoneGradeRepository zoneGradeRepository;
    @Autowired
    private GameSeatRepository gameSeatRepository;
    @Autowired
    private OrderJpaRepository orderRepository;
    @Autowired
    private OrderSeatJpaRepository orderSeatRepository;

    public Game createGame(Stadium stadium, Team homeTeam, Team awayTeam) {
        Game game = Game.builder()
                .stadium(stadium)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .sportType(SportType.BASEBALL)
                .startAt(LocalDateTime.now().plusDays(7))
                .status(GameStatus.SCHEDULED)
                .build();
        game.updateStatus(GameStatus.ON_SALE);
        return gameRepository.save(game);
    }

    public List<Long> createGameWithSeats() {
        Stadium stadium = stadiumRepository.save(
                Stadium.builder()
                        .name("경기장")
                        .address("서울")
                        .totalSeats(25000)
                        .build()
        );

        ZoneGrade zoneGrade = zoneGradeRepository.save(
                ZoneGrade.builder()
                        .stadium(stadium)
                        .name("VIP")
                        .build()
        );

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

        // Team
        Team homeTeam = teamRepository.save(
                Team.createForTest("Team1", "T1", SportType.BASEBALL)
        );
        Team awayTeam = teamRepository.save(
                Team.createForTest("Team2", "T1", SportType.BASEBALL)
        );

        Game game = createGame(stadium, homeTeam, awayTeam);

        GameSeat gs1 = gameSeatRepository.save(GameSeat.builder().game(game).seat(seat1).price(15000).build());
        GameSeat gs2 = gameSeatRepository.save(GameSeat.builder().game(game).seat(seat2).price(15000).build());

        return List.of(gs1.getId(), gs2.getId());
    }

    // ─────────────── Member ───────────────
    public Member createMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, nickname, OAuthProvider.GOOGLE, "g-" + email));
    }

    public void deleteAll() {
        orderSeatRepository.deleteAll();
        orderRepository.deleteAll();
        gameSeatRepository.deleteAll();
        gameRepository.deleteAll();
        seatRepository.deleteAll();
        sectionRepository.deleteAll();
        zoneGradeRepository.deleteAll();
        teamRepository.deleteAll();
        stadiumRepository.deleteAll();
        memberRepository.deleteAll();
    }

}
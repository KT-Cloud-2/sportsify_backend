package com.sportsify.ticketing.fixture;

import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.*;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.payment.domain.repository.PaymentRepository;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamJpaRepository;
import com.sportsify.ticketing.infrastructure.repository.OrderJpaRepository;
import com.sportsify.ticketing.infrastructure.repository.OrderSeatJpaRepository;
import com.sportsify.ticketing.infrastructure.repository.TicketJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class TicketingTestFixture {

    @Autowired
    private TicketJpaRepository ticketRepository;
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
    private PricePolicyRepository pricePolicyRepository;
    @Autowired
    private OrderJpaRepository orderRepository;
    @Autowired
    private OrderSeatJpaRepository orderSeatRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    public Game createGame() {
        Stadium stadium = stadiumRepository.save(
                Stadium.builder()
                        .name("경기장")
                        .address("서울")
                        .totalSeats(25000)
                        .build()
        );

        // Team
        Team homeTeam = teamRepository.save(
                Team.createForTest("Team1", "T1", SportType.BASEBALL)
        );
        Team awayTeam = teamRepository.save(
                Team.createForTest("Team2", "T1", SportType.BASEBALL)
        );

        Game game = Game.builder()
                .stadium(stadium)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .sportType(SportType.BASEBALL)
                .startAt(LocalDateTime.now().plusDays(7))
                .status(GameStatus.SCHEDULED)
                .dayType(DayType.WEEKDAY)
                .gameGrade(GameGrade.NORMAL)
                .build();

        game.updateStatus(GameStatus.ON_SALE);

        return gameRepository.save(game);
    }

    public List<Long> createGameSeatsWithCount(Game game, int count) {

        Stadium stadium = game.getStadium();

        ZoneGrade zoneGrade = zoneGradeRepository.save(
                ZoneGrade.builder()
                        .stadium(stadium)
                        .name("VIP")
                        .build()
        );

        createPricePoliciesWithGame(game, zoneGrade);

        Section section = sectionRepository.save(
                Section.builder()
                        .stadium(stadium)
                        .zoneGrade(zoneGrade)
                        .name("A구역")
                        .floor("1층")
                        .build()
        );

        ArrayList<Long> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Seat seat = seatRepository.save(
                    Seat.builder().section(section).rowNumber(i / 10 + 1 + "").seatNumber(i % 10 + 1 + "").build()
            );

            ids.add(gameSeatRepository.save(GameSeat.builder().game(game).seat(seat).price(15000).build()).getId());
        }

        return ids;
    }

    public void createPricePoliciesWithGame(Game game, ZoneGrade zoneGrade) {

        pricePolicyRepository.save(PricePolicy
                .builder()
                .stadium(game.getStadium())
                .dayType(game.getDayType())
                .gameGrade(game.getGameGrade())
                .zoneGrade(zoneGrade)
                .price(10000)
                .build()
        );
    }

    // ─────────────── Member ───────────────
    public Member createMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, nickname, OAuthProvider.GOOGLE, "g-" + email));
    }

    public Member createMemberWithNum(int number) {
        return memberRepository.save(Member.create(number + "@test.com", "n-" + number, OAuthProvider.GOOGLE, "g-" + number));
    }

    public void deleteAll() {
        ticketRepository.deleteAll();
        orderSeatRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        gameSeatRepository.deleteAll();
        pricePolicyRepository.deleteAll();
        gameRepository.deleteAll();
        seatRepository.deleteAll();
        sectionRepository.deleteAll();
        zoneGradeRepository.deleteAll();
        teamRepository.deleteAll();
        stadiumRepository.deleteAll();
        memberRepository.deleteAll();
    }

}

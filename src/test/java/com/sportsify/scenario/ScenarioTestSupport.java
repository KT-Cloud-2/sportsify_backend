package com.sportsify.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sportsify.config.TestContainersConfig;
import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.model.PricePolicy;
import com.sportsify.game.domain.repository.*;
import com.sportsify.infrastructure.security.JwtProvider;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.infrastructure.repository.NotificationSettingJpaRepository;
import com.sportsify.payment.infrastructure.toss.TossPaymentClient;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 시나리오 테스트 베이스 클래스.
 *
 * Spring ApplicationContext를 5개 시나리오 전체가 공유한다.
 * 외부 I/O MockitoBean을 여기에 집중 선언해 캐시 키를 통일한다.
 * ref: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html
 */
@Tag("scenario")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public abstract class ScenarioTestSupport {

    protected final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 외부 I/O — 전부 여기에 집중 선언 (캐시 키 통일 핵심)
    @MockitoBean
    protected TossPaymentClient tossPaymentClient;
    @MockitoBean
    protected JavaMailSender mailSender;
    // Redis 연결 불안정 방지:
    // - StringRedisTemplate: JWT 블랙리스트 체크(isBlacklisted)를 항상 false로 통과시킨다.
    // - StreamMessageListenerContainer: Mock으로 선언하면 RedisStreamsConfig@Bean 메서드가 실행되지 않아
    //   initConsumerGroups()의 NPE와 InfrastructureException을 방지한다.
    @MockitoBean
    protected StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    @SuppressWarnings("rawtypes")
    protected StreamMessageListenerContainer streamListenerContainer;

    @Autowired
    protected JwtProvider jwtProvider;
    @Autowired
    protected WebApplicationContext context;

    @Autowired
    private MemberJpaRepository memberRepository;
    @Autowired
    private NotificationSettingJpaRepository notificationSettingRepository;
    @Autowired
    private TeamJpaRepository teamRepository;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private ZoneGradeRepository zoneGradeRepository;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameSeatRepository gameSeatRepository;
    @Autowired
    private PricePolicyRepository pricePolicyRepository;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        buildMockMvc();
    }

    private void buildMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // @BeforeAll에서 mockMvc가 필요할 때 호출한다 (@BeforeEach보다 먼저 실행되므로 직접 초기화)
    protected MockMvc mockMvc() {
        if (this.mockMvc == null) {
            buildMockMvc();
        }
        return this.mockMvc;
    }

    protected Member createMember(String email) {
        Member member = memberRepository.save(
                Member.create(email, email.split("@")[0], OAuthProvider.KAKAO, UUID.randomUUID().toString())
        );
        notificationSettingRepository.save(NotificationSetting.createDefault(member.getId()));
        return member;
    }

    protected String bearerToken(Long memberId) {
        return "Bearer " + jwtProvider.createAccessToken(memberId, "USER");
    }

    /**
     * 시나리오 테스트용 ON_SALE 경기 + 좌석 N개를 생성한다.
     * seed.sql 없이도 테스트가 자립할 수 있도록 필요한 모든 연관 엔티티를 직접 생성한다.
     * 반환: [gameId, gameSeatId1, gameSeatId2, ...]
     */
    protected List<Long> createGameWithSeats(int seatCount) {
        Stadium stadium = stadiumRepository.save(
                Stadium.builder().name("테스트구장-" + UUID.randomUUID()).totalSeats(seatCount).build()
        );
        ZoneGrade zone = zoneGradeRepository.save(
                ZoneGrade.builder().stadium(stadium).name("R").build()
        );
        pricePolicyRepository.save(PricePolicy.builder()
                .stadium(stadium)
                .dayType(DayType.WEEKDAY)
                .gameGrade(GameGrade.NORMAL)
                .zoneGrade(zone)
                .price(50000)
                .build());
        Section section = sectionRepository.save(
                Section.builder().stadium(stadium).zoneGrade(zone).name("1루").floor("1F").build()
        );

        Team homeTeam = teamRepository.save(Team.createForTest("홈팀-" + UUID.randomUUID(), "홈", SportType.BASEBALL));
        Team awayTeam = teamRepository.save(Team.createForTest("원정팀-" + UUID.randomUUID(), "원정", SportType.BASEBALL));

        Game game = gameRepository.save(Game.create(
                stadium, homeTeam, awayTeam, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, GameStatus.ON_SALE,
                DayType.WEEKDAY, GameGrade.NORMAL, 4,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6)
        ));

        List<Long> result = new ArrayList<>();
        result.add(game.getId());

        for (int i = 1; i <= seatCount; i++) {
            Seat seat = seatRepository.save(
                    Seat.builder().section(section).rowNumber("A").seatNumber(String.valueOf(i)).build()
            );
            GameSeat gameSeat = gameSeatRepository.save(
                    GameSeat.builder().game(game).seat(seat).price(50000).build()
            );
            result.add(gameSeat.getId());
        }

        return result;
    }

    protected List<Long> fetchAvailableSeatIds(Long gameId, String token) throws Exception {
        String body = mockMvc().perform(get("/api/games/{gameId}/seats", gameId)
                        .param("status", "AVAILABLE")
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString();

        JsonNode array = objectMapper.readTree(body);
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : array) {
            ids.add(node.get("seatId").asLong());
        }
        return ids;
    }
}

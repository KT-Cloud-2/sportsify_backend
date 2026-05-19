package com.sportsify.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sportsify.config.TestContainersConfig;
import com.sportsify.infrastructure.security.JwtProvider;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.infrastructure.repository.NotificationSettingJpaRepository;
import com.sportsify.payment.infrastructure.toss.TossPaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * 시나리오 테스트 베이스 클래스.
 *
 * Spring ApplicationContext를 5개 시나리오 전체가 공유한다.
 * 외부 I/O MockitoBean을 여기에 집중 선언해 캐시 키를 통일한다.
 * ref: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Tag("scenario")
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
    @Autowired
    protected JwtProvider jwtProvider;
    protected MockMvc mockMvc;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private NotificationSettingJpaRepository notificationSettingRepository;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    protected void executeSeed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/scenario/seed.sql"));
        }
    }

    protected void cleanUp(JdbcTemplate jdbc) {
        jdbc.execute("""
                TRUNCATE notifications, notification_events, notification_history,
                         payments, orders, order_seats,
                         notification_settings, notification_channels,
                         member_favorite_teams, members
                RESTART IDENTITY CASCADE
                """);
    }

    protected Member createMember(MemberJpaRepository memberRepository, String email, String providerId) {
        Member member = memberRepository.save(
                Member.create(email, email.split("@")[0], OAuthProvider.KAKAO, providerId)
        );
        notificationSettingRepository.save(NotificationSetting.createDefault(member.getId()));
        return member;
    }

    protected String bearerToken(Long memberId) {
        return "Bearer " + jwtProvider.createAccessToken(memberId, "USER");
    }
}

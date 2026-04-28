package com.sportsify.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sportsify.config.TestContainersConfig;
import com.sportsify.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@ExtendWith(RestDocumentationExtension.class)
public abstract class ApiTestSupport {

    // restdocs-api-spec 0.19.x 는 Jackson 2.x 기반이므로, Spring Boot 4 컨텍스트에서
    // com.fasterxml ObjectMapper 빈이 등록되지 않는다 → 직접 생성해서 직렬화에만 사용
    protected final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    protected JwtProvider jwtProvider;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc(WebApplicationContext context, RestDocumentationContextProvider restDocs) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .apply(MockMvcRestDocumentation.documentationConfiguration(restDocs))
                .build();
    }

    protected String bearerToken(Long memberId, String role) {
        return "Bearer " + jwtProvider.createAccessToken(memberId, role);
    }
}

package com.sportsify.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sportsify.common.swagger.SwaggerApiErrorCustomizer;
import com.sportsify.infrastructure.config.JacksonConfig;
import com.sportsify.infrastructure.config.SecurityConfig;
import com.sportsify.infrastructure.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtProvider.class,
        JacksonConfig.class,
        SwaggerApiErrorCustomizer.class
})
public abstract class WebMvcTestSupport {

    protected final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtProvider jwtProvider;

    @MockitoBean
    protected StringRedisTemplate redisTemplate;

    @MockitoBean
    protected CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    protected OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    protected String bearerToken(Long memberId, String role) {
        return "Bearer " + jwtProvider.createAccessToken(memberId, role);
    }
}

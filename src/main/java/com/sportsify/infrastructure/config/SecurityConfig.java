package com.sportsify.infrastructure.config;

import com.sportsify.infrastructure.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/oauth2/**",
            "/login/**",
            "/api/auth/**",
            "/api/teams",
            "/api/teams/**",
            "/api/games",
            "/api/games/**",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger",
            "/swagger.html",
            "/webjars/**",
            "/docs.html",
            "/api/chat/rooms/**",
            "/api/chat/messages/getMessages/**",
            "/ws/chat/**"
    );

    private static final List<String> LOCAL_ONLY_PATHS = List.of(
            "/dev/**", "/notification-test.html", "/dev-test.html", "/checkout.html", "/success.html", "/fail.html"
    );
    private static final List<String> SSE_PATHS = List.of(
            "/api/notifications/stream"
    );

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final Environment environment;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider, redisTemplate, SSE_PATHS);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> publicPaths = new ArrayList<>(PUBLIC_PATHS);

        if (isLocalProfile()) {
            publicPaths.addAll(LOCAL_ONLY_PATHS);
        }

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter());
        registration.setEnabled(false);
        return registration;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

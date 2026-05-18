package com.sportsify.common.dev;

import com.sportsify.infrastructure.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dev/token")
@Profile("local")
@RequiredArgsConstructor
public class DevTokenController {

    private final JwtProvider jwtProvider;

    @GetMapping
    public ResponseEntity<Map<String, String>> issue(
            @RequestParam(defaultValue = "1") Long memberId,
            @RequestParam(defaultValue = "USER") String role
    ) {
        String token = jwtProvider.createAccessToken(memberId, role);
        return ResponseEntity.ok(Map.of("token", token, "memberId", String.valueOf(memberId)));
    }
}

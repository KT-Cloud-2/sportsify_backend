package com.sportsify.common.dev;

import com.sportsify.infrastructure.security.JwtProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;

@RestController
@RequestMapping("/dev/token")
@Profile("local")
@RequiredArgsConstructor
public class DevTokenController {

    private final JwtProvider jwtProvider;
    private final MemberJpaRepository memberJpaRepository;

    private static final ExecutorService TOKEN_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    @GetMapping
    public ResponseEntity<Map<String, String>> issue(
            @RequestParam(defaultValue = "1") Long memberId,
            @RequestParam(defaultValue = "USER") String role
    ) {
        String token = jwtProvider.createAccessToken(memberId, role);
        return ResponseEntity.ok(Map.of("token", token, "memberId", String.valueOf(memberId)));
    }

    @GetMapping("/by-email")
    public ResponseEntity<Map<String, String>> issueByEmail(
            @RequestParam String email,
            @RequestParam(defaultValue = "USER") String role
    ) {
        return memberJpaRepository.findByEmail(email)
                .map(member -> {
                    String token = jwtProvider.createAccessToken(member.getId(), role);
                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "memberId", String.valueOf(member.getId()),
                            "email", member.getEmail(),
                            "nickname", member.getNickname()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bulk")
    public ResponseEntity<List<Map<String, String>>> issueBulk(
            @RequestParam(defaultValue = "1") Long startMemberId,
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(defaultValue = "USER") String role,
            @RequestParam(required = false) Long expiryMs
    ) {
        List<CompletableFuture<Map<String, String>>> futures = LongStream
                .range(startMemberId, startMemberId + count)
                .mapToObj(memberId -> CompletableFuture.supplyAsync(() -> {
                    String token = expiryMs != null
                            ? jwtProvider.createAccessTokenWithExpiry(memberId, role, expiryMs)
                            : jwtProvider.createAccessToken(memberId, role);
                    return Map.<String, String>of("token", token, "memberId", String.valueOf(memberId));
                }, TOKEN_EXECUTOR))
                .toList();

        List<Map<String, String>> tokens = new ArrayList<>(futures.size());
        for (CompletableFuture<Map<String, String>> future : futures) {
            tokens.add(future.join());
        }
        return ResponseEntity.ok(tokens);
    }
}

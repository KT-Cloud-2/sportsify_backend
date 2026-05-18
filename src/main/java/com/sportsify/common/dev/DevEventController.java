package com.sportsify.common.dev;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dev/events")
@Profile("local")
@RequiredArgsConstructor
public class DevEventController {

    private final StringRedisTemplate redisTemplate;

    @PostMapping("/publish")
    public ResponseEntity<Map<String, String>> publish(@RequestBody PublishRequest request) {
        redisTemplate.opsForStream().add(request.stream(), Map.of("payload", request.payload()));
        log.info("DEV event published stream={}", request.stream());
        return ResponseEntity.ok(Map.of("stream", request.stream(), "status", "published"));
    }

    record PublishRequest(String stream, String payload) {}
}

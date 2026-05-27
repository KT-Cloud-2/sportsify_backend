package com.sportsify.notification.presentation.controller;

import com.sportsify.notification.application.service.SlackCommandService;
import com.sportsify.notification.infrastructure.slack.SlackSignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackCommandController {

    private final SlackCommandService commandService;
    private final SlackSignatureVerifier signatureVerifier;

    @PostMapping(value = "/command", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> command(
            @RequestHeader("X-Slack-Request-Timestamp") String timestamp,
            @RequestHeader("X-Slack-Signature") String signature,
            @RequestBody String rawBody
    ) {
        if (!signatureVerifier.verify(timestamp, signature, rawBody)) {
            return ResponseEntity.status(401).body("Invalid signature");
        }

        String text = extractParam(rawBody, "text");
        return ResponseEntity.ok(commandService.handle(text));
    }

    private String extractParam(String body, String key) {
        for (String token : body.split("&")) {
            String[] kv = token.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1].replace("+", " "), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}

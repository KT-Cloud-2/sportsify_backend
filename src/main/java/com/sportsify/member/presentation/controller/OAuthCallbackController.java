package com.sportsify.member.presentation.controller;

import com.sportsify.member.presentation.dto.OAuthCallbackResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuthCallbackController {

    @GetMapping("/oauth2/callback")
    public ResponseEntity<OAuthCallbackResponse> oauthCallback(
            @RequestParam String accessToken,
            @RequestParam String refreshToken
    ) {
        return ResponseEntity.ok(new OAuthCallbackResponse(accessToken, refreshToken));
    }
}

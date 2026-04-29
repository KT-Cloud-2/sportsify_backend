package com.sportsify.payment.infrastructure.toss;

import com.sportsify.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.sportsify.payment.infrastructure.toss.dto.TossConfirmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class TossPaymentClient {

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    @Value("${toss.payments.confirm-url}")
    private String confirmUrl;

    public TossConfirmResponse confirm(TossConfirmRequest request) {
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        return RestClient.create()
                .post()
                .uri(confirmUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .body(request)
                .retrieve()
                .body(TossConfirmResponse.class);
    }
}
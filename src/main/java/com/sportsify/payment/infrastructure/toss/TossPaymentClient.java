package com.sportsify.payment.infrastructure.toss;

import com.sportsify.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.sportsify.payment.infrastructure.toss.dto.TossConfirmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class TossPaymentClient {

    private final RestClient restClient;

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    @Value("${toss.payments.confirm-url}")
    private String confirmUrl;

    @Value("${toss.payments.cancel-url}")
    private String cancelUrl;

    public TossPaymentClient(@Qualifier("tossRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public TossConfirmResponse confirm(TossConfirmRequest request) {
        String encodedSecretKey = createEncodedSecretKey();

        return restClient
                .post()
                .uri(confirmUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .body(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, res) -> {
                            log.error("Toss confirm API 호출 실패. status={}", res.getStatusCode());
                            throw new IllegalStateException(
                                    "Toss confirm API 호출 실패: " + res.getStatusCode()
                            );
                        }
                )
                .body(TossConfirmResponse.class);
    }

    public void cancel(String paymentKey, String cancelReason) {
        String encodedSecretKey = createEncodedSecretKey();

        restClient
                .post()
                .uri(cancelUrl, paymentKey)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .body(Map.of("cancelReason", cancelReason))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, res) -> {
                            log.error("Toss cancel API 호출 실패. status={}", res.getStatusCode());
                            throw new IllegalStateException(
                                    "Toss cancel API 호출 실패: " + res.getStatusCode()
                            );
                        }
                )
                .toBodilessEntity();
    }

    private String createEncodedSecretKey() {
        return Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
}

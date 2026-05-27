package com.sportsify.notification.infrastructure.slack;

import com.sportsify.notification.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v0";
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    private final NotificationProperties properties;

    public boolean verify(String timestamp, String signature, String rawBody) {
        try {
            long requestTime = Long.parseLong(timestamp);
            if (Math.abs(System.currentTimeMillis() / 1000 - requestTime) > TIMESTAMP_TOLERANCE_SECONDS) {
                return false;
            }
            String baseString = VERSION + ":" + timestamp + ":" + rawBody;
            String expected = VERSION + "=" + hmacSha256(properties.slack().signingSecret(), baseString);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.warn("Slack 서명 검증 실패 error={}", e.getMessage());
            return false;
        }
    }

    private String hmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}

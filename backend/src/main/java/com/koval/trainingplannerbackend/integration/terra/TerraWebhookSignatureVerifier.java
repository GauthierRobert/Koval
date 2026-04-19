package com.koval.trainingplannerbackend.integration.terra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies Terra webhook signatures.
 * Terra signs the raw request body with HMAC-SHA256 using the signing secret
 * and sends the result in the {@code terra-signature} header.
 */
@Component
public class TerraWebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(TerraWebhookSignatureVerifier.class);
    private static final String ALGO = "HmacSHA256";

    private final String signingSecret;

    public TerraWebhookSignatureVerifier(@Value("${terra.signing-secret:}") String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public boolean verify(String rawBody, String signatureHeader) {
        if (signingSecret == null || signingSecret.isBlank()) {
            log.warn("Terra signing-secret not configured - webhook signature verification skipped");
            return true;
        }
        if (signatureHeader == null || rawBody == null) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), ALGO));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(computed);

            String received = extractV1Signature(signatureHeader);
            if (received == null) {
                return false;
            }

            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    received.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Terra webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Header format: {@code t=<timestamp>,v1=<hex-hmac>}.
     */
    private String extractV1Signature(String header) {
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && "v1".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}

package com.koval.trainingplannerbackend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail-fast validation of required application secrets at startup so misconfiguration
 * is surfaced on boot instead of on the first request that needs the secret.
 *
 * <p>Disabled under the {@code test} profile where secrets are stubbed.
 */
@Component
@Profile("!test")
public class StartupSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupSecretsValidator.class);
    private static final int JWT_SECRET_MIN_LENGTH = 32;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${admin.secret:}")
    private String adminSecret;

    @Value("${strava.client-id:}")
    private String stravaClientId;

    @Value("${strava.client-secret:}")
    private String stravaClientSecret;

    @Value("${google.client-id:}")
    private String googleClientId;

    @Value("${google.client-secret:}")
    private String googleClientSecret;

    @PostConstruct
    void validate() {
        if (isBlank(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is missing. Set the JWT_SECRET environment variable to a value of at least "
                            + JWT_SECRET_MIN_LENGTH + " characters.");
        }
        if (jwtSecret.length() < JWT_SECRET_MIN_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + JWT_SECRET_MIN_LENGTH + " characters for HMAC-SHA256 (got "
                            + jwtSecret.length() + ").");
        }
        if (isBlank(anthropicApiKey)) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY is missing. Set the ANTHROPIC_API_KEY environment variable.");
        }
        if (isBlank(adminSecret)) {
            throw new IllegalStateException(
                    "ADMIN_SECRET is missing. Set the ADMIN_SECRET environment variable.");
        }

        Map<String, String> optional = new LinkedHashMap<>();
        optional.put("STRAVA_CLIENT_ID", stravaClientId);
        optional.put("STRAVA_CLIENT_SECRET", stravaClientSecret);
        optional.put("GOOGLE_CLIENT_ID", googleClientId);
        optional.put("GOOGLE_CLIENT_SECRET", googleClientSecret);
        optional.forEach((name, value) -> {
            if (isBlank(value)) {
                log.warn("Optional secret {} is not configured; the related integration will be unavailable.", name);
            }
        });

        log.info("Startup secret validation passed (JWT len={}, Anthropic key set, admin secret set).",
                jwtSecret.length());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

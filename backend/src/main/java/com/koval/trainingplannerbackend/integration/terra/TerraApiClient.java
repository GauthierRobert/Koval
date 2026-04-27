package com.koval.trainingplannerbackend.integration.terra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin client for Terra's Health & Fitness API.
 * Currently covers widget session creation and user deauthentication — everything
 * we need for the Nolio read path. Activity data arrives via webhook so we don't
 * fetch it here.
 */
@Component
public class TerraApiClient {

    private static final Logger log = LoggerFactory.getLogger(TerraApiClient.class);

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final String devId;
    private final String apiKey;

    public TerraApiClient(@Value("${terra.api-base-url}") String apiBaseUrl,
                          @Value("${terra.dev-id:}") String devId,
                          @Value("${terra.api-key:}") String apiKey) {
        this.apiBaseUrl = apiBaseUrl;
        this.devId = devId;
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Creates a widget session the user can open to connect a provider (e.g. Nolio).
     * {@code referenceId} should be our internal userId — Terra echoes it back in the auth webhook.
     */
    public WidgetSession generateWidgetSession(String referenceId,
                                               List<String> providers,
                                               String successUrl,
                                               String failureUrl) {
        if (devId == null || devId.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Terra integration not configured. Set TERRA_DEV_ID and TERRA_API_KEY on the backend.");
        }
        String url = apiBaseUrl + "/auth/generateWidgetSession";

        Map<String, Object> body = Map.of(
                "reference_id", referenceId,
                "providers", providers,
                "auth_success_redirect_url", successUrl,
                "auth_failure_redirect_url", failureUrl,
                "language", "en"
        );

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers()), Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new TerraApiException("Empty response from Terra widget session endpoint");
            }

            String widgetUrl = (String) responseBody.get("url");
            String sessionId = (String) responseBody.get("session_id");
            Number expiresIn = (Number) responseBody.get("expires_in");

            if (widgetUrl == null) {
                throw new TerraApiException("Terra response missing 'url': " + responseBody);
            }

            return new WidgetSession(widgetUrl, sessionId, expiresIn != null ? expiresIn.longValue() : 900L);
        } catch (HttpClientErrorException e) {
            log.warn("Terra widget session request failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new TerraApiException("Terra widget session failed: " + e.getStatusCode());
        }
    }

    /**
     * Revoke a Terra user. Called when the user disconnects Nolio from our side.
     */
    public void deauthenticateUser(String terraUserId) {
        String url = apiBaseUrl + "/auth/deauthenticateUser?user_id=" + terraUserId;

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
        } catch (HttpClientErrorException e) {
            // Log but don't block unlink on Terra-side failures.
            log.warn("Terra deauth failed for {}: {} {}", terraUserId, e.getStatusCode(), e.getResponseBodyAsString());
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("dev-id", devId);
        headers.set("x-api-key", apiKey);
        return headers;
    }

    public record WidgetSession(String url, String sessionId, long expiresInSeconds) {}

    public static class TerraApiException extends RuntimeException {
        public TerraApiException(String message) { super(message); }
    }
}

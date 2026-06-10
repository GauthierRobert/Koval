package com.koval.trainingplannerbackend.integration.nolio.write;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.auth.User;
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
 * Thin HTTP client for the Nolio API (https://github.com/NolioApp/NolioAPI-Documentation/wiki).
 * Writes are POSTs keyed by the partner-chosen {@code id_partner} carried in the
 * JSON body; reads are GETs with query params. Business 4xx errors come back as
 * plain text (not JSON) — error snippets are surfaced as-is.
 */
@Component
public class NolioApiClient {

    private static final Logger log = LoggerFactory.getLogger(NolioApiClient.class);
    private static final int ERROR_BODY_SNIPPET_LENGTH = 300;

    private final NolioOAuthService oauthService;
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;

    public NolioApiClient(NolioOAuthService oauthService,
                          @Value("${nolio.api-base-url:}") String apiBaseUrl) {
        this.oauthService = oauthService;
        this.apiBaseUrl = apiBaseUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Creates a planned training. The payload must already carry {@code id_partner}.
     * Returns Nolio's echo of the created object (used to capture {@code nolio_id}).
     */
    public JsonNode createPlannedTraining(User user, Map<String, Object> payload) {
        return exchange(tokenFor(user), HttpMethod.POST, "/create/planned/training/", payload);
    }

    /** Updates the planned training identified by the payload's {@code id_partner}. */
    public void updatePlannedTraining(User user, Map<String, Object> payload) {
        exchange(tokenFor(user), HttpMethod.POST, "/update/planned/training/", payload);
    }

    /** Deletes the planned training identified by {@code idPartner}. */
    public void deletePlannedTraining(User user, long idPartner) {
        exchange(tokenFor(user), HttpMethod.POST, "/delete/planned/training/", Map.of("id_partner", idPartner));
    }

    /** Fetches one achieved workout by Nolio id. Returns the array element, or null when absent. */
    public JsonNode getTraining(User user, long nolioId) {
        return firstElement(exchange(tokenFor(user), HttpMethod.GET, "/get/training/?id=" + nolioId, null));
    }

    /** Fetches one planned workout by Nolio id. Returns the array element, or null when absent. */
    public JsonNode getPlannedTraining(User user, long nolioId) {
        return firstElement(exchange(tokenFor(user), HttpMethod.GET, "/get/planned/training/?id=" + nolioId, null));
    }

    /**
     * Resolves the Nolio user id of the token's owner via {@code /get/user/}.
     * Takes a raw access token because it is called during the OAuth callback,
     * before the tokens are persisted on the {@link User}.
     */
    public String fetchNolioUserId(String accessToken) {
        JsonNode profile = exchange(accessToken, HttpMethod.GET, "/get/user/", null);
        if (profile == null || !profile.hasNonNull("id")) {
            throw new NolioApiException("Nolio /get/user/ response missing id: " + profile);
        }
        return profile.get("id").asText();
    }

    private String tokenFor(User user) {
        return oauthService.ensureValidToken(user);
    }

    private static JsonNode firstElement(JsonNode body) {
        if (body == null || !body.isArray() || body.isEmpty()) return null;
        return body.get(0);
    }

    private JsonNode exchange(String token, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiBaseUrl + path, method, new HttpEntity<>(body, headers), JsonNode.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            String detail = snippet(e.getResponseBodyAsString());
            log.warn("Nolio API {} {} failed: {} {}", method, path, e.getStatusCode(), detail);
            throw new NolioApiException(
                    "Nolio API " + method + " " + path + " failed: " + e.getStatusCode()
                            + (detail.isBlank() ? "" : " — " + detail));
        }
    }

    private static String snippet(String body) {
        if (body == null) return "";
        return body.length() > ERROR_BODY_SNIPPET_LENGTH
                ? body.substring(0, ERROR_BODY_SNIPPET_LENGTH)
                : body;
    }

    public static class NolioApiException extends RuntimeException {
        public NolioApiException(String message) { super(message); }
    }
}

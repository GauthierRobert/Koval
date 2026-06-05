package com.koval.trainingplannerbackend.integration.suunto;

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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client over the Suunto Cloud API.
 *
 * <p>Every call carries two credentials: the per-user OAuth bearer token and the partner-level
 * APIM subscription key ({@code Ocp-Apim-Subscription-Key} header) issued in the Suunto API zone.
 *
 * <p>Read path: {@link #listWorkouts} (JSON summaries) → {@link #exportFit} (full FIT binary).
 * Push path: SuuntoPlus guides ({@link #pushGuide}/{@link #updateGuide}/{@link #deleteGuide}) —
 * planned workouts shown on the watch; Suunto's workout upload API only accepts completed
 * activities, so guides are the planned-workout vehicle.
 *
 * <p>Endpoint paths live in the constants below — confirm against the API zone
 * (https://apizone.suunto.com) once production access is granted and adjust in one place.
 */
@Component
public class SuuntoApiClient {

    private static final Logger log = LoggerFactory.getLogger(SuuntoApiClient.class);
    private static final String BASE_URL = "https://cloudapi.suunto.com";

    private static final String USER_PATH = "/v2/user";
    private static final String WORKOUTS_PATH = "/v2/workouts";
    private static final String WORKOUT_EXPORT_FIT_PATH = "/v2/workouts/exportFit/";
    private static final String GUIDES_PATH = "/v3/suuntoplus/guides";

    private static final int LIST_PAGE_SIZE = 100;

    private final RestTemplate restTemplate;
    private final String subscriptionKey;

    public SuuntoApiClient(@Value("${suunto.subscription-key:}") String subscriptionKey) {
        this.subscriptionKey = subscriptionKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
    }

    /** Fetches the user profile (first/last name, etc.) for the authenticated Suunto user. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchUser(String accessToken) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + USER_PATH, HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (RestClientException e) {
            log.warn("Suunto fetchUser failed: {}", e.getMessage());
            return Map.of();
        }
    }

    // ── Workouts (read) ─────────────────────────────────────────────────

    /**
     * Lists workout summaries synced since the given timestamp (epoch millis).
     * Returns at most {@value #LIST_PAGE_SIZE} entries — enough for history imports,
     * which are windowed by the sync service.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listWorkouts(String accessToken, long sinceEpochMillis) {
        String url = BASE_URL + WORKOUTS_PATH + "?since=" + sinceEpochMillis + "&limit=" + LIST_PAGE_SIZE;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), Map.class);
            return extractPayloadList(response.getBody());
        } catch (RestClientException e) {
            log.warn("Suunto listWorkouts failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Fetches a single workout summary by its workout key. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchWorkout(String accessToken, String workoutKey) {
        String url = BASE_URL + WORKOUTS_PATH + "/" + workoutKey;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return Map.of();
            // Suunto wraps single resources in {error, metadata, payload}.
            Object payload = body.get("payload");
            if (payload instanceof Map<?, ?> map) return (Map<String, Object>) map;
            return body;
        } catch (RestClientException e) {
            log.warn("Suunto fetchWorkout {} failed: {}", workoutKey, e.getMessage());
            return Map.of();
        }
    }

    /** Downloads the full FIT file for a workout. Empty when the export fails. */
    public Optional<byte[]> exportFit(String accessToken, String workoutKey) {
        String url = BASE_URL + WORKOUT_EXPORT_FIT_PATH + workoutKey;
        try {
            HttpHeaders headers = bearer(accessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            return body != null && body.length > 0 ? Optional.of(body) : Optional.empty();
        } catch (RestClientException e) {
            log.warn("Suunto exportFit {} failed: {}", workoutKey, e.getMessage());
            return Optional.empty();
        }
    }

    // ── SuuntoPlus guides (planned-workout push) ────────────────────────

    /**
     * Creates a SuuntoPlus guide for the authorized user. Returns the guide's external id
     * (echoed from the doc) on success, empty on failure.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> pushGuide(String accessToken, Map<String, Object> guide) {
        try {
            HttpHeaders headers = bearer(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + GUIDES_PATH, HttpMethod.POST, new HttpEntity<>(guide, headers), Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) return Optional.empty();
            Object externalId = guide.get("externalId");
            return Optional.ofNullable(externalId).map(String::valueOf);
        } catch (RestClientException e) {
            log.warn("Suunto pushGuide failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Replaces an existing guide, addressed by its external id. */
    @SuppressWarnings("unchecked")
    public void updateGuide(String accessToken, String guideExternalId, Map<String, Object> guide) {
        try {
            HttpHeaders headers = bearer(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(BASE_URL + GUIDES_PATH + "/" + guideExternalId,
                    HttpMethod.PUT, new HttpEntity<>(guide, headers), Map.class);
        } catch (RestClientException e) {
            log.warn("Suunto updateGuide {} failed: {}", guideExternalId, e.getMessage());
            throw new IllegalStateException("Suunto guide update failed", e);
        }
    }

    /** Best-effort delete of a guide by its external id. */
    public void deleteGuide(String accessToken, String guideExternalId) {
        try {
            restTemplate.exchange(BASE_URL + GUIDES_PATH + "/" + guideExternalId,
                    HttpMethod.DELETE, new HttpEntity<>(bearer(accessToken)), Void.class);
        } catch (RestClientException e) {
            log.warn("Suunto deleteGuide {} failed: {}", guideExternalId, e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Suunto wraps list responses in {@code {error, metadata, payload: [...]}}. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractPayloadList(Map<String, Object> body) {
        if (body == null) return List.of();
        Object payload = body.get("payload");
        if (payload instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (subscriptionKey != null && !subscriptionKey.isBlank()) {
            headers.set("Ocp-Apim-Subscription-Key", subscriptionKey);
        }
        return headers;
    }
}

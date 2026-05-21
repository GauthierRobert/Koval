package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.config.exceptions.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin client over the Polar AccessLink REST API.
 *
 * <p>Read path (exercises) uses the transaction pattern:
 * <ol>
 *   <li>{@link #openExerciseTransaction} — creates a snapshot of new exercises (204 if none).</li>
 *   <li>{@link #listExercises} — URLs of each exercise in the transaction.</li>
 *   <li>{@link #fetchExercise} — per-exercise summary JSON.</li>
 *   <li>{@link #commitExerciseTransaction} — marks them as consumed.</li>
 * </ol>
 *
 * <p>Write path: {@link #createTrainingTarget} pushes a planned workout to the user's Polar Flow.
 */
@Component
public class PolarApiClient {

    private static final Logger log = LoggerFactory.getLogger(PolarApiClient.class);
    private static final String BASE_URL = "https://www.polaraccesslink.com";

    private final RestTemplate restTemplate;

    public PolarApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Registers the user in AccessLink after the initial OAuth token exchange.
     * Polar requires this once per user; subsequent calls return 409 which we treat as success.
     *
     * @param memberId stable internal id we want stored on Polar's side (Koval user id)
     */
    @SuppressWarnings("unchecked")
    public void registerUser(String accessToken, String memberId) {
        HttpHeaders headers = bearer(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("member-id", memberId);
        try {
            restTemplate.exchange(BASE_URL + "/v3/users", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.debug("Polar user {} already registered", memberId);
                return;
            }
            throw new ExternalServiceException("Polar", "registerUser failed: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Polar", "registerUser failed: " + e.getMessage(), e);
        }
    }

    /** Fetches the user profile (first/last name, etc.) for a registered AccessLink user. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchUser(String accessToken, String polarUserId) {
        String url = BASE_URL + "/v3/users/" + polarUserId;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (RestClientException e) {
            log.warn("Polar fetchUser {} failed: {}", polarUserId, e.getMessage());
            return Map.of();
        }
    }

    // ── Exercises (read) ────────────────────────────────────────────────

    /** Opens a new exercise transaction. Returns transaction id, or empty when there's nothing new (204). */
    @SuppressWarnings("unchecked")
    public java.util.Optional<String> openExerciseTransaction(String accessToken, String polarUserId) {
        String url = BASE_URL + "/v3/users/" + polarUserId + "/exercise-transactions";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(bearer(accessToken)), Map.class);
            if (response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null) {
                return java.util.Optional.empty();
            }
            Object txId = response.getBody().get("transaction-id");
            return java.util.Optional.ofNullable(txId).map(String::valueOf);
        } catch (HttpClientErrorException.NotFound e) {
            return java.util.Optional.empty();
        } catch (RestClientException e) {
            throw new ExternalServiceException("Polar", "openExerciseTransaction failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> listExercises(String accessToken, String polarUserId, String txId) {
        String url = BASE_URL + "/v3/users/" + polarUserId + "/exercise-transactions/" + txId;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), Map.class);
            if (response.getBody() == null) return List.of();
            Object exercises = response.getBody().get("exercises");
            if (exercises instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        } catch (RestClientException e) {
            log.warn("Polar listExercises failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchExercise(String accessToken, String exerciseUrl) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    URI.create(exerciseUrl), HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (RestClientException e) {
            log.warn("Polar fetchExercise {} failed: {}", exerciseUrl, e.getMessage());
            return Map.of();
        }
    }

    public void commitExerciseTransaction(String accessToken, String polarUserId, String txId) {
        String url = BASE_URL + "/v3/users/" + polarUserId + "/exercise-transactions/" + txId;
        try {
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(bearer(accessToken)), Void.class);
        } catch (RestClientException e) {
            log.warn("Polar commitExerciseTransaction failed: {}", e.getMessage());
        }
    }

    // ── Training targets (write) ────────────────────────────────────────

    /**
     * POSTs a training target. Returns the Polar-assigned id (or empty on failure / unsupported response).
     */
    @SuppressWarnings("unchecked")
    public java.util.Optional<String> createTrainingTarget(String accessToken, String polarUserId,
                                                            Map<String, Object> payload) {
        String url = BASE_URL + "/v3/users/" + polarUserId + "/training-targets";
        HttpHeaders headers = bearer(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            if (response.getBody() == null) return java.util.Optional.empty();
            Object id = response.getBody().getOrDefault("id", response.getBody().get("training-target-id"));
            return java.util.Optional.ofNullable(id).map(String::valueOf);
        } catch (HttpClientErrorException e) {
            throw new ExternalServiceException("Polar",
                    "createTrainingTarget failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Polar", "createTrainingTarget failed: " + e.getMessage(), e);
        }
    }

    /** DELETEs a previously-created training target. Best-effort — 404 is treated as success. */
    public void deleteTrainingTarget(String accessToken, String polarUserId, String trainingTargetId) {
        String url = BASE_URL + "/v3/users/" + polarUserId + "/training-targets/" + trainingTargetId;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(bearer(accessToken)), Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Already gone — fine.
        } catch (RestClientException e) {
            log.warn("Polar deleteTrainingTarget {} failed: {}", trainingTargetId, e.getMessage());
        }
    }

    private static HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}

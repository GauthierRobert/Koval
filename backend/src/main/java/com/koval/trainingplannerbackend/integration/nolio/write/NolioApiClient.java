package com.koval.trainingplannerbackend.integration.nolio.write;

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
 * Thin HTTP client for the Nolio workouts API.
 * Endpoint paths are relative to {@code nolio.api-base-url} and match the shape
 * expected by {@link NolioWorkoutMapper}. Exact request/response bodies may need
 * adjustment once Nolio's developer portal is available.
 */
@Component
public class NolioApiClient {

    private static final Logger log = LoggerFactory.getLogger(NolioApiClient.class);

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

    @SuppressWarnings("unchecked")
    public String createWorkout(User user, Map<String, Object> payload) {
        ResponseEntity<Map> response = send(user, HttpMethod.POST, "/workouts", payload);
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new NolioApiException("Empty response from Nolio workout create");
        }
        Object id = body.getOrDefault("id", body.get("workoutId"));
        if (id == null) {
            throw new NolioApiException("Nolio create response missing workout id: " + body);
        }
        return String.valueOf(id);
    }

    public void updateWorkout(User user, String workoutId, Map<String, Object> payload) {
        send(user, HttpMethod.PUT, "/workouts/" + workoutId, payload);
    }

    public void deleteWorkout(User user, String workoutId) {
        send(user, HttpMethod.DELETE, "/workouts/" + workoutId, null);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> send(User user, HttpMethod method, String path, Object body) {
        String token = oauthService.ensureValidToken(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token);

        try {
            return restTemplate.exchange(
                    apiBaseUrl + path, method, new HttpEntity<>(body, headers), Map.class);
        } catch (HttpClientErrorException e) {
            log.warn("Nolio API {} {} failed: {} {}", method, path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new NolioApiException("Nolio API " + method + " " + path + " failed: " + e.getStatusCode());
        }
    }

    public static class NolioApiException extends RuntimeException {
        public NolioApiException(String message) { super(message); }
    }
}

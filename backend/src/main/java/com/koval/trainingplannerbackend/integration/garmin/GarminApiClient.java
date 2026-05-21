package com.koval.trainingplannerbackend.integration.garmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.config.exceptions.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Garmin Connect API client for fetching activities and FIT files.
 */
@Component
public class GarminApiClient {

    private static final Logger log = LoggerFactory.getLogger(GarminApiClient.class);
    private static final String BASE_URL = "https://apis.garmin.com";
    private static final String TRAINING_API_BASE = BASE_URL + "/training-api";

    private final GarminOAuthService oauthService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        restTemplate = new RestTemplate(factory);
    }

    public GarminApiClient(GarminOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    /**
     * Fetch activities between start and end epoch seconds.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchActivities(String accessToken, String tokenSecret,
                                                      long startEpoch, long endEpoch) {
        String url = BASE_URL + "/wellness-api/rest/activityDetails"
                + "?uploadStartTimeInSeconds=" + startEpoch
                + "&uploadEndTimeInSeconds=" + endEpoch;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", oauthService.signRequest("GET", url, accessToken, tokenSecret));

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch Garmin activities: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Training API (write path) ───────────────────────────────────────

    /**
     * Create a workout on Garmin Connect. Returns the Garmin-assigned workoutId.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> createWorkout(String accessToken, String tokenSecret, Map<String, Object> payload) {
        String url = TRAINING_API_BASE + "/workout";
        String body = toJson(payload);
        HttpHeaders headers = jsonHeaders(oauthService.signRequestWithBodyHash(
                "POST", url, accessToken, tokenSecret, body));
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            if (response.getBody() == null) return Optional.empty();
            Object id = response.getBody().getOrDefault("workoutId", response.getBody().get("id"));
            return Optional.ofNullable(id).map(String::valueOf);
        } catch (HttpClientErrorException e) {
            throw new ExternalServiceException("Garmin",
                    "createWorkout failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Garmin", "createWorkout failed: " + e.getMessage(), e);
        }
    }

    public void updateWorkout(String accessToken, String tokenSecret, String workoutId,
                              Map<String, Object> payload) {
        String url = TRAINING_API_BASE + "/workout/" + workoutId;
        String body = toJson(payload);
        HttpHeaders headers = jsonHeaders(oauthService.signRequestWithBodyHash(
                "PUT", url, accessToken, tokenSecret, body));
        try {
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Already gone.
        } catch (RestClientException e) {
            throw new ExternalServiceException("Garmin", "updateWorkout failed: " + e.getMessage(), e);
        }
    }

    public void deleteWorkout(String accessToken, String tokenSecret, String workoutId) {
        String url = TRAINING_API_BASE + "/workout/" + workoutId;
        HttpHeaders headers = jsonHeaders(oauthService.signRequest("DELETE", url, accessToken, tokenSecret));
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Already gone.
        } catch (RestClientException e) {
            log.warn("Garmin deleteWorkout {} failed: {}", workoutId, e.getMessage());
        }
    }

    /**
     * Schedule a workout on a calendar date. Returns the Garmin-assigned schedule id.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> scheduleWorkout(String accessToken, String tokenSecret,
                                            String workoutId, java.time.LocalDate date) {
        String url = TRAINING_API_BASE + "/schedule";
        Map<String, Object> payload = Map.of("workoutId", workoutId, "date", date.toString());
        String body = toJson(payload);
        HttpHeaders headers = jsonHeaders(oauthService.signRequestWithBodyHash(
                "POST", url, accessToken, tokenSecret, body));
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            if (response.getBody() == null) return Optional.empty();
            Object id = response.getBody().getOrDefault("workoutScheduleId",
                    response.getBody().getOrDefault("scheduleId", response.getBody().get("id")));
            return Optional.ofNullable(id).map(String::valueOf);
        } catch (HttpClientErrorException e) {
            throw new ExternalServiceException("Garmin",
                    "scheduleWorkout failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Garmin", "scheduleWorkout failed: " + e.getMessage(), e);
        }
    }

    public void unscheduleWorkout(String accessToken, String tokenSecret, String scheduleId) {
        String url = TRAINING_API_BASE + "/schedule/" + scheduleId;
        HttpHeaders headers = jsonHeaders(oauthService.signRequest("DELETE", url, accessToken, tokenSecret));
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Already gone.
        } catch (RestClientException e) {
            log.warn("Garmin unscheduleWorkout {} failed: {}", scheduleId, e.getMessage());
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new ExternalServiceException("Garmin", "Failed to serialize payload", e);
        }
    }

    private static HttpHeaders jsonHeaders(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    /**
     * Fetch a single activity by ID.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchActivity(String accessToken, String tokenSecret, String activityId) {
        String url = BASE_URL + "/wellness-api/rest/activityDetails/" + activityId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", oauthService.signRequest("GET", url, accessToken, tokenSecret));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch Garmin activity {}: {}", activityId, e.getMessage());
            return Map.of();
        }
    }
}

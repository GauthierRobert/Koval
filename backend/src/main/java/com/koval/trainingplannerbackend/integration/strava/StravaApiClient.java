package com.koval.trainingplannerbackend.integration.strava;

import com.koval.trainingplannerbackend.auth.StravaOAuthService;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class StravaApiClient {

    private static final Logger log = LoggerFactory.getLogger(StravaApiClient.class);
    private static final String ACTIVITIES_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final int PAGE_SIZE = 200;
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final StravaOAuthService stravaOAuthService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public StravaApiClient(StravaOAuthService stravaOAuthService, UserRepository userRepository) {
        this.stravaOAuthService = stravaOAuthService;
        this.userRepository = userRepository;
    }

    public String ensureValidToken(User user) {
        long now = Instant.now().getEpochSecond();
        if (user.getStravaTokenExpiresAt() != null && user.getStravaTokenExpiresAt() > now + TOKEN_EXPIRY_BUFFER_SECONDS) {
            return user.getStravaAccessToken();
        }

        log.info("Refreshing Strava token for user {}", user.getId());
        StravaOAuthService.StravaTokenResponse response = stravaOAuthService.refreshAccessToken(user.getStravaRefreshToken());
        user.setStravaAccessToken(response.getAccessToken());
        user.setStravaRefreshToken(response.getRefreshToken());
        user.setStravaTokenExpiresAt(response.getExpiresAt());
        userRepository.save(user);

        return response.getAccessToken();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchActivitiesAfter(User user, long afterEpoch) {
        String token = ensureValidToken(user);
        List<Map<String, Object>> allActivities = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = ACTIVITIES_URL + "?after=" + afterEpoch + "&per_page=" + PAGE_SIZE + "&page=" + page;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            try {
                ResponseEntity<List> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), List.class);

                List<Map<String, Object>> activities = response.getBody();
                if (activities == null || activities.isEmpty()) break;

                allActivities.addAll(activities);
                if (activities.size() < PAGE_SIZE) break;

                page++;
            } catch (HttpClientErrorException.TooManyRequests e) {
                throw new RateLimitException("Strava API rate limit exceeded");
            }
        }

        return allActivities;
    }

    /**
     * Fetch laps for a single activity.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchLaps(User user, String activityId) {
        String token = ensureValidToken(user);
        String url = "https://www.strava.com/api/v3/activities/" + activityId + "/laps";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            List<Map<String, Object>> laps = response.getBody();
            return laps != null ? laps : List.of();
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RateLimitException("Strava API rate limit exceeded");
        } catch (Exception e) {
            log.warn("Failed to fetch laps for activity {}: {}", activityId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch per-second streams for a single activity.
     * Returns a map keyed by stream type (time, watts, heartrate, cadence, velocity_smooth, distance, altitude).
     * Each value is a List of Numbers.
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<? extends Number>> fetchStreams(User user, String activityId) {
        String token = ensureValidToken(user);
        String url = "https://www.strava.com/api/v3/activities/" + activityId
                + "/streams?keys=time,watts,heartrate,cadence,velocity_smooth,distance,altitude&key_by_type=true";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) return Map.of();

            // Strava returns { "time": { "data": [...] }, "watts": { "data": [...] }, ... }
            Map<String, List<? extends Number>> result = new java.util.HashMap<>();
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> streamObj) {
                    Object data = ((Map<String, Object>) streamObj).get("data");
                    if (data instanceof List<?> list) {
                        result.put(entry.getKey(), (List<? extends Number>) list);
                    }
                }
            }
            return result;
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RateLimitException("Strava API rate limit exceeded");
        } catch (Exception e) {
            log.warn("Failed to fetch streams for activity {}: {}", activityId, e.getMessage());
            return Map.of();
        }
    }
}

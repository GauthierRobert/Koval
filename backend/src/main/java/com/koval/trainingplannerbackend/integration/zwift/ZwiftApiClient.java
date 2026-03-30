package com.koval.trainingplannerbackend.integration.zwift;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Zwift unofficial API client.
 * WARNING: These endpoints are reverse-engineered and may break.
 */
@Component
public class ZwiftApiClient {

    private static final Logger log = LoggerFactory.getLogger(ZwiftApiClient.class);
    private static final String BASE_URL = "https://us-or-rly101.zwift.com";

    private final ZwiftAuthService zwiftAuthService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public ZwiftApiClient(ZwiftAuthService zwiftAuthService, UserRepository userRepository) {
        this.zwiftAuthService = zwiftAuthService;
        this.userRepository = userRepository;
    }

    /**
     * Ensure the access token is valid, refresh if needed.
     */
    public String ensureValidToken(User user) {
        // Try the current token first — if it fails with 401, refresh
        return user.getZwiftAccessToken();
    }

    /**
     * Fetch recent activities for the user.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchActivities(User user, int limit) {
        String token = ensureValidToken(user);
        String url = BASE_URL + "/api/profiles/" + user.getZwiftUserId() + "/activities?limit=" + limit;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (HttpClientErrorException.Unauthorized e) {
            // Try to refresh token
            token = refreshAndSaveToken(user);
            headers.setBearerAuth(token);
            try {
                ResponseEntity<List> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
                return response.getBody() != null ? response.getBody() : List.of();
            } catch (Exception retryEx) {
                log.warn("Failed to fetch Zwift activities after refresh: {}", retryEx.getMessage());
                return List.of();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Zwift activities: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch FIT file for a specific activity.
     */
    public byte[] fetchActivityFit(User user, String activityId) {
        String token = ensureValidToken(user);
        String url = BASE_URL + "/api/profiles/" + user.getZwiftUserId() + "/activities/" + activityId + "/fit";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch Zwift FIT for activity {}: {}", activityId, e.getMessage());
            return null;
        }
    }

    private String refreshAndSaveToken(User user) {
        ZwiftAuthService.ZwiftTokenResponse refreshed = zwiftAuthService.refreshToken(user.getZwiftRefreshToken());
        user.setZwiftAccessToken(refreshed.accessToken());
        if (refreshed.refreshToken() != null) {
            user.setZwiftRefreshToken(refreshed.refreshToken());
        }
        userRepository.save(user);
        return refreshed.accessToken();
    }
}

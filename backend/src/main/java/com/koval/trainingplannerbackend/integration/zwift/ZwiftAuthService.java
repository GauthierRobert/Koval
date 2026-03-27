package com.koval.trainingplannerbackend.integration.zwift;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Zwift authentication using the unofficial Keycloak endpoint.
 * WARNING: This is not an official API and may break at any time.
 */
@Service
public class ZwiftAuthService {

    private static final Logger log = LoggerFactory.getLogger(ZwiftAuthService.class);
    private static final String TOKEN_URL = "https://secure.zwift.com/auth/realms/zwift/protocol/openid-connect/token";
    private static final String CLIENT_ID = "Zwift_Mobile_Link";

    @Value("${zwift.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Authenticate with Zwift using username/password.
     * Returns access token, refresh token, and Zwift profile ID.
     */
    @SuppressWarnings("unchecked")
    public ZwiftTokenResponse authenticate(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", CLIENT_ID);
        body.add("grant_type", "password");
        body.add("username", username);
        body.add("password", password);

        ResponseEntity<Map> response = restTemplate.exchange(
                TOKEN_URL, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> tokenBody = response.getBody();
        if (tokenBody == null) throw new RuntimeException("Empty Zwift token response");

        String accessToken = (String) tokenBody.get("access_token");
        String refreshToken = (String) tokenBody.get("refresh_token");

        // Extract Zwift user ID from the /api/profiles/me endpoint
        String zwiftUserId = fetchProfileId(accessToken);

        return new ZwiftTokenResponse(accessToken, refreshToken, zwiftUserId);
    }

    /**
     * Refresh an expired access token.
     */
    @SuppressWarnings("unchecked")
    public ZwiftTokenResponse refreshToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", CLIENT_ID);
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                TOKEN_URL, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> tokenBody = response.getBody();
        if (tokenBody == null) throw new RuntimeException("Empty Zwift token refresh response");

        String newAccessToken = (String) tokenBody.get("access_token");
        String newRefreshToken = (String) tokenBody.get("refresh_token");

        return new ZwiftTokenResponse(newAccessToken, newRefreshToken, null);
    }

    @SuppressWarnings("unchecked")
    private String fetchProfileId(String accessToken) {
        String url = "https://us-or-rly101.zwift.com/api/profiles/me";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> profile = response.getBody();
            if (profile != null && profile.containsKey("id")) {
                return String.valueOf(profile.get("id"));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Zwift profile: {}", e.getMessage());
        }
        return null;
    }

    public record ZwiftTokenResponse(String accessToken, String refreshToken, String zwiftUserId) {}
}

package com.koval.trainingplannerbackend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service for Strava OAuth2 integration.
 */
@Service
public class StravaOAuthService {

    @Value("${strava.client-id:}")
    private String clientId;

    @Value("${strava.client-secret:}")
    private String clientSecret;

    @Value("${strava.redirect-uri:http://localhost:8080/api/auth/strava/callback}")
    private String redirectUri;

    private static final String STRAVA_AUTH_URL = "https://www.strava.com/oauth/authorize";
    private static final String STRAVA_TOKEN_URL = "https://www.strava.com/oauth/token";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Generate the Strava authorization URL.
     */
    public String getAuthorizationUrl() {
        return STRAVA_AUTH_URL +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + redirectUri +
                "&approval_prompt=auto" +
                "&scope=read,activity:read_all,profile:read_all";
    }

    /**
     * Exchange authorization code for access/refresh tokens.
     */
    public StravaTokenResponse exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(STRAVA_TOKEN_URL, request, Map.class);
        return parseTokenResponse(response.getBody());
    }

    /**
     * Refresh an expired access token.
     */
    public StravaTokenResponse refreshAccessToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(STRAVA_TOKEN_URL, request, Map.class);
        return parseTokenResponse(response.getBody());
    }

    private StravaTokenResponse parseTokenResponse(Map<String, Object> responseBody) {
        if (responseBody == null) {
            throw new RuntimeException("Empty response from Strava");
        }

        StravaTokenResponse tokenResponse = new StravaTokenResponse();
        tokenResponse.setAccessToken((String) responseBody.get("access_token"));
        tokenResponse.setRefreshToken((String) responseBody.get("refresh_token"));
        tokenResponse.setExpiresAt(((Number) responseBody.get("expires_at")).longValue());

        // Parse athlete info
        Map<String, Object> athlete = (Map<String, Object>) responseBody.get("athlete");
        if (athlete != null) {
            tokenResponse.setAthleteId(String.valueOf(athlete.get("id")));
            tokenResponse.setFirstName((String) athlete.get("firstname"));
            tokenResponse.setLastName((String) athlete.get("lastname"));
            tokenResponse.setProfilePicture((String) athlete.get("profile"));
        }

        return tokenResponse;
    }

    /**
     * DTO for Strava token response.
     */
    public static class StravaTokenResponse {
        private String accessToken;
        private String refreshToken;
        private Long expiresAt;
        private String athleteId;
        private String firstName;
        private String lastName;
        private String profilePicture;

        // Getters and Setters
        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public Long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public String getAthleteId() {
            return athleteId;
        }

        public void setAthleteId(String athleteId) {
            this.athleteId = athleteId;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getProfilePicture() {
            return profilePicture;
        }

        public void setProfilePicture(String profilePicture) {
            this.profilePicture = profilePicture;
        }

        public String getDisplayName() {
            return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
        }
    }
}

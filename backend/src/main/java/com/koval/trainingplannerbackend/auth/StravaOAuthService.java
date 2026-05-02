package com.koval.trainingplannerbackend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Service for Strava OAuth2 integration.
 */
@Service
public class StravaOAuthService {

    @Value("${strava.client-id:}")
    private String clientId;

    @Value("${strava.client-secret:}")
    private String clientSecret;

    @Value("${strava.redirect-uri:http://localhost:4200/auth/callback}")
    private String redirectUri;

    private static final String STRAVA_AUTH_URL = "https://www.strava.com/oauth/authorize";
    private static final String STRAVA_TOKEN_URL = "https://www.strava.com/oauth/token";
    private static final String STRAVA_ATHLETE_URL = "https://www.strava.com/api/v3/athlete";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Generate the Strava authorization URL.
     */
    public String getAuthorizationUrl(String overrideRedirectUri) {
        String effectiveRedirectUri = Optional.ofNullable(overrideRedirectUri)
                .filter(s -> !s.isBlank())
                .orElse(redirectUri);
        return STRAVA_AUTH_URL +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + effectiveRedirectUri +
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
        StravaTokenResponse tokenResponse = parseTokenResponse(response.getBody());

        // Fetch full athlete profile (includes email) — not available in token response
        return fetchAthleteEmail(tokenResponse)
                .map(tokenResponse::withEmail)
                .orElse(tokenResponse);
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

    private Optional<String> fetchAthleteEmail(StravaTokenResponse tokenResponse) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenResponse.accessToken());
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    STRAVA_ATHLETE_URL, HttpMethod.GET, request, Map.class);
            return Optional.ofNullable(response.getBody())
                    .map(athlete -> athlete.get("email"))
                    .map(String.class::cast);
        } catch (RestClientException e) {
            // Non-critical — proceed without email
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private StravaTokenResponse parseTokenResponse(Map<String, Object> responseBody) {
        if (responseBody == null) {
            throw new RuntimeException("Empty response from Strava");
        }
        Map<String, Object> athlete = (Map<String, Object>) responseBody.get("athlete");
        return new StravaTokenResponse(
                (String) responseBody.get("access_token"),
                (String) responseBody.get("refresh_token"),
                ((Number) responseBody.get("expires_at")).longValue(),
                Optional.ofNullable(athlete).map(a -> String.valueOf(a.get("id"))).orElse(null),
                Optional.ofNullable(athlete).map(a -> (String) a.get("firstname")).orElse(null),
                Optional.ofNullable(athlete).map(a -> (String) a.get("lastname")).orElse(null),
                Optional.ofNullable(athlete).map(a -> (String) a.get("profile")).orElse(null),
                null);
    }
}

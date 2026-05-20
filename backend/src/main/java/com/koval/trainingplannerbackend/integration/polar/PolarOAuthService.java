package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

/**
 * OAuth 2.0 client for Polar AccessLink.
 * <p>
 * Flow: redirect user to {@link #AUTHORIZE_URL} with our client id + redirect uri, Polar redirects
 * back with {@code ?code=...}, we exchange for an access/refresh token with HTTP Basic auth.
 * <p>
 * Polar requires a one-time {@code POST /v3/users} call after the first token exchange to register
 * the user in AccessLink — see {@link PolarApiClient#registerUser}.
 */
@Service
public class PolarOAuthService {

    private static final Logger log = LoggerFactory.getLogger(PolarOAuthService.class);

    private static final String AUTHORIZE_URL = "https://flow.polar.com/oauth2/authorization";
    private static final String TOKEN_URL = "https://polarremote.com/v2/oauth2/token";
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public PolarOAuthService(UserRepository userRepository,
                             @Value("${polar.client-id:}") String clientId,
                             @Value("${polar.client-secret:}") String clientSecret,
                             @Value("${polar.redirect-uri:http://localhost:4200/auth/polar/callback}") String redirectUri) {
        this.userRepository = userRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /** Builds the URL the user's browser must visit to authorize Koval against their Polar account. */
    public String getAuthorizationUrl(String state) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Polar integration is not configured. Set POLAR_CLIENT_ID and POLAR_CLIENT_SECRET.");
        }
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "accesslink.read_all")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public PolarTokenResponse exchangeCodeForToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        return requestToken(body);
    }

    public PolarTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        return requestToken(body);
    }

    /** Returns a valid access token, refreshing if needed. */
    public String ensureValidToken(User user) {
        long now = Instant.now().getEpochSecond();
        if (user.getPolarTokenExpiresAt() != null
                && user.getPolarTokenExpiresAt() > now + TOKEN_EXPIRY_BUFFER_SECONDS
                && user.getPolarAccessToken() != null) {
            return user.getPolarAccessToken();
        }
        if (user.getPolarRefreshToken() == null || user.getPolarRefreshToken().isBlank()) {
            // Polar v2 long-lived tokens (no refresh token issued) — return what we have.
            if (user.getPolarAccessToken() != null) return user.getPolarAccessToken();
            throw new ExternalServiceException("Polar", "No Polar access token stored for user " + user.getId());
        }
        log.info("Refreshing Polar token for user {}", user.getId());
        PolarTokenResponse refreshed = refresh(user.getPolarRefreshToken());
        applyTokens(user, refreshed);
        userRepository.save(user);
        return refreshed.accessToken();
    }

    public void applyTokens(User user, PolarTokenResponse tokens) {
        user.setPolarAccessToken(tokens.accessToken());
        if (tokens.refreshToken() != null) user.setPolarRefreshToken(tokens.refreshToken());
        if (tokens.expiresAt() != null) user.setPolarTokenExpiresAt(tokens.expiresAt());
        if (tokens.polarUserId() != null) user.setPolarUserId(tokens.polarUserId());
    }

    @SuppressWarnings("unchecked")
    private PolarTokenResponse requestToken(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(clientId, clientSecret);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    TOKEN_URL, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> payload = response.getBody();
            if (payload == null) {
                throw new ExternalServiceException("Polar", "Empty response from Polar token endpoint");
            }
            String accessToken = (String) payload.get("access_token");
            String refreshToken = (String) payload.get("refresh_token");
            Number expiresIn = (Number) payload.get("expires_in");
            // Polar returns x_user_id (numeric) on the initial exchange.
            Object xUserId = payload.get("x_user_id");
            String polarUserId = xUserId != null ? String.valueOf(xUserId) : null;
            Long expiresAt = expiresIn != null
                    ? Instant.now().getEpochSecond() + expiresIn.longValue()
                    : null;
            return new PolarTokenResponse(accessToken, refreshToken, expiresAt, polarUserId);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Polar", "Token request failed: " + e.getMessage(), e);
        }
    }

    public record PolarTokenResponse(String accessToken, String refreshToken, Long expiresAt, String polarUserId) {}
}

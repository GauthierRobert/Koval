package com.koval.trainingplannerbackend.integration.suunto;

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
 * OAuth 2.0 client for the Suunto Cloud API.
 * <p>
 * Flow: redirect to {@link #AUTHORIZE_URL} with client id + redirect uri, Suunto redirects
 * back with {@code ?code=...}, we exchange for an access/refresh token with HTTP Basic auth.
 * The token response includes a {@code user} field (Suunto username) which we store as the
 * external account id.
 */
@Service
public class SuuntoOAuthService {

    private static final Logger log = LoggerFactory.getLogger(SuuntoOAuthService.class);

    private static final String AUTHORIZE_URL = "https://cloudapi-oauth.suunto.com/oauth/authorize";
    private static final String TOKEN_URL = "https://cloudapi-oauth.suunto.com/oauth/token";
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public SuuntoOAuthService(UserRepository userRepository,
                              @Value("${suunto.client-id:}") String clientId,
                              @Value("${suunto.client-secret:}") String clientSecret,
                              @Value("${suunto.redirect-uri:http://localhost:4200/auth/suunto/callback}") String redirectUri) {
        this.userRepository = userRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /** Builds the URL the user's browser must visit to authorize Koval against their Suunto account. */
    public String getAuthorizationUrl(String state) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Suunto integration is not configured. Set SUUNTO_CLIENT_ID and SUUNTO_CLIENT_SECRET.");
        }
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "workout")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public SuuntoTokenResponse exchangeCodeForToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        return requestToken(body);
    }

    public SuuntoTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        return requestToken(body);
    }

    /** Returns a valid access token, refreshing if needed. */
    public String ensureValidToken(User user) {
        long now = Instant.now().getEpochSecond();
        if (user.getSuuntoTokenExpiresAt() != null
                && user.getSuuntoTokenExpiresAt() > now + TOKEN_EXPIRY_BUFFER_SECONDS
                && user.getSuuntoAccessToken() != null) {
            return user.getSuuntoAccessToken();
        }
        if (user.getSuuntoRefreshToken() == null || user.getSuuntoRefreshToken().isBlank()) {
            if (user.getSuuntoAccessToken() != null) return user.getSuuntoAccessToken();
            throw new ExternalServiceException("Suunto", "No Suunto access token stored for user " + user.getId());
        }
        log.info("Refreshing Suunto token for user {}", user.getId());
        SuuntoTokenResponse refreshed = refresh(user.getSuuntoRefreshToken());
        applyTokens(user, refreshed);
        userRepository.save(user);
        return refreshed.accessToken();
    }

    public void applyTokens(User user, SuuntoTokenResponse tokens) {
        user.setSuuntoAccessToken(tokens.accessToken());
        if (tokens.refreshToken() != null) user.setSuuntoRefreshToken(tokens.refreshToken());
        if (tokens.expiresAt() != null) user.setSuuntoTokenExpiresAt(tokens.expiresAt());
        if (tokens.suuntoUserId() != null) user.setSuuntoUserId(tokens.suuntoUserId());
    }

    @SuppressWarnings("unchecked")
    private SuuntoTokenResponse requestToken(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(clientId, clientSecret);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    TOKEN_URL, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> payload = response.getBody();
            if (payload == null) {
                throw new ExternalServiceException("Suunto", "Empty response from Suunto token endpoint");
            }
            String accessToken = (String) payload.get("access_token");
            String refreshToken = (String) payload.get("refresh_token");
            Number expiresIn = (Number) payload.get("expires_in");
            // Suunto returns the username in the "user" field of the token payload.
            Object suuntoUser = payload.get("user");
            String suuntoUserId = suuntoUser != null ? String.valueOf(suuntoUser) : null;
            Long expiresAt = expiresIn != null
                    ? Instant.now().getEpochSecond() + expiresIn.longValue()
                    : null;
            return new SuuntoTokenResponse(accessToken, refreshToken, expiresAt, suuntoUserId);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Suunto", "Token request failed: " + e.getMessage(), e);
        }
    }

    public record SuuntoTokenResponse(String accessToken, String refreshToken, Long expiresAt, String suuntoUserId) {}
}

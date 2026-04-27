package com.koval.trainingplannerbackend.integration.nolio.write;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

/**
 * OAuth2 client for the direct Nolio API (write path - push planned workouts).
 * This is independent of Terra: Terra handles read, this handles write.
 *
 * Note: Exact authorize/token URLs are supplied via config since Nolio's
 * developer portal credentials are per-tenant.
 */
@Service
public class NolioOAuthService {

    private static final Logger log = LoggerFactory.getLogger(NolioOAuthService.class);
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private final String clientId;
    private final String clientSecret;
    private final String authUrl;
    private final String tokenUrl;
    private final String redirectUri;
    private final String scope;

    public NolioOAuthService(UserRepository userRepository,
                             @Value("${nolio.client-id:}") String clientId,
                             @Value("${nolio.client-secret:}") String clientSecret,
                             @Value("${nolio.auth-url:}") String authUrl,
                             @Value("${nolio.token-url:}") String tokenUrl,
                             @Value("${nolio.redirect-uri:http://localhost:4200/auth/nolio/callback}") String redirectUri,
                             @Value("${nolio.scope:workouts:write}") String scope) {
        this.userRepository = userRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authUrl = authUrl;
        this.tokenUrl = tokenUrl;
        this.redirectUri = redirectUri;
        this.scope = scope;
    }

    public String getAuthorizationUrl(String state) {
        if (authUrl == null || authUrl.isBlank() || clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Nolio integration not configured. Set NOLIO_AUTH_URL, NOLIO_TOKEN_URL, "
                            + "NOLIO_CLIENT_ID and NOLIO_CLIENT_SECRET on the backend.");
        }
        return UriComponentsBuilder.fromUriString(authUrl)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public NolioTokenResponse exchangeCodeForToken(String code) {
        return requestToken(form("authorization_code", body -> {
            body.add("code", code);
            body.add("redirect_uri", redirectUri);
        }));
    }

    public NolioTokenResponse refresh(String refreshToken) {
        return requestToken(form("refresh_token",
                body -> body.add("refresh_token", refreshToken)));
    }

    /** Returns a valid access token for the user, refreshing if needed. */
    public String ensureValidToken(User user) {
        long now = Instant.now().getEpochSecond();
        if (user.getNolioTokenExpiresAt() != null
                && user.getNolioTokenExpiresAt() > now + TOKEN_EXPIRY_BUFFER_SECONDS
                && user.getNolioAccessToken() != null) {
            return user.getNolioAccessToken();
        }

        if (user.getNolioRefreshToken() == null) {
            throw new NolioAuthException("No Nolio refresh token stored for user " + user.getId());
        }

        log.info("Refreshing Nolio token for user {}", user.getId());
        NolioTokenResponse refreshed = refresh(user.getNolioRefreshToken());
        applyTokens(user, refreshed);
        userRepository.save(user);
        return refreshed.accessToken();
    }

    public void applyTokens(User user, NolioTokenResponse tokens) {
        user.setNolioAccessToken(tokens.accessToken());
        if (tokens.refreshToken() != null) {
            user.setNolioRefreshToken(tokens.refreshToken());
        }
        if (tokens.expiresAt() != null) {
            user.setNolioTokenExpiresAt(tokens.expiresAt());
        }
        if (tokens.userId() != null) {
            user.setNolioUserId(tokens.userId());
        }
    }

    @SuppressWarnings("unchecked")
    private NolioTokenResponse requestToken(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenUrl, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> payload = response.getBody();
        if (payload == null) {
            throw new NolioAuthException("Empty response from Nolio token endpoint");
        }

        String accessToken = (String) payload.get("access_token");
        String refreshToken = (String) payload.get("refresh_token");
        Number expiresIn = (Number) payload.get("expires_in");
        String userId = (String) payload.getOrDefault("user_id", payload.get("sub"));

        Long expiresAt = expiresIn != null
                ? Instant.now().getEpochSecond() + expiresIn.longValue()
                : null;

        return new NolioTokenResponse(accessToken, refreshToken, expiresAt, userId);
    }

    private MultiValueMap<String, String> form(String grantType, java.util.function.Consumer<MultiValueMap<String, String>> extra) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", grantType);
        extra.accept(body);
        return body;
    }

    public record NolioTokenResponse(String accessToken, String refreshToken, Long expiresAt, String userId) {}

    public static class NolioAuthException extends RuntimeException {
        public NolioAuthException(String message) { super(message); }
    }
}

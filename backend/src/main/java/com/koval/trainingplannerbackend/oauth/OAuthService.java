package com.koval.trainingplannerbackend.oauth;

import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.auth.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OAuthService {

    private final OAuthClientRepository clientRepository;
    private final AuthorizationCodeRepository codeRepository;
    private final UserService userService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${oauth.code-ttl:300}")
    private int codeTtlSeconds;

    public OAuthService(OAuthClientRepository clientRepository,
                        AuthorizationCodeRepository codeRepository,
                        UserService userService) {
        this.clientRepository = clientRepository;
        this.codeRepository = codeRepository;
        this.userService = userService;
    }

    // --- Client Registration ---

    public record ClientRegistrationResult(String clientId, String clientSecret, String clientName, List<String> redirectUris) {}

    public ClientRegistrationResult registerClient(String clientName, List<String> redirectUris) {
        String clientId = UUID.randomUUID().toString();
        String clientSecret = generateRandomHex(40);

        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientSecretHash(sha256(clientSecret));
        client.setClientName(clientName);
        client.setRedirectUris(redirectUris);
        client.setCreatedAt(Instant.now());
        clientRepository.save(client);

        return new ClientRegistrationResult(clientId, clientSecret, clientName, redirectUris);
    }

    // --- Authorization ---

    public String authorize(String clientId, String redirectUri, String codeChallenge, String codeChallengeMethod, String userId) {
        OAuthClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown client_id"));

        if (!client.getRedirectUris().contains(redirectUri)) {
            throw new IllegalArgumentException("Invalid redirect_uri");
        }

        if (client.getUserId() == null) {
            client.setUserId(userId);
            clientRepository.save(client);
        }

        String code = generateRandomHex(40);

        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setCodeHash(sha256(code));
        authCode.setClientId(clientId);
        authCode.setUserId(userId);
        authCode.setRedirectUri(redirectUri);
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod(codeChallengeMethod);
        authCode.setExpiresAt(Instant.now().plusSeconds(codeTtlSeconds));
        authCode.setUsed(false);
        codeRepository.save(authCode);

        return code;
    }

    // --- Token Exchange ---

    public record TokenResult(String accessToken, String tokenType, long expiresIn) {}

    public TokenResult exchangeCode(String code, String redirectUri, String clientId, String clientSecret, String codeVerifier) {
        OAuthClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown client_id"));

        if (!client.getClientSecretHash().equals(sha256(clientSecret))) {
            throw new IllegalArgumentException("Invalid client_secret");
        }

        AuthorizationCode authCode = codeRepository.findByCodeHash(sha256(code))
                .orElseThrow(() -> new IllegalArgumentException("Invalid authorization code"));

        if (authCode.isUsed()) {
            throw new IllegalArgumentException("Authorization code already used");
        }

        if (authCode.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Authorization code expired");
        }

        if (!authCode.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Client mismatch");
        }

        if (!authCode.getRedirectUri().equals(redirectUri)) {
            throw new IllegalArgumentException("Redirect URI mismatch");
        }

        if (authCode.getCodeChallenge() != null && codeVerifier != null) {
            String computedChallenge = base64UrlEncode(sha256Bytes(codeVerifier));
            if (!computedChallenge.equals(authCode.getCodeChallenge())) {
                throw new IllegalArgumentException("PKCE verification failed");
            }
        }

        authCode.setUsed(true);
        codeRepository.save(authCode);

        client.setLastUsedAt(Instant.now());
        clientRepository.save(client);

        User user = userService.getUserById(authCode.getUserId());
        String jwt = generateJwt(user);

        return new TokenResult(jwt, "Bearer", jwtExpiration / 1000);
    }

    // --- Client Management ---

    public record ClientSummary(String id, String clientName, String clientIdPrefix, Instant createdAt, Instant lastUsedAt) {}

    public List<ClientSummary> listClients(String userId) {
        return clientRepository.findByUserId(userId).stream()
                .map(c -> new ClientSummary(
                        c.getId(),
                        c.getClientName(),
                        c.getClientId().substring(0, Math.min(8, c.getClientId().length())),
                        c.getCreatedAt(),
                        c.getLastUsedAt()))
                .toList();
    }

    public void deleteClient(String id, String userId) {
        clientRepository.deleteByIdAndUserId(id, userId);
    }

    // --- Internal helpers ---

    private String generateJwt(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(user.getId())
                .claim("role", user.getRole().name())
                .claim("name", user.getDisplayName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key)
                .compact();
    }

    static String sha256(String input) {
        return HexFormat.of().formatHex(sha256Bytes(input));
    }

    private static byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateRandomHex(int length) {
        byte[] bytes = new byte[length / 2];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}

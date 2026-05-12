package com.koval.trainingplannerbackend.oauth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OAuthController {

    private final OAuthService oAuthService;

    @Value("${oauth.issuer:http://localhost:8080}")
    private String issuer;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public OAuthController(OAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /**
     * OAuth 2.0 Protected Resource Metadata (RFC 9728 / MCP OAuth 2.1).
     * Tells MCP clients which authorization server protects this resource.
     * Must be public (no auth required).
     */
    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**"})
    public Map<String, Object> protectedResourceMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resource", issuer);
        meta.put("authorization_servers", List.of(issuer));
        meta.put("scopes_supported", List.of("mcp"));
        meta.put("bearer_methods_supported", List.of("header"));
        return meta;
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> metadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("issuer", issuer);
        meta.put("authorization_endpoint", issuer + "/oauth/authorize");
        meta.put("token_endpoint", issuer + "/oauth/token");
        meta.put("registration_endpoint", issuer + "/oauth/register");
        meta.put("response_types_supported", List.of("code"));
        meta.put("grant_types_supported", List.of("authorization_code"));
        meta.put("code_challenge_methods_supported", List.of("S256"));
        meta.put("token_endpoint_auth_methods_supported", List.of("client_secret_post"));
        meta.put("scopes_supported", List.of("mcp"));
        return meta;
    }

    @PostMapping("/oauth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        Object redirectUrisObj = request.get("redirect_uris");
        if (!(redirectUrisObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_redirect_uri",
                    "error_description", "redirect_uris is required"));
        }
        List<String> redirectUris = rawList.stream().map(String::valueOf).toList();

        String clientName = request.get("client_name") instanceof String s && !s.isBlank() ? s : "MCP Client";

        var result = oAuthService.registerClient(clientName, redirectUris);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("client_id", result.clientId());
        response.put("client_secret", result.clientSecret());
        response.put("client_id_issued_at", java.time.Instant.now().getEpochSecond());
        response.put("client_secret_expires_at", 0);
        response.put("client_name", result.clientName());
        response.put("redirect_uris", result.redirectUris());
        response.put("grant_types", List.of("authorization_code"));
        response.put("response_types", List.of("code"));
        response.put("token_endpoint_auth_method", "client_secret_post");
        if (request.get("scope") instanceof String scope && !scope.isBlank()) {
            response.put("scope", scope);
        } else {
            response.put("scope", "mcp");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/oauth/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("response_type") String responseType,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest request) {

        if (!"code".equals(responseType)) {
            return ResponseEntity.badRequest().build();
        }

        String jwt = token;
        if (jwt == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        if (jwt == null) {
            String authorizeUrl = issuer + "/oauth/authorize"
                    + "?client_id=" + encode(clientId)
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&response_type=code"
                    + (codeChallenge != null ? "&code_challenge=" + encode(codeChallenge) : "")
                    + (codeChallengeMethod != null ? "&code_challenge_method=" + encode(codeChallengeMethod) : "")
                    + (state != null ? "&state=" + encode(state) : "");

            String frontendLoginUrl = issuer.replace("api.", "").replace(":8080", ":4200")
                    + "/login?returnTo=" + encode(authorizeUrl);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendLoginUrl)
                    .build();
        }

        String userId;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
            userId = claims.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String code = oAuthService.authorize(clientId, redirectUri, codeChallenge, codeChallengeMethod, userId);

            String location = redirectUri + "?code=" + encode(code)
                    + (state != null ? "&state=" + encode(state) : "");

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", location)
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/oauth/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier) {

        if (!"authorization_code".equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }

        try {
            var result = oAuthService.exchangeCode(code, redirectUri, clientId, clientSecret, codeVerifier);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("access_token", result.accessToken());
            response.put("token_type", result.tokenType());
            response.put("expires_in", result.expiresIn());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant", "error_description", e.getMessage()));
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

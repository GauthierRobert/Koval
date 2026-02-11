package com.koval.trainingplannerbackend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Authentication (Strava OAuth2).
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final StravaOAuthService stravaOAuthService;
    private final UserService userService;

    @Value("${jwt.secret:your-256-bit-secret-key-here-must-be-at-least-32-chars}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private long jwtExpiration;

    public AuthController(StravaOAuthService stravaOAuthService, UserService userService) {
        this.stravaOAuthService = stravaOAuthService;
        this.userService = userService;
    }

    /**
     * Redirect to Strava authorization page.
     */
    @GetMapping("/strava")
    public ResponseEntity<Map<String, String>> getStravaAuthUrl() {
        Map<String, String> response = new HashMap<>();
        response.put("authUrl", stravaOAuthService.getAuthorizationUrl());
        return ResponseEntity.ok(response);
    }

    /**
     * Handle Strava OAuth callback.
     */
    @GetMapping("/strava/callback")
    public ResponseEntity<Map<String, Object>> handleStravaCallback(@RequestParam String code) {
        try {
            // Exchange code for tokens
            StravaOAuthService.StravaTokenResponse tokenResponse = stravaOAuthService.exchangeCodeForToken(code);

            // Find or create user
            User user = userService.findOrCreateFromStrava(
                    tokenResponse.getAthleteId(),
                    tokenResponse.getDisplayName(),
                    tokenResponse.getProfilePicture(),
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresAt());

            // Generate JWT
            String jwt = generateJwtToken(user);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("user", userToMap(user));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /**
     * Get current user info from JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String token = authHeader.substring(7);
            String userId = parseJwtToken(token);

            User user = userService.getUserById(userId);
            return ResponseEntity.ok(userToMap(user));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * DTO for role update request.
     */
    public static class RoleRequest {
        private UserRole role;

        public UserRole getRole() {
            return role;
        }

        public void setRole(UserRole role) {
            this.role = role;
        }
    }

    /**
     * Update user's role (ATHLETE or COACH).
     */
    @PostMapping("/role")
    public ResponseEntity<Map<String, Object>> setRole(
            @RequestBody RoleRequest request,
            @RequestHeader(value = "Authorization") String authHeader) {

        try {
            String token = authHeader.substring(7);
            String userId = parseJwtToken(token);

            User user = userService.setRole(userId, request.getRole());
            return ResponseEntity.ok(userToMap(user));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private String generateJwtToken(User user) {
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

    private String parseJwtToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("displayName", user.getDisplayName());
        map.put("profilePicture", user.getProfilePicture());
        map.put("role", user.getRole().name());
        map.put("ftp", user.getFtp());
        map.put("hasCoach", user.hasCoach());
        if (user.isCoach()) {
            map.put("athleteCount", user.getAthleteIds().size());
        }
        return map;
    }
}

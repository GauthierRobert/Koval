package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.training.tag.Tag;
import com.koval.trainingplannerbackend.training.tag.TagService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final StravaOAuthService stravaOAuthService;
    private final GoogleOAuthService googleOAuthService;
    private final UserService userService;
    private final TagService tagService;

    @Value("${jwt.secret:your-256-bit-secret-key-here-must-be-at-least-32-chars}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public AuthController(StravaOAuthService stravaOAuthService, GoogleOAuthService googleOAuthService,
                          UserService userService, TagService tagService) {
        this.stravaOAuthService = stravaOAuthService;
        this.googleOAuthService = googleOAuthService;
        this.userService = userService;
        this.tagService = tagService;
    }

    // --- Strava OAuth ---

    @GetMapping("/strava")
    public ResponseEntity<Map<String, String>> getStravaAuthUrl() {
        Map<String, String> response = new HashMap<>();
        response.put("authUrl", stravaOAuthService.getAuthorizationUrl());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/strava/callback")
    public ResponseEntity<Map<String, Object>> handleStravaCallback(@RequestParam String code) {
        try {
            StravaOAuthService.StravaTokenResponse tokenResponse = stravaOAuthService.exchangeCodeForToken(code);

            User user = userService.findOrCreateFromStrava(
                    tokenResponse.getAthleteId(),
                    tokenResponse.getDisplayName(),
                    tokenResponse.getProfilePicture(),
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresAt());

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

    // --- Google OAuth ---

    @GetMapping("/google")
    public ResponseEntity<Map<String, String>> getGoogleAuthUrl() {
        Map<String, String> response = new HashMap<>();
        response.put("authUrl", googleOAuthService.getAuthorizationUrl());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Map<String, Object>> handleGoogleCallback(@RequestParam String code) {
        try {
            GoogleOAuthService.GoogleUserInfo googleUser = googleOAuthService.exchangeCodeAndGetUserInfo(code);

            User user = userService.findOrCreateFromGoogle(
                    googleUser.getGoogleId(),
                    googleUser.getName(),
                    googleUser.getEmail(),
                    googleUser.getPicture());

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

    // --- Current User ---

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

    public record RoleRequest(UserRole role) {}

    @PostMapping("/role")
    public ResponseEntity<Map<String, Object>> setRole(
            @RequestBody RoleRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String userId = parseJwtToken(authHeader.substring(7));
            User user = userService.setRole(userId, request.role());
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

        map.put("hasCoach", tagService.athleteHasCoach(user.getId()));
        List<Tag> userTags = tagService.getTagsForAthlete(user.getId());
        map.put("tags", userTags.stream().map(Tag::getName).toList());

        if (user.isCoach()) {
            List<String> athleteIds = tagService.getAthleteIdsForCoach(user.getId());
            map.put("athleteCount", athleteIds.size());
        }
        return map;
    }
}

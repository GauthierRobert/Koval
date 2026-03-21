package com.koval.trainingplannerbackend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final StravaOAuthService stravaOAuthService;
    private final GoogleOAuthService googleOAuthService;
    private final UserService userService;
    private final UserRepository userRepository;

    @Value("${jwt.secret:your-256-bit-secret-key-here-must-be-at-least-32-chars}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public AuthController(StravaOAuthService stravaOAuthService, GoogleOAuthService googleOAuthService,
            UserService userService, UserRepository userRepository) {
        this.stravaOAuthService = stravaOAuthService;
        this.googleOAuthService = googleOAuthService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    // --- Strava OAuth ---

    @GetMapping("/strava")
    public ResponseEntity<Map<String, String>> getStravaAuthUrl(
            @RequestParam(required = false) String redirectUri) {
        Map<String, String> response = new HashMap<>();
        response.put("authUrl", stravaOAuthService.getAuthorizationUrl(redirectUri));
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
            response.put("user", userService.userToMap(user));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    // --- Google OAuth ---

    @GetMapping("/google")
    public ResponseEntity<Map<String, String>> getGoogleAuthUrl(
            @RequestParam(required = false) String redirectUri) {
        Map<String, String> response = new HashMap<>();
        response.put("authUrl", googleOAuthService.getAuthorizationUrl(redirectUri));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Map<String, Object>> handleGoogleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String redirectUri) {
        try {
            GoogleOAuthService.GoogleUserInfo googleUser = googleOAuthService.exchangeCodeAndGetUserInfo(code, redirectUri);

            User user = userService.findOrCreateFromGoogle(
                    googleUser.getGoogleId(),
                    googleUser.getName(),
                    googleUser.getEmail(),
                    googleUser.getPicture());

            String jwt = generateJwtToken(user);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("user", userService.userToMap(user));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /**
     * Mobile OAuth callback: Google redirects here (HTTPS), we exchange the code
     * and redirect to the mobile deep link with the JWT token.
     */
    @GetMapping("/google/mobile-callback")
    public ResponseEntity<Void> handleGoogleMobileCallback(@RequestParam String code) {
        try {
            String serverCallbackUri = googleOAuthService.getMobileCallbackUri();
            GoogleOAuthService.GoogleUserInfo googleUser =
                    googleOAuthService.exchangeCodeAndGetUserInfo(code, serverCallbackUri);

            User user = userService.findOrCreateFromGoogle(
                    googleUser.getGoogleId(),
                    googleUser.getName(),
                    googleUser.getEmail(),
                    googleUser.getPicture());

            String jwt = generateJwtToken(user);

            String deepLink = "koval://auth/callback?token=" + jwt;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", deepLink)
                    .build();
        } catch (Exception e) {
            String errorLink = "koval://auth/callback?error=" +
                    java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", errorLink)
                    .build();
        }
    }

    // --- DEV ONLY — login with arbitrary userId, no password ---

    public record DevLoginRequest(String userId, String displayName, UserRole role) {
    }

    @PostMapping("/dev/login")
    public ResponseEntity<Map<String, Object>> devLogin(@RequestBody DevLoginRequest request) {
        String userId = request.userId();
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(userId).orElseGet(() -> {
            User newUser = new User();
            newUser.setId(userId);
            newUser.setAuthProvider(AuthProvider.GOOGLE);
            newUser.setDisplayName(request.displayName() != null ? request.displayName() : userId);
            newUser.setRole(request.role() != null ? request.role() : UserRole.ATHLETE);
            newUser.setFtp(250);
            return userRepository.save(newUser);
        });

        String jwt = generateJwtToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("user", userService.userToMap(user));
        return ResponseEntity.ok(response);
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
            return ResponseEntity.ok(userService.userToMap(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    public record RoleRequest(UserRole role) {
    }

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
            return ResponseEntity.ok(userService.userToMap(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    public record SettingsRequest(Integer ftp, Integer weightKg, Integer functionalThresholdPace,
            Integer criticalSwimSpeed, Integer pace5k, Integer pace10k,
            Integer paceHalfMarathon, Integer paceMarathon,
            Integer vo2maxPower, Integer vo2maxPace,
            Map<String, Integer> customZoneReferenceValues) {
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestBody SettingsRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String userId = parseJwtToken(authHeader.substring(7));
            User user = userService.updateSettings(userId,
                    request.ftp(), request.weightKg(), request.functionalThresholdPace(),
                    request.criticalSwimSpeed(), request.pace5k(), request.pace10k(),
                    request.paceHalfMarathon(), request.paceMarathon(),
                    request.vo2maxPower(), request.vo2maxPace(),
                    request.customZoneReferenceValues());
            return ResponseEntity.ok(userService.userToMap(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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

    public record ZoneReferenceRequest(String zoneSystemId, int value) {}

    @PatchMapping("/me/zone-reference")
    public ResponseEntity<Void> setZoneReference(
            @RequestBody ZoneReferenceRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String userId = parseJwtToken(authHeader.substring(7));
            userService.setCustomZoneReference(userId, request.zoneSystemId(), request.value());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public record OnboardingRequest(UserRole role, Integer ftp, Integer weightKg, Integer criticalSwimSpeed,
            Integer functionalThresholdPace) {
    }

    @PostMapping("/onboarding")
    public ResponseEntity<Map<String, Object>> completeOnboarding(
            @RequestBody OnboardingRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String userId = parseJwtToken(authHeader.substring(7));
            User user = userService.getUserById(userId);

            if (request.role() != null) user.setRole(request.role());
            if (request.ftp() != null) user.setFtp(request.ftp());
            if (request.weightKg() != null) user.setWeightKg(request.weightKg());
            if (request.criticalSwimSpeed() != null) user.setCriticalSwimSpeed(request.criticalSwimSpeed());
            if (request.functionalThresholdPace() != null) user.setFunctionalThresholdPace(request.functionalThresholdPace());
            user.setNeedsOnboarding(false);

            userRepository.save(user);

            // Re-issue JWT with potentially updated role
            String jwt = generateJwtToken(user);
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("user", userService.userToMap(user));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

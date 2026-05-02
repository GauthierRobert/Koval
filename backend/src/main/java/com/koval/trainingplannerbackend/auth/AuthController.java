package com.koval.trainingplannerbackend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
public class AuthController {

    private final StravaOAuthService stravaOAuthService;
    private final GoogleOAuthService googleOAuthService;
    private final UserService userService;
    private final AccountLinkingService accountLinkingService;
    private final UserResponseMapper userResponseMapper;
    private final UserRepository userRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public AuthController(StravaOAuthService stravaOAuthService, GoogleOAuthService googleOAuthService,
            UserService userService, AccountLinkingService accountLinkingService,
            UserResponseMapper userResponseMapper, UserRepository userRepository) {
        this.stravaOAuthService = stravaOAuthService;
        this.googleOAuthService = googleOAuthService;
        this.userService = userService;
        this.accountLinkingService = accountLinkingService;
        this.userResponseMapper = userResponseMapper;
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
            StravaTokenResponse tokenResponse = stravaOAuthService.exchangeCodeForToken(code);

            User user = accountLinkingService.findOrCreateFromStrava(
                    tokenResponse.athleteId(),
                    tokenResponse.displayName(),
                    tokenResponse.profilePicture(),
                    tokenResponse.accessToken(),
                    tokenResponse.refreshToken(),
                    tokenResponse.expiresAt(),
                    tokenResponse.email());

            String jwt = generateJwtToken(user);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("user", userResponseMapper.userToMap(user));

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

            User user = accountLinkingService.findOrCreateFromGoogle(
                    googleUser.getGoogleId(),
                    googleUser.getName(),
                    googleUser.getEmail(),
                    googleUser.getPicture());

            String jwt = generateJwtToken(user);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("user", userResponseMapper.userToMap(user));

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

            User user = accountLinkingService.findOrCreateFromGoogle(
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
            newUser.setPower3MinW(350);
            newUser.setPower12MinW(280);
            // CP and W' will be derived on first settings save; seed them so the chart works in dev:
            newUser.setCriticalPower(257);
            newUser.setWPrimeJ(16800);
            return userRepository.save(newUser);
        });

        String jwt = generateJwtToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("user", userResponseMapper.userToMap(user));
        return ResponseEntity.ok(response);
    }

    // --- Current User (authenticated via JwtAuthenticationFilter + SecurityConfig) ---

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    public record RoleRequest(UserRole role) {
    }

    @PostMapping("/role")
    public ResponseEntity<Map<String, Object>> setRole(@RequestBody RoleRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.setRole(userId, request.role());
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    public record SettingsRequest(@Positive Integer ftp, @Positive Integer weightKg, Integer functionalThresholdPace,
            Integer criticalSwimSpeed, Integer pace5k, Integer pace10k,
            Integer paceHalfMarathon, Integer paceMarathon,
            Integer vo2maxPower, Integer vo2maxPace,
            @Positive Integer power3MinW, @Positive Integer power12MinW,
            Map<String, Integer> customZoneReferenceValues,
            String aiPrePrompt, Boolean aiPrePromptEnabled) {
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody @Valid SettingsRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.updateSettings(userId,
                request.ftp(), request.weightKg(), request.functionalThresholdPace(),
                request.criticalSwimSpeed(), request.pace5k(), request.pace10k(),
                request.paceHalfMarathon(), request.paceMarathon(),
                request.vo2maxPower(), request.vo2maxPace(),
                request.power3MinW(), request.power12MinW(),
                request.customZoneReferenceValues(),
                request.aiPrePrompt(), request.aiPrePromptEnabled());
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    // --- Account linking (authenticated user connects an additional provider) ---

    @PostMapping("/link/strava/callback")
    public ResponseEntity<Map<String, Object>> linkStrava(@RequestParam String code) {
        String userId = SecurityUtils.getCurrentUserId();
        StravaTokenResponse tokenResponse = stravaOAuthService.exchangeCodeForToken(code);
        User user = accountLinkingService.linkStrava(userId, tokenResponse.athleteId(),
                tokenResponse.accessToken(), tokenResponse.refreshToken(), tokenResponse.expiresAt());
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @PostMapping("/link/google/callback")
    public ResponseEntity<Map<String, Object>> linkGoogle(
            @RequestParam String code,
            @RequestParam(required = false) String redirectUri) {
        String userId = SecurityUtils.getCurrentUserId();
        GoogleOAuthService.GoogleUserInfo googleUser = googleOAuthService.exchangeCodeAndGetUserInfo(code, redirectUri);
        User user = accountLinkingService.linkGoogle(userId, googleUser.getGoogleId(), googleUser.getEmail());
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @DeleteMapping("/link/garmin")
    public ResponseEntity<Map<String, Object>> unlinkGarmin() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = accountLinkingService.unlinkGarmin(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @DeleteMapping("/link/zwift")
    public ResponseEntity<Map<String, Object>> unlinkZwift() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = accountLinkingService.unlinkZwift(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @DeleteMapping("/link/nolio-read")
    public ResponseEntity<Map<String, Object>> unlinkNolioRead() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = accountLinkingService.unlinkNolioRead(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @DeleteMapping("/link/nolio-write")
    public ResponseEntity<Map<String, Object>> unlinkNolioWrite() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = accountLinkingService.unlinkNolioWrite(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    // --- Unlinking ---

    @DeleteMapping("/link/strava")
    public ResponseEntity<Map<String, Object>> unlinkStrava() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = accountLinkingService.unlinkStrava(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @DeleteMapping("/link/google")
    public ResponseEntity<Map<String, Object>> unlinkGoogle() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = accountLinkingService.unlinkGoogle(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
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

    public record ZoneReferenceRequest(String zoneSystemId, int value) {}

    @PatchMapping("/me/zone-reference")
    public ResponseEntity<Void> setZoneReference(@RequestBody ZoneReferenceRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        userService.setCustomZoneReference(userId, request.zoneSystemId(), request.value());
        return ResponseEntity.ok().build();
    }

    public record OnboardingRequest(UserRole role, @Positive Integer ftp, @Positive Integer weightKg,
            Integer criticalSwimSpeed, Integer functionalThresholdPace, Boolean cguAccepted) {
    }

    @PostMapping("/onboarding")
    public ResponseEntity<Map<String, Object>> completeOnboarding(@RequestBody @Valid OnboardingRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.completeOnboarding(userId, request.role(), request.ftp(), request.weightKg(),
                request.criticalSwimSpeed(), request.functionalThresholdPace(), request.cguAccepted());

        // Re-issue JWT with potentially updated role
        String jwt = generateJwtToken(user);
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("user", userResponseMapper.userToMap(user));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cgu/accept")
    public ResponseEntity<Map<String, Object>> acceptCgu() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.acceptCgu(userId);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }
}

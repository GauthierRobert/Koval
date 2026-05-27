package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.auth.AccountLinkingService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints exposed to the frontend and to Polar's webhook system.
 * <ul>
 *   <li>{@code GET  /api/integration/polar/auth} — returns the Polar authorization URL.</li>
 *   <li>{@code POST /api/integration/polar/callback?code=...} — completes the OAuth flow.</li>
 *   <li>{@code POST /api/integration/polar/import-history} — pulls new completed exercises.</li>
 *   <li>{@code POST /api/integration/polar/webhook} — Polar AccessLink webhook receiver.</li>
 * </ul>
 *
 * <p>There is intentionally no workout-push endpoint: the Polar AccessLink API is read-only for
 * training data and exposes no way to create planned workouts (training targets). Polar is an
 * import-only integration.
 */
@RestController
@RequestMapping("/api/integration/polar")
public class PolarIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(PolarIntegrationController.class);

    private final PolarOAuthService oauthService;
    private final PolarApiClient apiClient;
    private final PolarActivitySyncService syncService;
    private final AccountLinkingService accountLinkingService;
    private final UserRepository userRepository;
    private final UserResponseMapper userResponseMapper;

    public PolarIntegrationController(PolarOAuthService oauthService,
                                       PolarApiClient apiClient,
                                       PolarActivitySyncService syncService,
                                       AccountLinkingService accountLinkingService,
                                       UserRepository userRepository,
                                       UserResponseMapper userResponseMapper) {
        this.oauthService = oauthService;
        this.apiClient = apiClient;
        this.syncService = syncService;
        this.accountLinkingService = accountLinkingService;
        this.userRepository = userRepository;
        this.userResponseMapper = userResponseMapper;
    }

    @GetMapping("/auth")
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        if (!oauthService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Polar integration is not configured"));
        }
        String userId = SecurityUtils.getCurrentUserId();
        // Plain userId state — the frontend resends it to the backend on callback, so we don't
        // strictly need server-side state storage. Keep simple to mirror Garmin's flow.
        String authUrl = oauthService.getAuthorizationUrl(userId);
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestParam("code") String code) {
        String userId = SecurityUtils.getCurrentUserId();
        PolarOAuthService.PolarTokenResponse tokens = oauthService.exchangeCodeForToken(code);

        // Register the user in Polar AccessLink (idempotent — Polar returns 409 on re-register).
        apiClient.registerUser(tokens.accessToken(), userId);

        User user = accountLinkingService.linkPolar(userId, tokens.polarUserId(),
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt());
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @PostMapping("/import-history")
    public PolarActivitySyncService.SyncResult importHistory() {
        return syncService.importNewExercises(SecurityUtils.getCurrentUserId());
    }

    /**
     * Polar AccessLink webhook. Polar POSTs an event payload like:
     * {@code {"event":"EXERCISE","user_id":12345,"entity_id":"...","url":"..."} }
     * We fan it out to the sync service for that user.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Polar webhook payload: {}", payload);
        Object eventType = payload.get("event");
        if (!"EXERCISE".equalsIgnoreCase(String.valueOf(eventType))) {
            // PING events and others — ack only.
            return ResponseEntity.ok().build();
        }
        Object polarUserId = payload.get("user_id");
        if (polarUserId == null) return ResponseEntity.ok().build();

        userRepository.findByPolarUserId(String.valueOf(polarUserId))
                .ifPresentOrElse(
                        u -> {
                            try {
                                syncService.importNewExercises(u.getId());
                            } catch (RuntimeException e) {
                                log.warn("Polar webhook import failed for user {}: {}", u.getId(), e.getMessage());
                            }
                        },
                        () -> log.warn("Polar webhook for unknown polarUserId={}", polarUserId));
        return ResponseEntity.ok().build();
    }
}

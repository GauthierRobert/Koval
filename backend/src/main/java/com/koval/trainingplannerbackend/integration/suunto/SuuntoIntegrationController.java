package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.auth.AccountLinkingService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserResponseMapper;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints exposed to the frontend for the Suunto integration.
 * <ul>
 *   <li>{@code GET  /api/integration/suunto/auth} — returns the Suunto authorization URL.</li>
 *   <li>{@code POST /api/integration/suunto/callback?code=...} — completes the OAuth flow
 *       when the user links Suunto to an existing Koval account.</li>
 *   <li>{@code POST /api/integration/suunto/import-history} — backfills recent workouts.</li>
 *   <li>{@code PUT  /api/integration/suunto/auto-push} — toggles auto-push of scheduled
 *       workouts (delivered as SuuntoPlus guides).</li>
 * </ul>
 *
 * <p>This mirrors the Polar integration controller. Webhook-driven imports live in
 * {@link SuuntoWebhookController}.
 */
@RestController
@RequestMapping("/api/integration/suunto")
public class SuuntoIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(SuuntoIntegrationController.class);

    private final SuuntoOAuthService oauthService;
    private final SuuntoActivitySyncService syncService;
    private final AccountLinkingService accountLinkingService;
    private final UserRepository userRepository;
    private final UserResponseMapper userResponseMapper;

    public SuuntoIntegrationController(SuuntoOAuthService oauthService,
                                        SuuntoActivitySyncService syncService,
                                        AccountLinkingService accountLinkingService,
                                        UserRepository userRepository,
                                        UserResponseMapper userResponseMapper) {
        this.oauthService = oauthService;
        this.syncService = syncService;
        this.accountLinkingService = accountLinkingService;
        this.userRepository = userRepository;
        this.userResponseMapper = userResponseMapper;
    }

    @GetMapping("/auth")
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        if (!oauthService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Suunto integration is not configured"));
        }
        String userId = SecurityUtils.getCurrentUserId();
        String authUrl = oauthService.getAuthorizationUrl(userId);
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestParam("code") String code) {
        String userId = SecurityUtils.getCurrentUserId();
        SuuntoOAuthService.SuuntoTokenResponse tokens = oauthService.exchangeCodeForToken(code);
        if (tokens.suuntoUserId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Suunto did not return a user id"));
        }
        User user = accountLinkingService.linkSuunto(userId, tokens.suuntoUserId(),
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt());
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    /** Backfill recent Suunto workouts. Idempotent — already-imported workouts are skipped. */
    @PostMapping("/import-history")
    public ResponseEntity<SuuntoActivitySyncService.SyncResult> importHistory() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(syncService.importHistory(userId));
    }

    /** Toggle auto-push of scheduled workouts to Suunto. */
    @PutMapping("/auto-push")
    public ResponseEntity<Map<String, Object>> setAutoPush(@RequestBody Map<String, Boolean> body) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        if (enabled && user.getSuuntoUserId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Suunto is not connected for this user"));
        }
        user.setSuuntoAutoPushWorkouts(enabled);
        userRepository.save(user);
        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }
}

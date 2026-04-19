package com.koval.trainingplannerbackend.integration.nolio.write;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints for the Nolio write path: direct OAuth flow + workout push +
 * the user-level auto-sync toggle.
 */
@RestController
@RequestMapping("/api/integration/nolio")
public class NolioIntegrationController {

    private final NolioOAuthService oauthService;
    private final NolioPushService pushService;
    private final UserService userService;
    private final UserRepository userRepository;

    public NolioIntegrationController(NolioOAuthService oauthService,
                                      NolioPushService pushService,
                                      UserService userService,
                                      UserRepository userRepository) {
        this.oauthService = oauthService;
        this.pushService = pushService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Map<String, String>> authorize() {
        String state = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(Map.of("authUrl", oauthService.getAuthorizationUrl(state)));
    }

    /**
     * OAuth redirect target. {@code state} carries our userId (set in /authorize).
     * This endpoint is unauthenticated - see SecurityConfig.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestParam String code,
                                                        @RequestParam String state) {
        User user = userService.getUserById(state);
        NolioOAuthService.NolioTokenResponse tokens = oauthService.exchangeCodeForToken(code);
        oauthService.applyTokens(user, tokens);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "connected", true,
                "nolioUserId", user.getNolioUserId() == null ? "" : user.getNolioUserId()
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        User user = userService.getUserById(SecurityUtils.getCurrentUserId());
        user.setNolioAccessToken(null);
        user.setNolioRefreshToken(null);
        user.setNolioTokenExpiresAt(null);
        user.setNolioUserId(null);
        user.setNolioAutoSyncWorkouts(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workouts/{trainingId}/push")
    public Training pushWorkout(@PathVariable String trainingId) {
        return pushService.push(SecurityUtils.getCurrentUserId(), trainingId);
    }

    @PatchMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        User user = userService.getUserById(SecurityUtils.getCurrentUserId());
        if (body.get("autoSyncWorkouts") instanceof Boolean flag) {
            user.setNolioAutoSyncWorkouts(flag);
            userRepository.save(user);
        }
        return ResponseEntity.ok(Map.of("nolioAutoSyncWorkouts", Boolean.TRUE.equals(user.getNolioAutoSyncWorkouts())));
    }
}

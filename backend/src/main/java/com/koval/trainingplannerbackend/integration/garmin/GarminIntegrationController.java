package com.koval.trainingplannerbackend.integration.garmin;

import com.koval.trainingplannerbackend.auth.AccountLinkingService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserResponseMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/integration/garmin")
public class GarminIntegrationController {

    private final GarminOAuthService oauthService;
    private final GarminActivitySyncService syncService;
    private final AccountLinkingService accountLinkingService;
    private final UserResponseMapper userResponseMapper;

    // Temporary store for request token secrets during OAuth flow
    private final Map<String, String> pendingTokenSecrets = new ConcurrentHashMap<>();

    public GarminIntegrationController(GarminOAuthService oauthService,
                                        GarminActivitySyncService syncService,
                                        AccountLinkingService accountLinkingService,
                                        UserResponseMapper userResponseMapper) {
        this.oauthService = oauthService;
        this.syncService = syncService;
        this.accountLinkingService = accountLinkingService;
        this.userResponseMapper = userResponseMapper;
    }

    /**
     * Step 1: Start OAuth flow — returns the authorization URL.
     */
    @GetMapping("/auth")
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        if (!oauthService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Garmin integration is not configured"));
        }
        Map<String, String> requestToken = oauthService.getRequestToken();
        String oauthToken = requestToken.get("oauth_token");
        String oauthTokenSecret = requestToken.get("oauth_token_secret");

        // Store secret for callback
        pendingTokenSecrets.put(oauthToken, oauthTokenSecret);

        String authUrl = oauthService.getAuthorizationUrl(oauthToken);
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    /**
     * Step 2: OAuth callback — exchange tokens and link account.
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestParam("oauth_token") String oauthToken,
            @RequestParam("oauth_verifier") String verifier) {
        String userId = SecurityUtils.getCurrentUserId();
        String tokenSecret = pendingTokenSecrets.remove(oauthToken);
        if (tokenSecret == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OAuth token"));
        }

        GarminOAuthService.GarminAccessToken accessToken =
                oauthService.exchangeForAccessToken(oauthToken, tokenSecret, verifier);

        User user = accountLinkingService.linkGarmin(userId, accessToken.userId(),
                accessToken.accessToken(), accessToken.accessTokenSecret());

        return ResponseEntity.ok(userResponseMapper.userToMap(user));
    }

    @PostMapping("/import-history")
    public GarminActivitySyncService.SyncResult importHistory() {
        return syncService.importHistory(SecurityUtils.getCurrentUserId());
    }
}

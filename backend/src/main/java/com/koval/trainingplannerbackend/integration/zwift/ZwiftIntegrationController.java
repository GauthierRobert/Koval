package com.koval.trainingplannerbackend.integration.zwift;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/integration/zwift")
@CrossOrigin(origins = "*")
public class ZwiftIntegrationController {

    private final ZwiftAuthService authService;
    private final ZwiftActivitySyncService syncService;
    private final UserService userService;

    public ZwiftIntegrationController(ZwiftAuthService authService,
                                       ZwiftActivitySyncService syncService,
                                       UserService userService) {
        this.authService = authService;
        this.syncService = syncService;
        this.userService = userService;
    }

    /**
     * Connect Zwift using username/password (no OAuth redirect flow).
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody ZwiftLoginRequest request) {
        if (!authService.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Zwift integration is not enabled"));
        }

        String userId = SecurityUtils.getCurrentUserId();

        try {
            ZwiftAuthService.ZwiftTokenResponse tokenResponse =
                    authService.authenticate(request.username(), request.password());

            User user = userService.linkZwift(userId, tokenResponse.zwiftUserId(),
                    tokenResponse.accessToken(), tokenResponse.refreshToken());

            return ResponseEntity.ok(userService.userToMap(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to connect Zwift: " + e.getMessage()));
        }
    }

    @PostMapping("/import-history")
    public ZwiftActivitySyncService.SyncResult importHistory() {
        return syncService.importHistory(SecurityUtils.getCurrentUserId());
    }

    public record ZwiftLoginRequest(String username, String password) {}
}

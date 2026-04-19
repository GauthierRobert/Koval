package com.koval.trainingplannerbackend.integration.terra;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints the frontend uses to start / stop a Terra-backed provider connection.
 * Currently only Nolio routes through Terra — other providers keep their own flows.
 */
@RestController
@RequestMapping("/api/integration/terra")
public class TerraIntegrationController {

    private final TerraWidgetService widgetService;
    private final TerraApiClient terraApiClient;
    private final UserService userService;
    private final UserRepository userRepository;

    public TerraIntegrationController(TerraWidgetService widgetService,
                                      TerraApiClient terraApiClient,
                                      UserService userService,
                                      UserRepository userRepository) {
        this.widgetService = widgetService;
        this.terraApiClient = terraApiClient;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /** Returns a widget URL the frontend opens so the user can connect Nolio via Terra. */
    @PostMapping("/nolio/connect")
    public ResponseEntity<Map<String, Object>> connectNolio() {
        String userId = SecurityUtils.getCurrentUserId();
        TerraApiClient.WidgetSession session = widgetService.generateNolioSession(userId);
        return ResponseEntity.ok(Map.of(
                "widgetUrl", session.url(),
                "sessionId", session.sessionId(),
                "expiresInSeconds", session.expiresInSeconds()
        ));
    }

    /** Revokes the user's Nolio-via-Terra connection and clears local state. */
    @DeleteMapping("/nolio")
    public ResponseEntity<Void> disconnectNolio() {
        User user = userService.getUserById(SecurityUtils.getCurrentUserId());
        if (user.getTerraUserId() != null) {
            terraApiClient.deauthenticateUser(user.getTerraUserId());
        }
        user.setTerraUserId(null);
        user.setTerraProviderNolioConnected(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }
}

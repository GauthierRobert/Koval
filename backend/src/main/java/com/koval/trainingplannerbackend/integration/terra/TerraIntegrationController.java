package com.koval.trainingplannerbackend.integration.terra;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
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

    private final TerraIntegrationService integrationService;

    public TerraIntegrationController(TerraIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    /** Returns a widget URL the frontend opens so the user can connect Nolio via Terra. */
    @PostMapping("/nolio/connect")
    public ResponseEntity<Map<String, Object>> connectNolio() {
        String userId = SecurityUtils.getCurrentUserId();
        TerraApiClient.WidgetSession session = integrationService.startNolioConnect(userId);
        return ResponseEntity.ok(Map.of(
                "widgetUrl", session.url(),
                "sessionId", session.sessionId(),
                "expiresInSeconds", session.expiresInSeconds()
        ));
    }

    /** Revokes the user's Nolio-via-Terra connection and clears local state. */
    @DeleteMapping("/nolio")
    public ResponseEntity<Void> disconnectNolio() {
        integrationService.disconnectNolio(SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}

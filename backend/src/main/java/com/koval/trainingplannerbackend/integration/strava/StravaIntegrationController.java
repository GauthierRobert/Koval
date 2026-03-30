package com.koval.trainingplannerbackend.integration.strava;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/strava")
@CrossOrigin(origins = "*")
public class StravaIntegrationController {

    private final StravaActivitySyncService syncService;

    public StravaIntegrationController(StravaActivitySyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/import-history")
    public StravaActivitySyncService.SyncResult importHistory() {
        return syncService.importHistory(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/status")
    public StravaActivitySyncService.SyncStatus status() {
        return syncService.getSyncStatus(SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/sessions/{sessionId}/build-fit")
    public CompletedSession buildFit(@PathVariable String sessionId) {
        return syncService.buildFitForSession(sessionId, SecurityUtils.getCurrentUserId());
    }
}

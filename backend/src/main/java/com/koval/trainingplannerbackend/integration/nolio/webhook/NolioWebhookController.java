package com.koval.trainingplannerbackend.integration.nolio.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Receivers for Nolio's webhook channels. Two URLs are declared in the Nolio
 * app admin: {@code webhook_event_real_url} → /real (achieved Training/Note/
 * Competition) and {@code webhook_event_planned_url} → /planned (TrainingPlanned
 * et al.). Requests are authenticated by the static {@code X-Nolio-Key} header.
 * Both endpoints ack immediately and process asynchronously — Nolio only needs
 * the 200. Unauthenticated in SecurityConfig (key-checked here instead).
 */
@RestController
@RequestMapping("/api/integration/nolio/webhook")
public class NolioWebhookController {

    private static final Logger log = LoggerFactory.getLogger(NolioWebhookController.class);

    private final NolioSessionSyncService sessionSyncService;
    private final NolioPlannedSyncService plannedSyncService;
    private final String webhookKey;

    public NolioWebhookController(NolioSessionSyncService sessionSyncService,
                                  NolioPlannedSyncService plannedSyncService,
                                  @Value("${nolio.webhook-key:}") String webhookKey) {
        this.sessionSyncService = sessionSyncService;
        this.plannedSyncService = plannedSyncService;
        this.webhookKey = webhookKey;
    }

    @PostMapping("/real")
    public ResponseEntity<Void> realEvent(@RequestHeader(value = "X-Nolio-Key", required = false) String key,
                                          @RequestBody NolioWebhookPayload payload) {
        if (!keyMatches(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (payload.isTestDelivery()) {
            log.info("Nolio real-channel test delivery received and ignored");
            return ResponseEntity.ok().build();
        }
        sessionSyncService.handleAsync(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/planned")
    public ResponseEntity<Void> plannedEvent(@RequestHeader(value = "X-Nolio-Key", required = false) String key,
                                             @RequestBody NolioWebhookPayload payload) {
        if (!keyMatches(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (payload.isTestDelivery()) {
            log.info("Nolio planned-channel test delivery received and ignored");
            return ResponseEntity.ok().build();
        }
        plannedSyncService.handleAsync(payload);
        return ResponseEntity.ok().build();
    }

    /** Constant-time compare; an unconfigured key rejects everything rather than accepting everything. */
    private boolean keyMatches(String received) {
        if (webhookKey == null || webhookKey.isBlank()) {
            log.warn("Nolio webhook received but NOLIO_WEBHOOK_KEY is not configured — rejecting");
            return false;
        }
        if (received == null) return false;
        return MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8),
                webhookKey.getBytes(StandardCharsets.UTF_8));
    }
}

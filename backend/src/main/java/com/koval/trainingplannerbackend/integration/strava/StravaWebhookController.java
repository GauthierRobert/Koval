package com.koval.trainingplannerbackend.integration.strava;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.integration.strava.StravaWebhookSubscriptionService.StravaSubscriptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Handles Strava webhook subscription validation and event reception.
 * Both endpoints are unauthenticated (permitted in SecurityConfig).
 */
@RestController
@RequestMapping("/api/integration/strava/webhook")
public class StravaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StravaWebhookController.class);

    private final StravaActivitySyncService syncService;
    private final UserService userService;
    private final StravaWebhookSubscriptionService subscriptionService;
    private final String verifyToken;
    private final String adminSecret;

    public StravaWebhookController(StravaActivitySyncService syncService,
                                    UserService userService,
                                    StravaWebhookSubscriptionService subscriptionService,
                                    @Value("${strava.webhook-verify-token:strava-webhook-verify}") String verifyToken,
                                    @Value("${admin.secret}") String adminSecret) {
        this.syncService = syncService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.verifyToken = verifyToken;
        this.adminSecret = adminSecret;
    }

    /**
     * Strava subscription validation callback.
     * Strava sends a GET with hub.mode, hub.verify_token, and hub.challenge.
     * We must echo hub.challenge back if the verify_token matches.
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> validateSubscription(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if (!"subscribe".equals(mode) || !verifyToken.equals(token)) {
            log.warn("Strava webhook validation failed: mode={}, token mismatch={}", mode, !verifyToken.equals(token));
            return ResponseEntity.status(403).build();
        }

        log.info("Strava webhook subscription validated");
        return ResponseEntity.ok(Map.of("hub.challenge", challenge));
    }

    /**
     * Receives Strava webhook events.
     * Must respond with 200 within 2 seconds — processing is done asynchronously.
     */
    @PostMapping
    public ResponseEntity<Void> receiveEvent(@RequestBody Map<String, Object> event) {
        String objectType = (String) event.get("object_type");
        String aspectType = (String) event.get("aspect_type");

        if (!"activity".equals(objectType) || !"create".equals(aspectType)) {
            log.debug("Ignoring Strava webhook event: {}:{}", objectType, aspectType);
            return ResponseEntity.ok().build();
        }

        Number objectId = (Number) event.get("object_id");
        Number ownerId = (Number) event.get("owner_id");

        if (objectId == null || ownerId == null) {
            log.warn("Strava webhook event missing object_id or owner_id");
            return ResponseEntity.ok().build();
        }

        String stravaAthleteId = String.valueOf(ownerId.longValue());
        String stravaActivityId = String.valueOf(objectId.longValue());

        // Process asynchronously to respond within 2 seconds
        Thread.startVirtualThread(() -> {
            try {
                Optional<User> userOpt = userService.findByStravaId(stravaAthleteId);
                if (userOpt.isEmpty()) {
                    log.debug("No user found for Strava athlete {}", stravaAthleteId);
                    return;
                }
                syncService.importSingleActivity(userOpt.get(), stravaActivityId);
                log.info("Imported Strava activity {} for athlete {}", stravaActivityId, stravaAthleteId);
            } catch (RuntimeException e) {
                log.warn("Failed to process Strava webhook for activity {}: {}", stravaActivityId, e.getMessage());
            }
        });

        return ResponseEntity.ok().build();
    }

    /** POST /api/integration/strava/webhook/subscribe — register the webhook with Strava. Call once per environment. */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestHeader("X-Admin-Secret") String secret) {
        if (!adminSecret.equals(secret)) {
            return ResponseEntity.status(403).build();
        }
        try {
            StravaWebhookSubscriptionService.SubscriptionResult result = subscriptionService.createSubscription();
            log.info("Strava webhook subscription registered: id={}", result.id());
            return ResponseEntity.ok(Map.of("id", result.id(), "callbackUrl", result.callbackUrl()));
        } catch (StravaSubscriptionException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /api/integration/strava/webhook/subscribe — view the current subscription. */
    @GetMapping("/subscribe")
    public ResponseEntity<?> viewSubscription(@RequestHeader("X-Admin-Secret") String secret) {
        if (!adminSecret.equals(secret)) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(subscriptionService.viewSubscription());
        } catch (StravaSubscriptionException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

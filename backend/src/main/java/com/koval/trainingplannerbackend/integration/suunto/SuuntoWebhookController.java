package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Receives Suunto workout notifications. The callback URL is configured statically in the
 * Suunto API zone app profile (no subscribe API); Suunto POSTs {@code username} (the Suunto
 * account id we store as {@code suuntoUserId}) and {@code workoutid} for each newly synced
 * workout. Unauthenticated — permitted in SecurityConfig.
 */
@RestController
@RequestMapping("/api/integration/suunto/webhook")
public class SuuntoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SuuntoWebhookController.class);

    private final SuuntoActivitySyncService syncService;
    private final UserRepository userRepository;

    public SuuntoWebhookController(SuuntoActivitySyncService syncService,
                                   UserRepository userRepository) {
        this.syncService = syncService;
        this.userRepository = userRepository;
    }

    /**
     * Acks immediately and imports asynchronously. Accepts the parameters either as query
     * params or as a JSON body — Suunto's docs only guarantee the names, not the transport.
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestParam(value = "username", required = false) String usernameParam,
            @RequestParam(value = "workoutid", required = false) String workoutIdParam,
            @RequestBody(required = false) Map<String, Object> body) {

        String username = firstNonBlank(usernameParam, body, "username");
        String workoutId = firstNonBlank(workoutIdParam, body, "workoutid");

        if (username == null || workoutId == null) {
            log.warn("Suunto webhook missing username or workoutid");
            return ResponseEntity.ok().build();
        }

        Thread.startVirtualThread(() -> {
            try {
                Optional<User> userOpt = userRepository.findBySuuntoUserId(username);
                if (userOpt.isEmpty()) {
                    log.debug("No user found for Suunto username {}", username);
                    return;
                }
                syncService.importSingleWorkout(userOpt.get(), workoutId);
            } catch (RuntimeException e) {
                log.warn("Failed to process Suunto webhook for workout {}: {}", workoutId, e.getMessage());
            }
        });

        return ResponseEntity.ok().build();
    }

    private static String firstNonBlank(String param, Map<String, Object> body, String key) {
        if (param != null && !param.isBlank()) return param;
        if (body != null && body.get(key) != null) {
            String value = String.valueOf(body.get(key));
            if (!value.isBlank()) return value;
        }
        return null;
    }
}

package com.koval.trainingplannerbackend.integration.terra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.integration.nolio.read.NolioActivityIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Receives Terra webhook events (auth, deauth, activity, ...).
 * Signature is verified against the signing secret; processing is dispatched asynchronously
 * so we return 200 quickly.
 */
@RestController
@RequestMapping("/api/webhooks/terra")
public class TerraWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TerraWebhookController.class);

    private final TerraWebhookSignatureVerifier signatureVerifier;
    private final UserRepository userRepository;
    private final NolioActivityIngestService nolioIngestService;
    private final ObjectMapper objectMapper;

    public TerraWebhookController(TerraWebhookSignatureVerifier signatureVerifier,
                                  UserRepository userRepository,
                                  NolioActivityIngestService nolioIngestService,
                                  ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.userRepository = userRepository;
        this.nolioIngestService = nolioIngestService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
                                        @RequestHeader(value = "terra-signature", required = false) String signature) {

        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("Terra webhook signature invalid - rejecting");
            return ResponseEntity.status(403).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Terra webhook body parse failed: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        String type = textOrNull(root, "type");
        if (type == null) {
            return ResponseEntity.ok().build();
        }

        Thread.startVirtualThread(() -> dispatch(type, root));
        return ResponseEntity.ok().build();
    }

    private void dispatch(String type, JsonNode event) {
        try {
            switch (type) {
                case "auth" -> handleAuth(event);
                case "deauth", "access_revoked" -> handleDeauth(event);
                case "activity" -> handleActivity(event);
                default -> log.debug("Ignoring Terra event type '{}'", type);
            }
        } catch (RuntimeException e) {
            log.warn("Terra webhook '{}' processing failed: {}", type, e.getMessage(), e);
        }
    }

    private void handleAuth(JsonNode event) {
        String referenceId = textOrNull(event.path("user"), "reference_id");
        String terraUserId = textOrNull(event.path("user"), "user_id");
        String provider = textOrNull(event.path("user"), "provider");

        if (referenceId == null || terraUserId == null) {
            log.warn("Terra auth event missing reference_id or user_id");
            return;
        }

        Optional<User> userOpt = userRepository.findById(referenceId);
        if (userOpt.isEmpty()) {
            log.warn("Terra auth event references unknown user {}", referenceId);
            return;
        }

        User user = userOpt.get();
        user.setTerraUserId(terraUserId);
        if (TerraWidgetService.PROVIDER_NOLIO.equalsIgnoreCase(provider)) {
            user.setTerraProviderNolioConnected(true);
        }
        userRepository.save(user);
        log.info("Terra auth linked user={} provider={}", user.getId(), provider);
    }

    private void handleDeauth(JsonNode event) {
        String terraUserId = textOrNull(event.path("user"), "user_id");
        if (terraUserId == null) return;

        userRepository.findByTerraUserId(terraUserId).ifPresent(user -> {
            user.setTerraProviderNolioConnected(false);
            user.setTerraUserId(null);
            userRepository.save(user);
            log.info("Terra deauth cleared for user={}", user.getId());
        });
    }

    private void handleActivity(JsonNode event) {
        String terraUserId = textOrNull(event.path("user"), "user_id");
        String provider = textOrNull(event.path("user"), "provider");
        JsonNode data = event.path("data");

        if (terraUserId == null || !data.isArray() || data.isEmpty()) {
            return;
        }

        if (!TerraWidgetService.PROVIDER_NOLIO.equalsIgnoreCase(provider)) {
            log.debug("Ignoring Terra activity from non-Nolio provider '{}'", provider);
            return;
        }

        Optional<User> userOpt = userRepository.findByTerraUserId(terraUserId);
        if (userOpt.isEmpty()) {
            log.warn("Terra activity references unknown terraUserId={}", terraUserId);
            return;
        }

        User user = userOpt.get();
        for (JsonNode activity : data) {
            nolioIngestService.ingest(user, activity);
        }
        user.setNolioLastSyncAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}

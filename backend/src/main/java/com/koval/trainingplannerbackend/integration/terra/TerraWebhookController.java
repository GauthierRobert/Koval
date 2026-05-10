package com.koval.trainingplannerbackend.integration.terra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final TerraWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public TerraWebhookController(TerraWebhookSignatureVerifier signatureVerifier,
                                  TerraWebhookService webhookService,
                                  ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
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

        JsonNode typeNode = root.path("type");
        if (!typeNode.isTextual()) {
            return ResponseEntity.ok().build();
        }

        String type = typeNode.asText();
        Thread.startVirtualThread(() -> webhookService.dispatch(type, root));
        return ResponseEntity.ok().build();
    }
}

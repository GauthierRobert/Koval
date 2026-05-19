package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/action")
public class AIActionController {

    private final AIActionService aiActionService;
    private final AiActionQuotaService quotaService;

    public AIActionController(AIActionService aiActionService, AiActionQuotaService quotaService) {
        this.aiActionService = aiActionService;
        this.quotaService = quotaService;
    }

    public record ActionRequest(String message, AIActionType actionType, AIActionService.ActionContext context) {}

    @PostMapping
    public ResponseEntity<?> executeAction(@RequestBody ActionRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty_message", "message", "Message cannot be empty."));
        }
        if (request.actionType() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_action_type", "message", "actionType is required."));
        }

        String userId = SecurityUtils.getCurrentUserId();

        // Monthly AI workout quota (prod only — gated by config). Throws 429 if exhausted.
        quotaService.checkQuota(userId, request.actionType());

        AIActionService.ActionContext ctx = request.context() != null
                ? request.context()
                : new AIActionService.ActionContext(null, null, null, null, null, null);

        AIActionService.ActionResult result = aiActionService.execute(request.message(), request.actionType(), ctx, userId);
        if (result.success()) {
            quotaService.recordUsage(userId, request.actionType());
        }
        return ResponseEntity.ok(result);
    }
}

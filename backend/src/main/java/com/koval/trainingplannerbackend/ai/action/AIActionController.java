package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/action")
@CrossOrigin(origins = "*")
public class AIActionController {

    private final AIActionService aiActionService;

    public AIActionController(AIActionService aiActionService) {
        this.aiActionService = aiActionService;
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
        AIActionService.ActionContext ctx = request.context() != null
                ? request.context()
                : new AIActionService.ActionContext(null, null, null);

        AIActionService.ActionResult result = aiActionService.execute(request.message(), request.actionType(), ctx, userId);
        return ResponseEntity.ok(result);
    }
}

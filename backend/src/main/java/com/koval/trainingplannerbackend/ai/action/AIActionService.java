package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.ai.UserContextResolver;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Stateless one-shot AI action service.
 * No history, no memory — each call is independent.
 */
@Service
public class AIActionService {

    private final ChatClient actionZoneClient;
    private final ChatClient actionTrainingSessionClient;
    private final ChatClient actionTrainingCreatorClient;
    private final UserContextResolver userContextResolver;
    private final ZoneSystemService zoneSystemService;

    public record ActionContext(String clubId, String clubGroupId, String coachGroupId, String sessionId,
                                String sport, String zoneSystemId) {}
    public record ActionResult(String content, boolean success) {}

    public AIActionService(@Qualifier("actionZoneClient") ChatClient actionZoneClient,
                           @Qualifier("actionTrainingSessionClient") ChatClient actionTrainingSessionClient,
                           @Qualifier("actionTrainingCreatorClient") ChatClient actionTrainingCreatorClient,
                           UserContextResolver userContextResolver,
                           ZoneSystemService zoneSystemService) {
        this.actionZoneClient = actionZoneClient;
        this.actionTrainingSessionClient = actionTrainingSessionClient;
        this.actionTrainingCreatorClient = actionTrainingCreatorClient;
        this.userContextResolver = userContextResolver;
        this.zoneSystemService = zoneSystemService;
    }

    public ActionResult execute(String userMessage, AIActionType actionType, ActionContext context, String userId) {
        UserContext userCtx = userContextResolver.resolve(userId);
        ChatClient client = switch (actionType) {
            case ZONE_CREATION          -> actionZoneClient;
            case TRAINING_WITH_SESSION  -> actionTrainingSessionClient;
            case TRAINING_CREATION -> actionTrainingCreatorClient;
        };

        String systemContext = buildSystemContext(userCtx, context);

        try {
            ActionToolTracker.reset();
            String content = client.prompt()
                    .messages(new SystemMessage(systemContext))
                    .user(userMessage)
                    .call()
                    .content();

            if (ActionToolTracker.wasCalled()) {
                return new ActionResult("done", true);
            }
            // AI didn't call a tool — it's asking for clarification
            return new ActionResult(content != null ? content : "Could not complete the action.", false);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return new ActionResult("Action failed: " + msg, false);
        }
    }

    private String buildSystemContext(UserContext ctx, ActionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("userId = ").append(ctx.userId()).append("\n");
        sb.append("userRole = ").append(ctx.role()).append("\n");
        sb.append("clubId = ").append(context.clubId() != null ? context.clubId() : "null").append("\n");
        sb.append("clubGroupId = ").append(context.clubGroupId() != null ? context.clubGroupId() : "null").append("\n");
        sb.append("coachGroupId = ").append(context.coachGroupId() != null ? context.coachGroupId() : "null").append("\n");
        sb.append("sessionId = ").append(context.sessionId() != null ? context.sessionId() : "null").append("\n");
        sb.append("sport = ").append(context.sport() != null ? context.sport() : "null").append("\n");
        sb.append("zoneSystemId = ").append(context.zoneSystemId() != null ? context.zoneSystemId() : "null");

        // Append zone system details when available
        String zsId = context.zoneSystemId();
        if (zsId != null && !zsId.isBlank() && !"null".equalsIgnoreCase(zsId)) {
            try {
                ZoneSystem zs = zoneSystemService.getZoneSystem(zsId);
                sb.append("\n\nZone System: ").append(zs.getName())
                  .append(" (").append(zs.getReferenceType()).append(")");
                if (zs.getZones() != null) {
                    sb.append("\nZones:");
                    for (var z : zs.getZones()) {
                        sb.append("\n  ").append(z.label()).append(": ")
                          .append(z.low()).append("-").append(z.high()).append("%");
                        if (z.description() != null) sb.append(" (").append(z.description()).append(")");
                    }
                }
                if (zs.getAnnotations() != null && !zs.getAnnotations().isBlank()) {
                    sb.append("\nAnnotations: ").append(zs.getAnnotations());
                }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }
}

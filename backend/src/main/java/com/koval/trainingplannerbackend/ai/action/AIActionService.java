package com.koval.trainingplannerbackend.ai.action;

import com.koval.trainingplannerbackend.ai.UserContextResolver;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
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
    private final UserContextResolver userContextResolver;

    public record ActionContext(String clubId, String clubGroupId, String coachGroupId) {}
    public record ActionResult(String content, boolean success) {}

    public AIActionService(@Qualifier("actionZoneClient") ChatClient actionZoneClient,
                           @Qualifier("actionTrainingSessionClient") ChatClient actionTrainingSessionClient,
                           UserContextResolver userContextResolver) {
        this.actionZoneClient = actionZoneClient;
        this.actionTrainingSessionClient = actionTrainingSessionClient;
        this.userContextResolver = userContextResolver;
    }

    public ActionResult execute(String userMessage, AIActionType actionType, ActionContext context, String userId) {
        UserContext userCtx = userContextResolver.resolve(userId);
        ChatClient client = actionType == AIActionType.ZONE_CREATION
                ? actionZoneClient
                : actionTrainingSessionClient;

        String systemContext = buildSystemContext(userCtx, context);

        try {
            String content = client.prompt()
                    .messages(new SystemMessage(systemContext))
                    .user(userMessage)
                    .call()
                    .content();
            return new ActionResult(content != null ? content : "Done.", true);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return new ActionResult("Action failed: " + msg, false);
        }
    }

    private String buildSystemContext(UserContext ctx, ActionContext context) {
        return "userId = " + ctx.userId() + "\n"
                + "userRole = " + ctx.role() + "\n"
                + "clubId = " + (context.clubId() != null ? context.clubId() : "null") + "\n"
                + "clubGroupId = " + (context.clubGroupId() != null ? context.clubGroupId() : "null") + "\n"
                + "coachGroupId = " + (context.coachGroupId() != null ? context.coachGroupId() : "null");
    }
}

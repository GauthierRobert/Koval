package com.koval.trainingplannerbackend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.ai.agents.RouterService;
import com.koval.trainingplannerbackend.ai.agents.TrainingAgent;
import com.koval.trainingplannerbackend.ai.logger.UsageTracker;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates AI chat interactions by routing to specialist agents.
 * Manages history and delegates to the appropriate agent based on classification.
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final Map<AgentType, TrainingAgent> agents;
    private final RouterService routerService;
    private final ChatHistoryService chatHistoryService;
    private final UserContextResolver userContextResolver;
    private final ChatClient plannerClient;
    private final ObjectMapper objectMapper;

    public AIService(List<TrainingAgent> agentList,
                     RouterService routerService,
                     ChatHistoryService chatHistoryService,
                     UserContextResolver userContextResolver,
                     @Qualifier("plannerClient") ChatClient plannerClient,
                     ObjectMapper objectMapper) {
        this.agents = agentList.stream()
                .collect(Collectors.toMap(TrainingAgent::getAgentType, Function.identity()));
        this.routerService = routerService;
        this.chatHistoryService = chatHistoryService;
        this.userContextResolver = userContextResolver;
        this.plannerClient = plannerClient;
        this.objectMapper = objectMapper;
    }

    // ── Synchronous chat ────────────────────────────────────────────────

    @Timed(value = "ai.chat", description = "Time spent in synchronous AI chat call")
    public ChatMessageResponse chat(String userMessage, String userId, String chatHistoryId, AgentType agentType) {
        ChatTurn turn = prepareTurn(userMessage, userId, chatHistoryId, agentType);
        ChatMessageResponse response = turn.agent().chat(userMessage, userId, turn.history().getId(), turn.ctx());
        chatHistoryService.updateAfterResponse(turn.history(), userMessage, turn.resolvedType());
        return response;
    }

    public record ChatMessageResponse(String chatHistoryId, AssistantMessage message, AgentType agentType,
                                          UsageTracker.UsageSnapshot usage) {
    }

    // ── Streaming chat ──────────────────────────────────────────────────

    @Timed(value = "ai.chat.stream", description = "Time to assemble AI streaming response")
    public StreamResponse chatStream(String userMessage, String userId, String chatHistoryId, AgentType agentType) {
        ChatTurn turn = prepareTurn(userMessage, userId, chatHistoryId, agentType);
        String conversationId = turn.history().getId();
        StreamResponse agentResponse = turn.agent().chatStream(userMessage, userId, conversationId, turn.ctx());

        // Use doFinally to handle history update on both complete and error (#8)
        Flux<ServerSentEvent<String>> wrappedEvents = agentResponse.events()
                .doFinally(signal -> chatHistoryService.updateAfterResponse(turn.history(), userMessage, turn.resolvedType()));

        return new StreamResponse(conversationId, wrappedEvents);
    }

    private ChatTurn prepareTurn(String userMessage, String userId, String chatHistoryId, AgentType requested) {
        UserContext ctx = userContextResolver.resolve(userId);
        ChatHistory history = chatHistoryService.findOrCreate(userId, chatHistoryId);
        AgentType resolved = resolveAgent(requested, userMessage, ctx.role(), history.getLastAgentType());
        return new ChatTurn(ctx, history, resolved, agents.get(resolved));
    }

    private record ChatTurn(UserContext ctx, ChatHistory history, AgentType resolvedType, TrainingAgent agent) {}

    public record StreamResponse(String chatHistoryId, Flux<ServerSentEvent<String>> events) {
    }

    // ── Planner ─────────────────────────────────────────────────────────

    public record PlanTask(String task, String agentType) {}

    public List<PlanTask> plan(String userMessage) {
        String json = plannerClient.prompt().user(userMessage).call().content();
        String preview = truncate(userMessage, 80);
        try {
            List<PlanTask> tasks = objectMapper.readValue(stripJsonFences(json), new TypeReference<>() {});
            log.debug("Plan decomposed into {} task(s) for message: '{}'", tasks.size(), preview);
            return tasks;
        } catch (JsonProcessingException e) {
            log.warn("Plan decomposition failed for message '{}': {}", preview, e.getMessage());
            return List.of(new PlanTask(userMessage, "GENERAL"));
        }
    }

    private static String stripJsonFences(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        return trimmed.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ── Internals ───────────────────────────────────────────────────────

    private AgentType resolveAgent(AgentType requestedAgentType, String userMessage, String userRole, String lastAgentType) {
        if (requestedAgentType != null) {
            if (requiresCoachRole(requestedAgentType) && !UserContextResolver.COACH_ROLE.equals(userRole)) {
                log.debug("Downgrading {} to GENERAL for non-coach user (role={})", requestedAgentType, userRole);
                return AgentType.GENERAL;
            }
            return requestedAgentType;
        }
        AgentType classified = routerService.classify(userMessage, userRole, lastAgentType);
        if (requiresCoachRole(classified) && !UserContextResolver.COACH_ROLE.equals(userRole)) {
            log.debug("Router classified as {} but user role is {}, falling back to GENERAL", classified, userRole);
            return AgentType.GENERAL;
        }
        return classified;
    }

    private static boolean requiresCoachRole(AgentType type) {
        return type == AgentType.COACH_MANAGEMENT || type == AgentType.CLUB_MANAGEMENT;
    }
}

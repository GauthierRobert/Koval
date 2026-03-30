package com.koval.trainingplannerbackend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.ai.agents.RouterService;
import com.koval.trainingplannerbackend.ai.agents.TrainingAgent;
import com.koval.trainingplannerbackend.ai.logger.UsageTracker;
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

    public ChatMessageResponse chat(String userMessage, String userId, String chatHistoryId, AgentType agentType) {
        UserContext ctx = userContextResolver.resolve(userId);
        ChatHistory chatHistory = chatHistoryService.findOrCreate(userId, chatHistoryId);

        AgentType resolved = resolveAgent(agentType, userMessage, ctx.role(), chatHistory.getLastAgentType());
        TrainingAgent agent = agents.get(resolved);

        ChatMessageResponse response = agent.chat(userMessage, userId, chatHistory.getId(), ctx);
        chatHistoryService.updateAfterResponse(chatHistory, userMessage, resolved);
        return response;
    }

    public record ChatMessageResponse(String chatHistoryId, AssistantMessage message, AgentType agentType,
                                          UsageTracker.UsageSnapshot usage) {
    }

    // ── Streaming chat ──────────────────────────────────────────────────

    public StreamResponse chatStream(String userMessage, String userId, String chatHistoryId, AgentType agentType) {
        UserContext ctx = userContextResolver.resolve(userId);
        ChatHistory chatHistory = chatHistoryService.findOrCreate(userId, chatHistoryId);
        String conversationId = chatHistory.getId();

        AgentType resolved = resolveAgent(agentType, userMessage, ctx.role(), chatHistory.getLastAgentType());
        TrainingAgent agent = agents.get(resolved);

        StreamResponse agentResponse = agent.chatStream(userMessage, userId, conversationId, ctx);

        // Use doFinally to handle history update on both complete and error (#8)
        Flux<ServerSentEvent<String>> wrappedEvents = agentResponse.events()
                .doFinally(signal -> chatHistoryService.updateAfterResponse(chatHistory, userMessage, resolved));

        return new StreamResponse(conversationId, wrappedEvents);
    }

    public record StreamResponse(String chatHistoryId, Flux<ServerSentEvent<String>> events) {
    }

    // ── Planner ─────────────────────────────────────────────────────────

    public record PlanTask(String task, String agentType) {}

    public List<PlanTask> plan(String userMessage) {
        String json = plannerClient.prompt().user(userMessage).call().content();
        try {
            // Strip markdown code fences if present
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            List<PlanTask> tasks = objectMapper.readValue(cleaned, new TypeReference<List<PlanTask>>() {});
            log.debug("Plan decomposed into {} task(s) for message: '{}'", tasks.size(),
                    userMessage.length() > 80 ? userMessage.substring(0, 80) + "..." : userMessage);
            return tasks;
        } catch (Exception e) {
            log.warn("Plan decomposition failed for message '{}': {}",
                    userMessage.length() > 80 ? userMessage.substring(0, 80) + "..." : userMessage,
                    e.getMessage());
            return List.of(new PlanTask(userMessage, "GENERAL"));
        }
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

package com.koval.trainingplannerbackend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.ai.agents.RouterService;
import com.koval.trainingplannerbackend.ai.agents.TrainingAgent;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<AgentType, TrainingAgent> agents;
    private final RouterService routerService;
    private final ChatHistoryService chatHistoryService;
    private final UserContextResolver userContextResolver;
    private final ChatClient plannerClient;

    public AIService(List<TrainingAgent> agentList,
                     RouterService routerService,
                     ChatHistoryService chatHistoryService,
                     UserContextResolver userContextResolver,
                     @Qualifier("plannerClient") ChatClient plannerClient) {
        this.agents = agentList.stream()
                .collect(Collectors.toMap(TrainingAgent::getAgentType, Function.identity()));
        this.routerService = routerService;
        this.chatHistoryService = chatHistoryService;
        this.userContextResolver = userContextResolver;
        this.plannerClient = plannerClient;
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

    public record ChatMessageResponse(String chatHistoryId, AssistantMessage message, AgentType agentType) {
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
            return OBJECT_MAPPER.readValue(cleaned, new TypeReference<List<PlanTask>>() {});
        } catch (Exception e) {
            // Fall back to GENERAL instead of TRAINING_CREATION to avoid sending arbitrary text
            // through the most capable agent (#7)
            return List.of(new PlanTask(userMessage, "GENERAL"));
        }
    }

    // ── Internals ───────────────────────────────────────────────────────

    private AgentType resolveAgent(AgentType explicit, String userMessage, String userRole, String lastAgentType) {
        if (explicit != null) {
            // Prevent ATHLETE from using COACH_MANAGEMENT
            if (explicit == AgentType.COACH_MANAGEMENT && !"COACH".equals(userRole)) {
                return AgentType.GENERAL;
            }
            return explicit;
        }
        return routerService.classify(userMessage, userRole, lastAgentType);
    }
}

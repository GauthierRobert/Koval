package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.ai.agents.RouterService;
import com.koval.trainingplannerbackend.ai.agents.TrainingAgent;
import org.springframework.ai.chat.messages.AssistantMessage;
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

    private final Map<AgentType, TrainingAgent> agents;
    private final RouterService routerService;
    private final ChatHistoryService chatHistoryService;
    private final UserContextResolver userContextResolver;

    public AIService(List<TrainingAgent> agentList,
                     RouterService routerService,
                     ChatHistoryService chatHistoryService,
                     UserContextResolver userContextResolver) {
        this.agents = agentList.stream()
                .collect(Collectors.toMap(TrainingAgent::getAgentType, Function.identity()));
        this.routerService = routerService;
        this.chatHistoryService = chatHistoryService;
        this.userContextResolver = userContextResolver;
    }

    // ── Synchronous chat ────────────────────────────────────────────────

    public ChatMessageResponse chat(String userMessage, String userId, String chatHistoryId, AgentType agentType) {
        UserContext ctx = userContextResolver.resolve(userId);
        ChatHistory chatHistory = chatHistoryService.findOrCreate(userId, chatHistoryId);

        AgentType resolved = resolveAgent(agentType, userMessage, ctx.role());
        TrainingAgent agent = agents.get(resolved);

        ChatMessageResponse response = agent.chat(userMessage, userId, chatHistory.getId());
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

        AgentType resolved = resolveAgent(agentType, userMessage, ctx.role());
        TrainingAgent agent = agents.get(resolved);

        StreamResponse agentResponse = agent.chatStream(userMessage, userId, conversationId);

        // Wrap to handle post-stream history update
        Flux<ServerSentEvent<String>> wrappedEvents = agentResponse.events()
                .doOnComplete(() -> chatHistoryService.updateAfterResponse(chatHistory, userMessage, resolved));

        return new StreamResponse(conversationId, wrappedEvents);
    }

    public record StreamResponse(String chatHistoryId, Flux<ServerSentEvent<String>> events) {
    }

    // ── Internals ───────────────────────────────────────────────────────

    private AgentType resolveAgent(AgentType explicit, String userMessage, String userRole) {
        if (explicit != null) {
            // Prevent ATHLETE from using COACH_MANAGEMENT
            if (explicit == AgentType.COACH_MANAGEMENT && !"COACH".equals(userRole)) {
                return AgentType.GENERAL;
            }
            return explicit;
        }
        return routerService.classify(userMessage, userRole);
    }
}

package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.TrainingManagementService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Orchestrates AI chat interactions (synchronous and streaming).
 * Delegates history to {@link ChatHistoryService}, user context to {@link UserContextResolver}.
 */
@Service
public class AIService {

    private final ChatClient chatClient;
    private final ChatHistoryService chatHistoryService;
    private final UserContextResolver userContextResolver;
    private final TrainingManagementService trainingManagementService;
    private final CoachService coachService;

    public AIService(ChatClient chatClient,
                     ChatHistoryService chatHistoryService,
                     UserContextResolver userContextResolver,
                     TrainingManagementService trainingManagementService,
                     CoachService coachService) {
        this.chatClient = chatClient;
        this.chatHistoryService = chatHistoryService;
        this.userContextResolver = userContextResolver;
        this.trainingManagementService = trainingManagementService;
        this.coachService = coachService;
    }

    // ── Synchronous chat ────────────────────────────────────────────────

    public ChatMessageResponse chat(String userMessage, String userId, String chatHistoryId) {
        UserContext ctx = userContextResolver.resolve(userId);
        ChatHistory chatHistory = chatHistoryService.findOrCreate(userId, chatHistoryId);

        ChatResponse response = buildPrompt(ctx, chatHistory.getId())
                .user(userMessage)
                .call()
                .chatResponse();

        chatHistoryService.updateAfterResponse(chatHistory, userMessage);
        return new ChatMessageResponse(chatHistory.getId(), response.getResult().getOutput());
    }

    public record ChatMessageResponse(String chatHistoryId, AssistantMessage message) {}

    // ── Streaming chat ──────────────────────────────────────────────────

    /**
     * Streams AI response as SSE events using ChatClient.stream().chatResponse().
     *
     * SSE events emitted:
     * - status: "in_progress" at start, "complete" at end
     * - content: text token from the AI
     * - conversation_id: the conversation ID at the end
     */
    public StreamResponse chatStream(String userMessage, String userId, String chatHistoryId) {
        UserContext ctx = userContextResolver.resolve(userId);
        ChatHistory chatHistory = chatHistoryService.findOrCreate(userId, chatHistoryId);
        String conversationId = chatHistory.getId();

        // 1. Status: in_progress
        Flux<ServerSentEvent<String>> statusStart = Flux.just(sse("status", "in_progress"));

        // 2. Stream ChatResponse chunks — extract content and tool calls
        Flux<ServerSentEvent<String>> responseFlux = buildPrompt(ctx, conversationId)
                .user(userMessage)
                .stream()
                .chatResponse()
                .flatMap(this::mapChatResponseToEvents);

        // 3. After stream: status complete + conversation ID
        Flux<ServerSentEvent<String>> postStream = Flux.defer(() -> {
            chatHistoryService.updateAfterResponse(chatHistory, userMessage);
            return Flux.just(
                    sse("status", "complete"),
                    sse("conversation_id", conversationId)
            );
        });

        return new StreamResponse(conversationId, Flux.concat(statusStart, responseFlux, postStream));
    }

    public record StreamResponse(String chatHistoryId, Flux<ServerSentEvent<String>> events) {}

    // ── Internals ───────────────────────────────────────────────────────

    /**
     * Maps a single ChatResponse chunk to an SSE content event (if it has text).
     */
    private Flux<ServerSentEvent<String>> mapChatResponseToEvents(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return Flux.empty();
        }

        String text = response.getResult().getOutput().getText();
        if (text != null && !text.isEmpty()) {
            return Flux.just(sse("content", text));
        }

        return Flux.empty();
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(UserContext ctx, String conversationId) {
        return chatClient.prompt()
                .system(s -> s
                        .param("userId", ctx.userId())
                        .param("userRole", ctx.role())
                        .param("userFtp", ctx.ftp()))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(trainingManagementService, coachService);
    }

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }
}

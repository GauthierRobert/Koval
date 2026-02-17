package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.coach.CoachToolService;
import com.koval.trainingplannerbackend.training.tools.TrainingToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
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
    private final TrainingToolService trainingToolService;
    private final CoachToolService coachToolService;
    private final ContextToolService contextToolService;

    public AIService(ChatClient chatClient,
                     ChatHistoryService chatHistoryService,
                     UserContextResolver userContextResolver,
                     TrainingToolService trainingToolService,
                     CoachToolService coachToolService,
                     ContextToolService contextToolService) {
        this.chatClient = chatClient;
        this.chatHistoryService = chatHistoryService;
        this.userContextResolver = userContextResolver;
        this.trainingToolService = trainingToolService;
        this.coachToolService = coachToolService;
        this.contextToolService = contextToolService;
    }

    // ── Synchronous chat ────────────────────────────────────────────────

    public ChatMessageResponse chat(String userMessage, String userId, String chatHistoryId) {
        UserContext ctx = userContextResolver.resolve(userId);
        ChatHistory chatHistory = chatHistoryService.findOrCreate(userId, chatHistoryId);

        ChatResponse response = buildPrompt(chatHistory.getId())
                .user(buildUserMessage(ctx, userMessage))
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
        Flux<ServerSentEvent<String>> responseFlux = buildPrompt(conversationId)
                .user(buildUserMessage(ctx, userMessage))
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

        // Récupération de l'usage global
        Usage usage = response.getMetadata().getUsage();
        long total = usage.getTotalTokens();
        long prompt = usage.getPromptTokens();
        long generation = usage.getCompletionTokens();
        System.out.println("Total total Tokens: " + total);
        System.out.println("Total prompt Tokens: " + prompt);
        System.out.println("Total generation Tokens: " + generation);

        String text = response.getResult().getOutput().getText();
        if (text != null && !text.isEmpty()) {
            return Flux.just(sse("content", text));
        }

        return Flux.empty();
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(trainingToolService, coachToolService, contextToolService);
    }

    private String buildUserMessage(UserContext ctx, String userMessage) {
        return """
                [User Context]
                The current user is: %s
                The user's role is: %s
                The user's FTP is: %sW

                [User Message]
                %s""".formatted(ctx.userId(), ctx.role(), ctx.ftp(), userMessage);
    }

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }
}

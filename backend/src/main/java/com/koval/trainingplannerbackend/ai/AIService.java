package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Orchestrates AI chat interactions (synchronous and streaming).
 * Delegates history to {@link ChatHistoryService}, user context to {@link UserContextResolver}.
 */
@Service
public class AIService {

    /** Streaming — COACH (all tools, @Primary). */
    private final ChatClient chatClient;
    /** Streaming — ATHLETE (context + training tools only). */
    private final ChatClient athleteChatClient;

    private final ChatHistoryService chatHistoryService;
    private final UserContextResolver userContextResolver;

    public AIService(ChatClient chatClient,
                     ChatClient athleteChatClient,
                     ChatHistoryService chatHistoryService,
                     UserContextResolver userContextResolver) {
        this.chatClient = chatClient;
        this.athleteChatClient = athleteChatClient;
        this.chatHistoryService = chatHistoryService;
        this.userContextResolver = userContextResolver;
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

    public record ChatMessageResponse(String chatHistoryId, AssistantMessage message) {
    }

    // ── Streaming chat ──────────────────────────────────────────────────

    /**
     * Streams AI response as SSE events using ChatClient.stream().chatResponse().
     * <p>
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
        var statusStart = Flux.just(sse("status", "in_progress"));

        // 2. Stream ChatResponse chunks — extract content and tool calls
        var responseFlux = buildPrompt(ctx, conversationId)
                .user(userMessage)
                .stream()
                .chatResponse()
                .flatMap(this::mapChatResponseToEvents);

        // 3. After stream: status complete + conversation ID
        var postStream = Flux.defer(() -> {
            chatHistoryService.updateAfterResponse(chatHistory, userMessage);
            return Flux.just(
                    sse("status", "complete"),
                    sse("conversation_id", conversationId)
            );
        });

        return new StreamResponse(conversationId, Flux.concat(statusStart, responseFlux, postStream));
    }

    public record StreamResponse(String chatHistoryId, Flux<ServerSentEvent<String>> events) {

    }

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

    /**
     * Builds a prompt spec. Selects the role-appropriate ChatClient bean so the
     * SYSTEM_AND_TOOLS cache prefix is stable and hits correctly on Anthropic's side.
     */
    private ChatClient.ChatClientRequestSpec buildPrompt(UserContext ctx, String conversationId) {
        ChatClient client = "COACH".equals(ctx.role()) ? chatClient : athleteChatClient;
        return client.prompt()
                .messages(new SystemMessage(systemContext(ctx)))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
    }

    private String systemContext(UserContext ctx) {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);

        return """
                The current user is: %s
                The user's role is: %s
                The user's FTP is: %sW
                Today: %s (%s) — Week: %s to %s""".formatted(
                ctx.userId(), ctx.role(), ctx.ftp(),
                today, dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                weekStart, weekEnd);
    }

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }
}

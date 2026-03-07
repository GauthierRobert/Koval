package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.AIService.ChatMessageResponse;
import com.koval.trainingplannerbackend.ai.AIService.StreamResponse;
import com.koval.trainingplannerbackend.ai.UserContextResolver;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.net.SocketException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/**
 * Shared logic for all specialist agents: prompt building, streaming, error handling.
 */
public abstract class BaseAgentService implements TrainingAgent {

    private final ChatClient chatClient;
    private final UserContextResolver userContextResolver;

    protected BaseAgentService(ChatClient chatClient,
                               UserContextResolver userContextResolver) {
        this.chatClient = chatClient;
        this.userContextResolver = userContextResolver;
    }

    @Override
    public ChatMessageResponse chat(String userMessage, String userId, String conversationId) {
        UserContext ctx = userContextResolver.resolve(userId);
        var response = buildPrompt(ctx, conversationId)
                .user(userMessage)
                .call()
                .chatResponse();
        return new ChatMessageResponse(conversationId, response.getResult().getOutput(), getAgentType());
    }

    @Override
    public StreamResponse chatStream(String userMessage, String userId, String conversationId) {
        UserContext ctx = userContextResolver.resolve(userId);

        Sinks.Many<ServerSentEvent<String>> toolSink =
                Sinks.many().multicast().onBackpressureBuffer();

        var statusStart = Flux.just(sse("status", "in_progress"));
        var agentEvent = Flux.just(sse("agent", getAgentType().name()));

        var responseFlux = buildPrompt(ctx, conversationId, toolSink)
                .user(userMessage)
                .stream()
                .chatResponse()
                .flatMap(this::mapChatResponseToEvents)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .jitter(0.5)
                        .filter(BaseAgentService::isTransientNetworkError))
                .onErrorResume(ex -> Flux.just(sse("error", streamErrorMessage(ex))))
                .doOnTerminate(toolSink::tryEmitComplete);

        var postStream = Flux.just(
                sse("status", "complete"),
                sse("conversation_id", conversationId));

        var merged = Flux.merge(
                Flux.concat(statusStart, agentEvent, responseFlux),
                toolSink.asFlux());

        return new StreamResponse(conversationId, Flux.concat(merged, postStream));
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(UserContext ctx, String conversationId) {
        return chatClient.prompt()
                .messages(new SystemMessage(systemContext(ctx)))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(UserContext ctx, String conversationId,
                                                         Sinks.Many<ServerSentEvent<String>> toolSink) {
        return buildPrompt(ctx, conversationId)
                .toolContext(Map.of("toolSink", toolSink));
    }

    protected String systemContext(UserContext ctx) {
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

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private static boolean isTransientNetworkError(Throwable ex) {
        if (ex instanceof WebClientRequestException) return true;
        if (ex instanceof SocketException) return true;
        Throwable cause = ex.getCause();
        return cause instanceof SocketException || cause instanceof java.io.IOException;
    }

    private String streamErrorMessage(Throwable ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("429") || msg.contains("rate_limit")) {
            return "Rate limit exceeded. Please shorten your message or wait a moment and try again.";
        }
        if (isTransientNetworkError(ex) || msg.contains("Connection reset") || msg.contains("Connection refused")) {
            return "Connection to the AI service was interrupted. Please try again.";
        }
        return "An unexpected error occurred. Please try again.";
    }
}

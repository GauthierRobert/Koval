package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.AIService.ChatMessageResponse;
import com.koval.trainingplannerbackend.ai.AIService.StreamResponse;
import com.koval.trainingplannerbackend.ai.ConversationSummarizer;
import com.koval.trainingplannerbackend.ai.UsageTracker;
import com.koval.trainingplannerbackend.ai.UsageTracker.UsageSnapshot;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Shared logic for all specialist agents: prompt building, streaming, error handling.
 */
public abstract class BaseAgentService implements TrainingAgent {

    private final ChatClient chatClient;
    private final ZoneSystemService zoneSystemService;
    private final UsageTracker usageTracker;
    private final ConversationSummarizer conversationSummarizer;

    protected BaseAgentService(ChatClient chatClient, ZoneSystemService zoneSystemService,
                                UsageTracker usageTracker, ConversationSummarizer conversationSummarizer) {
        this.chatClient = chatClient;
        this.zoneSystemService = zoneSystemService;
        this.usageTracker = usageTracker;
        this.conversationSummarizer = conversationSummarizer;
    }

    @Override
    public ChatMessageResponse chat(String userMessage, String userId, String conversationId, UserContext ctx) {
        var response = buildPrompt(ctx, conversationId)
                .user(userMessage)
                .call()
                .chatResponse();
        UsageSnapshot usage = usageTracker.extractUsage(response);
        usageTracker.logUsage(getAgentType().name(), conversationId, usage);
        conversationSummarizer.summarizeIfNeeded(conversationId);
        return new ChatMessageResponse(conversationId, response.getResult().getOutput(), getAgentType(), usage);
    }

    @Override
    public StreamResponse chatStream(String userMessage, String userId, String conversationId, UserContext ctx) {
        Sinks.Many<ServerSentEvent<String>> toolSink =
                Sinks.many().multicast().onBackpressureBuffer();

        var statusStart = Flux.just(sse("status", "in_progress"));
        var agentEvent = Flux.just(sse("agent", getAgentType().name()));

        AtomicLong totalInput = new AtomicLong(0);
        AtomicLong totalOutput = new AtomicLong(0);

        var responseFlux = buildPrompt(ctx, conversationId, toolSink)
                .user(userMessage)
                .stream()
                .chatResponse()
                .doOnNext(response -> accumulateUsage(response, totalInput, totalOutput))
                .flatMap(this::mapChatResponseToEvents)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .jitter(0.5)
                        .filter(BaseAgentService::isTransientNetworkError))
                .onErrorResume(ex -> Flux.just(sse("error", streamErrorMessage(ex))))
                .doOnTerminate(toolSink::tryEmitComplete);

        var postStream = Flux.defer(() -> {
            String usageJson = "{\"inputTokens\":" + totalInput.get() + ",\"outputTokens\":" + totalOutput.get() + "}";
            usageTracker.logUsage(getAgentType().name(), conversationId,
                    new UsageSnapshot(totalInput.get(), totalOutput.get(), totalInput.get() + totalOutput.get()));
            conversationSummarizer.summarizeIfNeeded(conversationId);
            return Flux.just(
                    sse("usage", usageJson),
                    sse("status", "complete"),
                    sse("conversation_id", conversationId));
        });

        var merged = Flux.merge(
                Flux.concat(statusStart, agentEvent, responseFlux),
                toolSink.asFlux());

        return new StreamResponse(conversationId, Flux.concat(merged, postStream));
    }

    private void accumulateUsage(ChatResponse response, AtomicLong totalInput, AtomicLong totalOutput) {
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            if (usage.getPromptTokens() > 0) totalInput.set(usage.getPromptTokens());
            if (usage.getCompletionTokens() > 0) totalOutput.addAndGet(usage.getCompletionTokens());
        }
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(UserContext ctx, String conversationId) {
        String context = systemContext(ctx);
        String summary = conversationSummarizer.getSummaryIfNeeded(conversationId);
        if (summary != null) {
            context = context + "\n\nPrevious conversation summary: " + summary;
        }
        return chatClient.prompt()
                .messages(new SystemMessage(context))
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

        String base = """
                The current user is: %s
                The user's role is: %s
                The user's FTP is: %sW
                Today: %s (%s) — Week: %s to %s""".formatted(
                ctx.userId(), ctx.role(), ctx.ftp(),
                today, dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                weekStart, weekEnd);

        String zoneContext = buildDefaultZoneContext(ctx);
        return zoneContext.isEmpty() ? base : base + "\n" + zoneContext;
    }

    private String buildDefaultZoneContext(UserContext ctx) {
        if (!"COACH".equals(ctx.role())) return "";
        List<ZoneSystem> defaults = zoneSystemService.getDefaultZoneSystems(ctx.userId());
        if (defaults.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\nDefault Zone Systems:");
        for (ZoneSystem zs : defaults) {
            sb.append("\n- ").append(zs.getSportType())
              .append(": \"").append(zs.getName()).append("\" (id=").append(zs.getId())
              .append(", ref=").append(zs.getReferenceType()).append(") — ");
            String zones = zs.getZones().stream()
                    .map(z -> z.label() + ": " + z.low() + "-" + z.high() + "%" +
                            (z.description() != null ? " (" + z.description() + ")" : ""))
                    .collect(Collectors.joining(", "));
            sb.append(zones);
            if (zs.getAnnotations() != null && !zs.getAnnotations().isBlank()) {
                sb.append("\n  Annotations: ").append(zs.getAnnotations());
            }
        }
        return sb.toString();
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
            return "{\"code\":\"rate_limit_exceeded\",\"message\":\"Rate limit exceeded. Please shorten your message or wait a moment and try again.\",\"retryable\":true}";
        }
        if (isTransientNetworkError(ex) || msg.contains("Connection reset") || msg.contains("Connection refused")) {
            return "{\"code\":\"network_error\",\"message\":\"Connection to the AI service was interrupted. Please try again.\",\"retryable\":true}";
        }
        return "{\"code\":\"internal_error\",\"message\":\"An unexpected error occurred. Please try again.\",\"retryable\":false}";
    }
}

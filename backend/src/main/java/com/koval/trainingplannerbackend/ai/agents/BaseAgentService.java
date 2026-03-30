package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.AIService.ChatMessageResponse;
import com.koval.trainingplannerbackend.ai.AIService.StreamResponse;
import com.koval.trainingplannerbackend.ai.ConversationSummarizer;
import com.koval.trainingplannerbackend.ai.logger.UsageTracker;
import com.koval.trainingplannerbackend.ai.logger.UsageTracker.UsageSnapshot;
import com.koval.trainingplannerbackend.ai.UserContextResolver;
import com.koval.trainingplannerbackend.ai.UserContextResolver.ClubContext;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
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
        return buildPrompt(ctx, conversationId, null);
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(UserContext ctx, String conversationId,
                                                         Sinks.Many<ServerSentEvent<String>> toolSink) {
        String context = systemContext(ctx);
        String summary = conversationSummarizer.getSummaryIfNeeded(conversationId);
        if (summary != null) {
            context = context + "\n\nPrevious conversation summary: " + summary;
        }
        Map<String, Object> toolCtx = toolSink != null
                ? Map.of(SecurityUtils.USER_ID_KEY, ctx.userId(), "toolSink", toolSink)
                : Map.of(SecurityUtils.USER_ID_KEY, ctx.userId());
        return chatClient.prompt()
                .messages(new SystemMessage(context))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(toolCtx);
    }

    protected String systemContext(UserContext ctx) {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);

        StringBuilder sb = new StringBuilder();

        // Base profile line
        sb.append("role=%s ftp=%sW".formatted(ctx.role(), ctx.ftp()));
        if (ctx.css() != null) sb.append(" css=%ss/100m".formatted(ctx.css()));
        if (ctx.ftPace() != null) sb.append(" ftPace=%ss/km".formatted(ctx.ftPace()));
        sb.append(" today=%s(%s) week=%s/%s".formatted(
                today, dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                weekStart, weekEnd));

        // Fitness metrics
        if (ctx.ctl() != null || ctx.atl() != null || ctx.tsb() != null) {
            sb.append("\nctl=%.0f atl=%.0f tsb=%.0f".formatted(
                    ctx.ctl() != null ? ctx.ctl() : 0.0,
                    ctx.atl() != null ? ctx.atl() : 0.0,
                    ctx.tsb() != null ? ctx.tsb() : 0.0));
        }
        if (ctx.displayName() != null) sb.append(" name=").append(ctx.displayName());

        // Athletes (coach-management and scheduling agents)
        AgentType agent = getAgentType();
        if (!ctx.athletes().isEmpty()
                && (agent == AgentType.COACH_MANAGEMENT || agent == AgentType.SCHEDULING)) {
            sb.append("\n\nAthletes:");
            for (var a : ctx.athletes()) {
                sb.append("\n- ").append(a.id()).append(':').append(a.displayName());
            }
        }

        // Groups (coach-management and scheduling agents)
        if (!ctx.athleteGroups().isEmpty()
                && (agent == AgentType.COACH_MANAGEMENT || agent == AgentType.SCHEDULING)) {
            sb.append("\n\nGroups:");
            for (var g : ctx.athleteGroups()) {
                sb.append("\n- ").append(g.id()).append(':').append(g.name());
            }
        }

        // Clubs with groups (club-management agent)
        if (!ctx.clubs().isEmpty() && agent == AgentType.CLUB_MANAGEMENT) {
            sb.append("\n\nClubs:");
            for (ClubContext c : ctx.clubs()) {
                sb.append("\n- ").append(c.id()).append(":\"").append(c.name()).append('"');
                if (!c.groups().isEmpty()) {
                    String groups = c.groups().stream()
                            .map(g -> g.id() + ":" + g.name())
                            .collect(Collectors.joining(","));
                    sb.append(" groups:[").append(groups).append(']');
                }
            }
        }

        // Default zones (any coach agent)
        String zoneContext = buildDefaultZoneContext(ctx);
        if (!zoneContext.isEmpty()) sb.append('\n').append(zoneContext);

        // Custom AI instructions
        if (ctx.aiPrePrompt() != null && !ctx.aiPrePrompt().isBlank()) {
            sb.append("\n\nCoach's custom instructions:\n").append(ctx.aiPrePrompt());
        }

        return sb.toString();
    }

    private String buildDefaultZoneContext(UserContext ctx) {
        if (!UserContextResolver.COACH_ROLE.equals(ctx.role())) return "";
        List<ZoneSystem> defaults = zoneSystemService.getDefaultZoneSystems(ctx.userId());
        if (defaults.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\nDefault Zones:");
        for (ZoneSystem zs : defaults) {
            sb.append("\n- ").append(zs.getSportType())
              .append(" \"").append(zs.getName()).append("\" (id=").append(zs.getId())
              .append(",").append(zs.getReferenceType()).append("): ");
            String zones = zs.getZones().stream()
                    .map(z -> z.label() + ":" + z.low() + "-" + z.high())
                    .collect(Collectors.joining(" "));
            sb.append(zones);
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

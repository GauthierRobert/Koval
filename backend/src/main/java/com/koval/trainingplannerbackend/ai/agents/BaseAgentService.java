package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.AIService.ChatMessageResponse;
import com.koval.trainingplannerbackend.ai.AIService.StreamResponse;
import com.koval.trainingplannerbackend.ai.AiErrorClassifier;
import com.koval.trainingplannerbackend.ai.ConversationSummarizer;
import com.koval.trainingplannerbackend.ai.UserContextResolver;
import com.koval.trainingplannerbackend.ai.UserContextResolver.UserContext;
import com.koval.trainingplannerbackend.ai.logger.UsageTracker;
import com.koval.trainingplannerbackend.ai.logger.UsageTracker.UsageSnapshot;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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

        // Anthropic streaming emits cumulative prompt tokens per chunk; completion tokens accumulate.
        AtomicReference<UsageSnapshot> usageRef = new AtomicReference<>(new UsageSnapshot(0, 0, 0));

        var responseFlux = buildPrompt(ctx, conversationId, toolSink)
                .user(userMessage)
                .stream()
                .chatResponse()
                .doOnNext(response -> usageRef.updateAndGet(prev -> mergeUsage(prev, response)))
                .flatMap(this::mapChatResponseToEvents)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .jitter(0.5)
                        .filter(AiErrorClassifier::isTransient))
                .onErrorResume(ex -> Flux.just(sse("error", AiErrorClassifier.toSseErrorJson(ex))))
                .doOnTerminate(toolSink::tryEmitComplete);

        var postStream = Flux.defer(() -> {
            UsageSnapshot total = usageRef.get();
            usageTracker.logUsage(getAgentType().name(), conversationId, total);
            conversationSummarizer.summarizeIfNeeded(conversationId);
            String usageJson = "{\"inputTokens\":" + total.inputTokens()
                    + ",\"outputTokens\":" + total.outputTokens() + "}";
            return Flux.just(
                    sse("usage", usageJson),
                    sse("status", "complete"),
                    sse("conversation_id", conversationId));
        });

        var merged = Flux.merge(
                Flux.concat(Flux.just(sse("status", "in_progress"), sse("agent", getAgentType().name())),
                        responseFlux),
                toolSink.asFlux());

        return new StreamResponse(conversationId, Flux.concat(merged, postStream));
    }

    private static UsageSnapshot mergeUsage(UsageSnapshot prev, ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return prev;
        }
        var u = response.getMetadata().getUsage();
        long input = u.getPromptTokens() > 0 ? u.getPromptTokens() : prev.inputTokens();
        long output = u.getCompletionTokens() > 0 ? prev.outputTokens() + u.getCompletionTokens() : prev.outputTokens();
        return new UsageSnapshot(input, output, input + output);
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
        StringBuilder sb = new StringBuilder();
        appendCoreLine(sb, ctx);
        appendPmcLine(sb, ctx);
        appendCoachLists(sb, ctx);
        appendClubs(sb, ctx);
        appendDefaultZones(sb, ctx);
        appendCustomInstructions(sb, ctx);
        return sb.toString();
    }

    private void appendCoreLine(StringBuilder sb, UserContext ctx) {
        LocalDate today = LocalDate.now();
        sb.append("role=%s ftp=%sW".formatted(ctx.role(), ctx.ftp()));
        if (ctx.css() != null) sb.append(" css=%ss/100m".formatted(ctx.css()));
        if (ctx.ftPace() != null) sb.append(" ftPace=%ss/km".formatted(ctx.ftPace()));
        sb.append(" today=%s(%s) week=%s/%s".formatted(
                today, today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                today.with(DayOfWeek.MONDAY), today.with(DayOfWeek.SUNDAY)));
    }

    private void appendPmcLine(StringBuilder sb, UserContext ctx) {
        if (ctx.ctl() == null && ctx.atl() == null && ctx.tsb() == null) return;
        sb.append("\nctl=%.0f atl=%.0f tsb=%.0f".formatted(
                nz(ctx.ctl()), nz(ctx.atl()), nz(ctx.tsb())));
    }

    private void appendCoachLists(StringBuilder sb, UserContext ctx) {
        boolean exposesCoachLists = switch (getAgentType()) {
            case COACH_MANAGEMENT, SCHEDULING -> true;
            case TRAINING_CREATION, ANALYSIS, CLUB_MANAGEMENT, GENERAL -> false;
        };
        if (!exposesCoachLists) return;
        appendNamedList(sb, "Athletes", ctx.athletes(), a -> a.id() + ":" + a.displayName());
        appendNamedList(sb, "Groups", ctx.athleteGroups(), g -> g.id() + ":" + g.name());
    }

    private void appendClubs(StringBuilder sb, UserContext ctx) {
        if (getAgentType() != AgentType.CLUB_MANAGEMENT || ctx.clubs().isEmpty()) return;
        sb.append("\n\nClubs:");
        ctx.clubs().forEach(c -> {
            sb.append("\n- ").append(c.id()).append(":\"").append(c.name()).append('"');
            if (!c.groups().isEmpty()) {
                String groups = c.groups().stream()
                        .map(g -> g.id() + ":" + g.name())
                        .collect(Collectors.joining(","));
                sb.append(" groups:[").append(groups).append(']');
            }
        });
    }

    private void appendDefaultZones(StringBuilder sb, UserContext ctx) {
        if (!UserContextResolver.COACH_ROLE.equals(ctx.role())) return;
        List<ZoneSystem> defaults = zoneSystemService.getDefaultZoneSystems(ctx.userId());
        if (defaults.isEmpty()) return;
        sb.append("\nDefault Zones:");
        defaults.forEach(zs -> {
            sb.append("\n- ").append(zs.getSportType())
              .append(" \"").append(zs.getName()).append("\" (id=").append(zs.getId())
              .append(",").append(zs.getReferenceType()).append("): ");
            sb.append(zs.getZones().stream()
                    .map(z -> z.label() + ":" + z.low() + "-" + z.high())
                    .collect(Collectors.joining(" ")));
        });
    }

    private void appendCustomInstructions(StringBuilder sb, UserContext ctx) {
        if (ctx.aiPrePrompt() == null || ctx.aiPrePrompt().isBlank()) return;
        sb.append("\n\nCoach's custom instructions:\n").append(ctx.aiPrePrompt());
    }

    private static <T> void appendNamedList(StringBuilder sb, String label, List<T> items, Function<T, String> fmt) {
        if (items.isEmpty()) return;
        sb.append("\n\n").append(label).append(":");
        items.forEach(it -> sb.append("\n- ").append(fmt.apply(it)));
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private Flux<ServerSentEvent<String>> mapChatResponseToEvents(ChatResponse response) {
        if (response == null || response.getResult() == null) return Flux.empty();
        String text = response.getResult().getOutput().getText();
        return (text == null || text.isEmpty()) ? Flux.empty() : Flux.just(sse("content", text));
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }
}

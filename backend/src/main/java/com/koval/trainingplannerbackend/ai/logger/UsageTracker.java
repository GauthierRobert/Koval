package com.koval.trainingplannerbackend.ai.logger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks and logs AI token usage and cache performance metrics.
 * Provides structured logging and Micrometer counters for cost monitoring,
 * cache hit-rate analysis, and anomaly detection.
 */
@Component
public class UsageTracker {

    private static final Logger log = LoggerFactory.getLogger(UsageTracker.class);

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    public UsageTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public record UsageSnapshot(
            long inputTokens,
            long outputTokens,
            long totalTokens,
            long cacheReadTokens,
            long cacheWriteTokens
    ) {
        public UsageSnapshot(long inputTokens, long outputTokens, long totalTokens) {
            this(inputTokens, outputTokens, totalTokens, 0, 0);
        }
    }

    public UsageSnapshot extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return new UsageSnapshot(0, 0, 0, 0, 0);
        }
        Usage usage = response.getMetadata().getUsage();
        long cacheRead = readLong(usage.getNativeUsage(), "cacheReadInputTokens", "getCacheReadInputTokens");
        long cacheWrite = readLong(usage.getNativeUsage(), "cacheCreationInputTokens", "getCacheCreationInputTokens");
        return new UsageSnapshot(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                cacheRead,
                cacheWrite
        );
    }

    public void logUsage(String agentType, String conversationId, UsageSnapshot usage) {
        log.info("AI usage: agent={} conversation={} input={} output={} total={} cacheRead={} cacheWrite={}",
                agentType, conversationId,
                usage.inputTokens(), usage.outputTokens(), usage.totalTokens(),
                usage.cacheReadTokens(), usage.cacheWriteTokens());
        recordCounter("input", agentType, usage.inputTokens());
        recordCounter("output", agentType, usage.outputTokens());
        recordCounter("cache_read", agentType, usage.cacheReadTokens());
        recordCounter("cache_write", agentType, usage.cacheWriteTokens());
    }

    private void recordCounter(String type, String agentType, long amount) {
        if (amount <= 0) return;
        String key = type + "|" + agentType;
        Counter counter = counterCache.computeIfAbsent(key, k -> Counter.builder("ai.tokens")
                .description("AI token usage by type and agent")
                .tag("type", type)
                .tag("agent", agentType)
                .register(meterRegistry));
        counter.increment(amount);
    }

    /**
     * Best-effort extraction of cache token fields from the Anthropic native usage object.
     * Spring AI's {@code Usage.getNativeUsage()} returns provider-specific data; the Anthropic
     * variant exposes {@code cacheReadInputTokens} and {@code cacheCreationInputTokens} as
     * either record accessors or getters depending on the SDK version. Reflection avoids a hard
     * compile-time dependency on a class path that may shift between Spring AI milestones.
     */
    private static long readLong(Object source, String... candidateNames) {
        if (source == null) return 0;
        Class<?> type = source.getClass();
        for (String name : candidateNames) {
            try {
                Method m = type.getMethod(name);
                Object value = m.invoke(source);
                if (value instanceof Number n) return n.longValue();
            } catch (NoSuchMethodException ignored) {
                // try next candidate
            } catch (ReflectiveOperationException e) {
                log.debug("Failed to read {} from native usage: {}", name, e.getMessage());
                return 0;
            }
        }
        return 0;
    }
}

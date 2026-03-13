package com.koval.trainingplannerbackend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Tracks and logs AI token usage and cache performance metrics.
 * Provides structured logging for cost monitoring and anomaly detection.
 */
@Component
public class UsageTracker {

    private static final Logger log = LoggerFactory.getLogger(UsageTracker.class);

    public record UsageSnapshot(
            long inputTokens,
            long outputTokens,
            long totalTokens
    ) {}

    public UsageSnapshot extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return new UsageSnapshot(0, 0, 0);
        }
        Usage usage = response.getMetadata().getUsage();
        return new UsageSnapshot(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );
    }

    public void logUsage(String agentType, String conversationId, UsageSnapshot usage) {
        log.info("AI usage: agent={} conversation={} input={} output={} total={}",
                agentType, conversationId,
                usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }
}

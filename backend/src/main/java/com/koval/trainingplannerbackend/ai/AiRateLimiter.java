package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.config.exceptions.RateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-user sliding-window rate limiter for AI endpoints.
 * Uses an in-memory ConcurrentHashMap with timestamped request deques.
 */
@Component
public class AiRateLimiter {

    private final int maxPerMinute;
    private final int maxPerHour;

    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    public AiRateLimiter(
            @Value("${app.ai.rate-limit.per-minute:20}") int maxPerMinute,
            @Value("${app.ai.rate-limit.per-hour:100}") int maxPerHour) {
        this.maxPerMinute = maxPerMinute;
        this.maxPerHour = maxPerHour;
    }

    /**
     * Check if the user is within rate limits. Throws RateLimitException if exceeded.
     * Records the request timestamp on success.
     */
    public void checkLimit(String userId) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = requestLog.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());

        // Purge entries older than 1 hour
        Instant oneHourAgo = now.minusSeconds(3600);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(oneHourAgo)) {
            timestamps.pollFirst();
        }

        // Count requests in last minute
        Instant oneMinuteAgo = now.minusSeconds(60);
        long countLastMinute = timestamps.stream().filter(t -> t.isAfter(oneMinuteAgo)).count();
        if (countLastMinute >= maxPerMinute) {
            throw new RateLimitException(
                    "You've sent too many requests. Please wait a moment before trying again (limit: "
                            + maxPerMinute + " requests/minute).");
        }

        // Count requests in last hour
        long countLastHour = timestamps.size();
        if (countLastHour >= maxPerHour) {
            throw new RateLimitException(
                    "Hourly request limit reached. Please try again later (limit: "
                            + maxPerHour + " requests/hour).");
        }

        // Record this request
        timestamps.addLast(now);
    }
}

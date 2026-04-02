package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.config.exceptions.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiRateLimiterTest {

    @Nested
    class PerMinuteLimit {

        private AiRateLimiter limiter;

        @BeforeEach
        void setUp() {
            limiter = new AiRateLimiter(5, 100); // 5/min, 100/hour
        }

        @Test
        void allowsRequestsWithinLimit() {
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 5; i++) {
                    limiter.checkLimit("user1");
                }
            });
        }

        @Test
        void blocksWhenMinuteLimitExceeded() {
            for (int i = 0; i < 5; i++) {
                limiter.checkLimit("user1");
            }
            assertThrows(RateLimitException.class, () -> limiter.checkLimit("user1"));
        }

        @Test
        void differentUsers_independentLimits() {
            for (int i = 0; i < 5; i++) {
                limiter.checkLimit("user1");
            }
            // user2 should still be allowed
            assertDoesNotThrow(() -> limiter.checkLimit("user2"));
        }
    }

    @Nested
    class PerHourLimit {

        @Test
        void blocksWhenHourLimitExceeded() {
            AiRateLimiter limiter = new AiRateLimiter(1000, 3); // high per-minute, low per-hour

            for (int i = 0; i < 3; i++) {
                limiter.checkLimit("user1");
            }

            RateLimitException ex = assertThrows(RateLimitException.class, () -> limiter.checkLimit("user1"));
            assertTrue(ex.getMessage().contains("Hourly"),
                    "Should mention hourly limit: " + ex.getMessage());
        }
    }

    @Nested
    class ExceptionDetails {

        @Test
        void exceptionHasCorrectCode() {
            AiRateLimiter limiter = new AiRateLimiter(1, 100);
            limiter.checkLimit("user1");

            RateLimitException ex = assertThrows(RateLimitException.class, () -> limiter.checkLimit("user1"));
            assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
        }
    }
}

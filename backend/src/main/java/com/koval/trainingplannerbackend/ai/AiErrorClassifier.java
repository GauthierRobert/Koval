package com.koval.trainingplannerbackend.ai;

import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.io.IOException;
import java.net.SocketException;
import java.util.Optional;

/**
 * Single source of truth for classifying upstream Anthropic / network errors.
 * Used by the synchronous controller path (translates to typed exceptions) and
 * by the streaming path (translates to SSE error payloads).
 */
public final class AiErrorClassifier {

    public enum AiErrorKind { RATE_LIMIT, NETWORK, INTERNAL }

    private AiErrorClassifier() {}

    public static AiErrorKind classify(Throwable ex) {
        String msg = Optional.ofNullable(ex.getMessage()).orElse("");
        if (msg.contains("429") || msg.contains("rate_limit")) return AiErrorKind.RATE_LIMIT;
        if (isTransient(ex) || msg.contains("Connection reset") || msg.contains("Connection refused")) {
            return AiErrorKind.NETWORK;
        }
        return AiErrorKind.INTERNAL;
    }

    public static boolean isRateLimit(Throwable ex) {
        return classify(ex) == AiErrorKind.RATE_LIMIT;
    }

    public static boolean isTransient(Throwable ex) {
        if (ex instanceof WebClientRequestException || ex instanceof SocketException) return true;
        return ex.getCause() instanceof IOException;
    }

    public static String toSseErrorJson(Throwable ex) {
        return switch (classify(ex)) {
            case RATE_LIMIT -> "{\"code\":\"rate_limit_exceeded\",\"message\":\"Rate limit exceeded. Please shorten your message or wait a moment and try again.\",\"retryable\":true}";
            case NETWORK -> "{\"code\":\"network_error\",\"message\":\"Connection to the AI service was interrupted. Please try again.\",\"retryable\":true}";
            case INTERNAL -> "{\"code\":\"internal_error\",\"message\":\"An unexpected error occurred. Please try again.\",\"retryable\":false}";
        };
    }
}

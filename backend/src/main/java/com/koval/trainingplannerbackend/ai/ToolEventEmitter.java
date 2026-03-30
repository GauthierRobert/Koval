package com.koval.trainingplannerbackend.ai;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.Optional;

/**
 * Shared SSE helpers for emitting tool_call / tool_result events during streaming.
 * Used by all tool services to avoid duplicating event-emission logic.
 */
public final class ToolEventEmitter {

    private ToolEventEmitter() {}

    public static void emitToolCall(ToolContext ctx, String name, String label) {
        getSink(ctx).ifPresent(s -> s.tryEmitNext(toolSse("tool_call", name, label, true)));
    }

    public static void emitToolResult(ToolContext ctx, String name, String label, boolean ok) {
        getSink(ctx).ifPresent(s -> s.tryEmitNext(toolSse("tool_result", name, label, ok)));
    }

    private static ServerSentEvent<String> toolSse(String event, String name, String label, boolean success) {
        String data = "{\"name\":\"%s\",\"label\":\"%s\",\"success\":%b}"
                .formatted(name, escapeJson(label), success);
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Sinks.Many<ServerSentEvent<String>>> getSink(ToolContext ctx) {
        if (ctx == null) return Optional.empty();
        return Optional.ofNullable(
                (Sinks.Many<ServerSentEvent<String>>) ctx.getContext().get("toolSink"));
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

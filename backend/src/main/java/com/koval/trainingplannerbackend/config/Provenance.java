package com.koval.trainingplannerbackend.config;

import java.time.LocalDateTime;

/**
 * Records how a piece of user-visible content was produced. Stamped on writes that originate
 * outside the web app (e.g. an external Claude client publishing through the MCP server) so
 * the UI can badge them as AI-generated.
 *
 * <p>{@code source} is one of {@code "mcp"}, {@code "web"}, {@code "ai-chat"}. The MCP server
 * does not surface client identity per tool invocation in a typed way today, so
 * {@code mcpClientName} and {@code model} stay nullable for now.
 */
public record Provenance(
        String source,
        String mcpClientName,
        String model,
        LocalDateTime generatedAt) {

    public static Provenance mcp() {
        return new Provenance("mcp", null, null, LocalDateTime.now());
    }

    public static Provenance web() {
        return new Provenance("web", null, null, LocalDateTime.now());
    }
}

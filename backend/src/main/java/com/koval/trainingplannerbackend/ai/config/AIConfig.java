package com.koval.trainingplannerbackend.ai.config;

import com.koval.trainingplannerbackend.ai.logger.PromptLogger;
import com.koval.trainingplannerbackend.ai.toon.ToonToolCallbackProvider;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared AI infrastructure: model constants, prompt loading, option builders,
 * tool wrapping.
 * <p>
 * Extended by {@link AIHaikuConfig} (Haiku) and {@link AISonnetConfig} (Sonnet).
 */
@Configuration
public class AIConfig {

    protected static final String SONNET = "claude-sonnet-4-6";
    protected static final String HAIKU = "claude-haiku-4-5-20251001";

    protected final String commonRules;

    @Autowired(required = false)
    protected PromptLogger promptLogger;

    @Value("${app.ai.toon-responses:true}")
    protected boolean toonResponses;

    public AIConfig() {
        this.commonRules = loadPrompt("common-rules");
    }

    // ── Shared helpers ─────────────────────────────────────────────────

    protected ChatClient.Builder withLogging(ChatClient.Builder builder) {
        if (promptLogger != null) {
            builder.defaultAdvisors(promptLogger);
        }
        return builder;
    }

    protected String agentPrompt(String name) {
        return loadPrompt(name) + "\n" + commonRules;
    }

    public static String loadPrompt(String name) {
        try {
            return new ClassPathResource("prompts/" + name + ".md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load prompt: " + name, e);
        }
    }

    protected ToolCallbackProvider wrapTools(Object... toolObjects) {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects).build();
        return toonResponses ? new ToonToolCallbackProvider(provider) : provider;
    }

    // ── Options helpers ─────────────────────────────────────────────────

    protected AnthropicChatOptions sonnetOptions() {
        return AnthropicChatOptions.builder()
                .model(SONNET)
                .temperature(0.7)
                .maxTokens(2048)
                .cacheOptions(cacheOptions())
                .build();
    }

    protected AnthropicChatOptions haikuOptions() {
        return AnthropicChatOptions.builder()
                .model(HAIKU)
                .temperature(0.7)
                .maxTokens(512)
                .cacheOptions(cacheOptions())
                .build();
    }

    protected AnthropicChatOptions haikuActionOptions() {
        return AnthropicChatOptions.builder()
                .model(HAIKU)
                .temperature(0.3)
                .maxTokens(512)
                .cacheOptions(cacheOptions())
                .build();
    }

    protected AnthropicChatOptions sonnetCachedActionOptions() {
        return AnthropicChatOptions.builder()
                .model(SONNET)
                .temperature(0.3)
                .maxTokens(2048)
                .cacheOptions(cacheOptions())
                .build();
    }

    protected AnthropicCacheOptions cacheOptions() {
        return AnthropicCacheOptions.builder()
                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                .messageTypeTtl(Stream.of(MessageType.values())
                        .collect(Collectors.toMap(mt -> mt, ignored -> AnthropicCacheTtl.FIVE_MINUTES, (m1, m2) -> m1, HashMap::new)))
                .build();
    }
}

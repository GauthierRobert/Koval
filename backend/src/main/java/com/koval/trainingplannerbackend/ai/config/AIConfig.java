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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

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
    protected final PromptLogger promptLogger;

    @Value("${app.ai.toon-responses:true}")
    protected boolean toonResponses;

    public AIConfig(Optional<PromptLogger> promptLogger) {
        this.commonRules = loadPrompt("common-rules");
        this.promptLogger = promptLogger.orElse(null);
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
        return anthropicOptions(SONNET, 0.7, 2048);
    }

    protected AnthropicChatOptions haikuOptions() {
        return anthropicOptions(HAIKU, 0.7, 512);
    }

    protected AnthropicChatOptions haikuActionOptions() {
        return anthropicOptions(HAIKU, 0.3, 512);
    }

    protected AnthropicChatOptions sonnetCachedActionOptions() {
        return anthropicOptions(SONNET, 0.3, 2048);
    }

    private AnthropicChatOptions anthropicOptions(String model, double temperature, int maxTokens) {
        return AnthropicChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .cacheOptions(cacheOptions())
                .build();
    }

    protected AnthropicCacheOptions cacheOptions() {
        Map<MessageType, AnthropicCacheTtl> ttl = new EnumMap<>(MessageType.class);
        for (MessageType mt : MessageType.values()) {
            ttl.put(mt, AnthropicCacheTtl.FIVE_MINUTES);
        }
        return AnthropicCacheOptions.builder()
                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                .messageTypeTtl(ttl)
                .build();
    }
}

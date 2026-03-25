package com.koval.trainingplannerbackend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Content;
import reactor.core.publisher.Flux;

/**
 * Logs the full prompt (system messages, user message, tool definitions)
 * just before it is sent to the model. Activated via {@code app.ai.log-prompts=true}.
 *
 * Insert as an advisor on any ChatClient to see exactly what the model receives.
 */
public class PromptLogger implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(PromptLogger.class);

    @Override
    public String getName() {
        return "PromptLogger";
    }

    @Override
    public int getOrder() {
        return 0; // run first, before other advisors
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        logRequest(request);
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        logRequest(request);
        return chain.nextStream(request);
    }

    private void logRequest(ChatClientRequest request) {
        if (!log.isInfoEnabled()) return;
        Prompt prompt = request.prompt();
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══ AI PROMPT ══════════════════════════════════════════════════╗\n");

        // Tool names
        var toolNames = prompt.getInstructions().stream().map(Content::getText).toList();
        if (toolNames != null && !toolNames.isEmpty()) {
            sb.append("── INSTRUCTIONS (").append(toolNames.size()).append(") ──────────────────────────────────────────\n");
            sb.append("  ").append(String.join(", ", toolNames)).append("\n");
        }


        // Chat options
        ChatOptions options = prompt.getOptions();
        if (options != null) {
            sb.append("── OPTIONS ────────────────────────────────────────────────────\n");
            if (options.getModel() != null) sb.append("  model = ").append(options.getModel()).append("\n");
            if (options.getTemperature() != null) sb.append("  temperature = ").append(options.getTemperature()).append("\n");
            if (options.getMaxTokens() != null) sb.append("  maxTokens = ").append(options.getMaxTokens()).append("\n");
        }

        sb.append("╚═══════════════════════════════════════════════════════════════╝");
        log.info(sb.toString());
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "... [truncated, " + text.length() + " chars total]";
    }

}

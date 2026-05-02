package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.config.AIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Classifies user messages into agent types via a lightweight Haiku LLM call (~150 tokens).
 * Always calls the LLM — the cost of a misrouted request (~10k tokens) far exceeds
 * the router call cost.
 */
@Service
public class RouterService {

    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private static final String ROUTER_SYSTEM = AIConfig.loadPrompt("router");

    private final ChatClient routerClient;

    public RouterService(@Qualifier("routerClient") ChatClient routerClient) {
        this.routerClient = routerClient;
    }

    public AgentType classify(String userMessage, String userRole, String lastAgentType) {
        try {
            String lastAgent = Optional.ofNullable(lastAgentType).orElse("NONE");
            String systemPrompt = ROUTER_SYSTEM.replace("{lastAgent}", lastAgent);

            String result = routerClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            AgentType classified = AgentType.valueOf(result.trim().toUpperCase());
            log.debug("Router classified message as {}", classified);
            return classified;
        } catch (Exception e) {
            log.warn("Router classification failed, falling back to GENERAL: {}", e.getMessage());
            return AgentType.GENERAL;
        }
    }
}

package com.koval.trainingplannerbackend.ai.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Classifies user messages into agent types using a lightweight Haiku model.
 * No tools, no memory — a single classification call.
 */
@Service
public class RouterService {

    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private static final String ROUTER_SYSTEM = """
            You are a message classifier for a triathlon/cycling training assistant.
            Classify the user message into exactly one of these categories:

            TRAINING_CREATION — creating, modifying, or designing workout plans
            SCHEDULING — assigning workouts to dates, calendar management, schedule queries, race goals (add/edit/delete/list goals)
            ANALYSIS — reviewing past sessions, performance metrics, PMC/CTL/ATL/TSB analysis, fitness relative to race goals
            COACH_MANAGEMENT — managing athletes, tags, zone systems, coach-specific operations
            GENERAL — greetings, general questions, anything that doesn't fit above

            The previous message in this conversation was handled by: {lastAgent}.
            If the message is ambiguous or a follow-up (e.g. "now schedule it", "delete that one"), prefer staying with the previous agent.

            Reply with ONLY the category label, nothing else.""";

    private final ChatClient routerClient;

    public RouterService(@Qualifier("routerClient") ChatClient routerClient) {
        this.routerClient = routerClient;
    }

    public AgentType classify(String userMessage, String userRole, String lastAgentType) {
        try {
            String lastAgent = lastAgentType != null ? lastAgentType : "NONE";
            String systemPrompt = ROUTER_SYSTEM.replace("{lastAgent}", lastAgent);

            String result = routerClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            AgentType agentType = AgentType.valueOf(result.trim().toUpperCase());

            // ATHLETE cannot use COACH_MANAGEMENT — fall back to GENERAL
            if (agentType == AgentType.COACH_MANAGEMENT && !"COACH".equals(userRole)) {
                log.debug("Router classified as COACH_MANAGEMENT but user is {}, falling back to GENERAL", userRole);
                return AgentType.GENERAL;
            }

            return agentType;
        } catch (Exception e) {
            log.warn("Router classification failed, falling back to GENERAL: {}", e.getMessage());
            return AgentType.GENERAL;
        }
    }
}

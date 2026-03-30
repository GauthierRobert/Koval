package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.ConversationSummarizer;
import com.koval.trainingplannerbackend.ai.UsageTracker;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Generic specialist agent parameterized by {@link AgentType}.
 * Replaces the individual per-agent subclasses that were structurally identical.
 */
public class SpecialistAgentService extends BaseAgentService {

    private final AgentType agentType;

    public SpecialistAgentService(AgentType agentType,
                                  ChatClient chatClient,
                                  ZoneSystemService zoneSystemService,
                                  UsageTracker usageTracker,
                                  ConversationSummarizer conversationSummarizer) {
        super(chatClient, zoneSystemService, usageTracker, conversationSummarizer);
        this.agentType = agentType;
    }

    @Override
    public AgentType getAgentType() {
        return agentType;
    }
}

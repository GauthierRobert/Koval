package com.koval.trainingplannerbackend.ai.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AnalysisAgentService extends BaseAgentService {

    public AnalysisAgentService(@Qualifier("analysisClient") ChatClient chatClient) {
        super(chatClient);
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.ANALYSIS;
    }
}

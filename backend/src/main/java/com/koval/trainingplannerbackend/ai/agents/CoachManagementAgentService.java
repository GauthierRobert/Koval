package com.koval.trainingplannerbackend.ai.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CoachManagementAgentService extends BaseAgentService {

    public CoachManagementAgentService(@Qualifier("coachManagementClient") ChatClient chatClient) {
        super(chatClient);
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.COACH_MANAGEMENT;
    }
}

package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.UserContextResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GeneralAgentService extends BaseAgentService {

    public GeneralAgentService(@Qualifier("generalClient") ChatClient chatClient,
                               UserContextResolver userContextResolver) {
        super(chatClient, userContextResolver);
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.GENERAL;
    }
}

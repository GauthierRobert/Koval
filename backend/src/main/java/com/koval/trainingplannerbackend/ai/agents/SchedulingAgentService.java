package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SchedulingAgentService extends BaseAgentService {

    public SchedulingAgentService(@Qualifier("schedulingClient") ChatClient chatClient,
                                    ZoneSystemService zoneSystemService) {
        super(chatClient, zoneSystemService);
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.SCHEDULING;
    }
}

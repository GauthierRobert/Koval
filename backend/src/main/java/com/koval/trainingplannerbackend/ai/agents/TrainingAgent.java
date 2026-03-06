package com.koval.trainingplannerbackend.ai.agents;

import com.koval.trainingplannerbackend.ai.AIService.ChatMessageResponse;
import com.koval.trainingplannerbackend.ai.AIService.StreamResponse;

/**
 * Common contract for all specialist agents.
 */
public interface TrainingAgent {

    AgentType getAgentType();

    ChatMessageResponse chat(String userMessage, String userId, String conversationId);

    StreamResponse chatStream(String userMessage, String userId, String conversationId);
}

package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.ai.agents.SpecialistAgentService;
import com.koval.trainingplannerbackend.ai.agents.TrainingAgent;
import com.koval.trainingplannerbackend.club.tools.ClubToolService;
import com.koval.trainingplannerbackend.coach.tools.CoachToolService;
import com.koval.trainingplannerbackend.goal.GoalToolService;
import com.koval.trainingplannerbackend.race.RaceToolService;
import com.koval.trainingplannerbackend.training.history.HistoryToolService;
import com.koval.trainingplannerbackend.training.tools.TrainingToolService;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import com.koval.trainingplannerbackend.training.zone.ZoneToolService;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Haiku-powered agents for lightweight operations: scheduling, analysis,
 * management queries, routing, and planning.
 */
@Configuration
public class AISimpleRequestConfig extends AIConfig {

    private static final int CHAT_MEMORY_WINDOW_SIZE = 8;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(CHAT_MEMORY_WINDOW_SIZE)
                .build();
    }

    @Bean
    public ChatClient schedulingClient(AnthropicChatModel chatModel,
                                       ChatMemory chatMemory,
                                       ContextToolService contextToolService,
                                       TrainingToolService trainingToolService,
                                       CoachToolService coachToolService,
                                       GoalToolService goalToolService,
                                       RaceToolService raceToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("scheduling"))
                .defaultOptions(haikuOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(wrapTools(contextToolService, coachToolService, goalToolService, raceToolService))
                .build();
    }

    @Bean
    public ChatClient analysisClient(AnthropicChatModel chatModel,
                                     ChatMemory chatMemory,
                                     ContextToolService contextToolService,
                                     HistoryToolService historyToolService,
                                     GoalToolService goalToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("analysis"))
                .defaultOptions(haikuOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(wrapTools(contextToolService, historyToolService, goalToolService))
                .build();
    }

    @Bean
    public ChatClient coachManagementClient(AnthropicChatModel chatModel,
                                            ChatMemory chatMemory,
                                            ContextToolService contextToolService,
                                            CoachToolService coachToolService,
                                            ZoneToolService zoneToolService,
                                            GoalToolService goalToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("coach-management"))
                .defaultOptions(haikuOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(wrapTools(contextToolService, coachToolService, zoneToolService, goalToolService))
                .build();
    }

    @Bean
    public ChatClient clubManagementClient(AnthropicChatModel chatModel,
                                            ChatMemory chatMemory,
                                            ContextToolService contextToolService,
                                            ClubToolService clubToolService,
                                            TrainingToolService trainingToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("club-management"))
                .defaultOptions(haikuOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(wrapTools(contextToolService, clubToolService, trainingToolService))
                .build();
    }

    @Bean
    public ChatClient generalClient(AnthropicChatModel chatModel,
                                    ChatMemory chatMemory,
                                    ContextToolService contextToolService,
                                    HistoryToolService historyToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("general"))
                .defaultOptions(haikuOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(wrapTools(contextToolService, historyToolService))
                .build();
    }

    @Bean
    public ChatClient routerClient(AnthropicChatModel chatModel) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(HAIKU)
                        .temperature(0.0)
                        .maxTokens(20)
                        .cacheOptions(cacheOptions())
                        .build())
                .build();
    }

    @Bean
    public ChatClient plannerClient(AnthropicChatModel chatModel) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(HAIKU)
                        .temperature(0.0)
                        .maxTokens(512)
                        .build())
                .defaultSystem(loadPrompt("planner"))
                .build();
    }

    // ── Agent beans ─────────────────────────────────────────────────────

    @Bean
    public TrainingAgent schedulingAgent(@Qualifier("schedulingClient") ChatClient chatClient,
                                         ZoneSystemService zoneSystemService,
                                         UsageTracker usageTracker,
                                         ConversationSummarizer conversationSummarizer) {
        return new SpecialistAgentService(AgentType.SCHEDULING, chatClient, zoneSystemService, usageTracker, conversationSummarizer);
    }

    @Bean
    public TrainingAgent analysisAgent(@Qualifier("analysisClient") ChatClient chatClient,
                                       ZoneSystemService zoneSystemService,
                                       UsageTracker usageTracker,
                                       ConversationSummarizer conversationSummarizer) {
        return new SpecialistAgentService(AgentType.ANALYSIS, chatClient, zoneSystemService, usageTracker, conversationSummarizer);
    }

    @Bean
    public TrainingAgent coachManagementAgent(@Qualifier("coachManagementClient") ChatClient chatClient,
                                              ZoneSystemService zoneSystemService,
                                              UsageTracker usageTracker,
                                              ConversationSummarizer conversationSummarizer) {
        return new SpecialistAgentService(AgentType.COACH_MANAGEMENT, chatClient, zoneSystemService, usageTracker, conversationSummarizer);
    }

    @Bean
    public TrainingAgent clubManagementAgent(@Qualifier("clubManagementClient") ChatClient chatClient,
                                             ZoneSystemService zoneSystemService,
                                             UsageTracker usageTracker,
                                             ConversationSummarizer conversationSummarizer) {
        return new SpecialistAgentService(AgentType.CLUB_MANAGEMENT, chatClient, zoneSystemService, usageTracker, conversationSummarizer);
    }

    @Bean
    public TrainingAgent generalAgent(@Qualifier("generalClient") ChatClient chatClient,
                                      ZoneSystemService zoneSystemService,
                                      UsageTracker usageTracker,
                                      ConversationSummarizer conversationSummarizer) {
        return new SpecialistAgentService(AgentType.GENERAL, chatClient, zoneSystemService, usageTracker, conversationSummarizer);
    }
}

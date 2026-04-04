package com.koval.trainingplannerbackend.ai.config;

import com.koval.trainingplannerbackend.ai.ConversationSummarizer;
import com.koval.trainingplannerbackend.ai.agents.AgentType;
import com.koval.trainingplannerbackend.ai.agents.SpecialistAgentService;
import com.koval.trainingplannerbackend.ai.agents.TrainingAgent;
import com.koval.trainingplannerbackend.ai.anonymization.AnonymizationService;
import com.koval.trainingplannerbackend.ai.logger.UsageTracker;
import com.koval.trainingplannerbackend.ai.tools.action.CreationTrainingToolService;
import com.koval.trainingplannerbackend.ai.tools.action.CreationTrainingWithClubSessionToolService;
import com.koval.trainingplannerbackend.ai.tools.training.TrainingToolService;
import com.koval.trainingplannerbackend.ai.tools.zone.ZoneToolService;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Schedulers;

/**
 * Sonnet-powered agents for complex creation tasks: training workouts,
 * zone systems, training+session combos, and race completion.
 */
@Configuration
public class AISonnetConfig extends AIConfig {

    @Bean
    public ChatClient trainingCreationClient(AnthropicChatModel chatModel,
                                             ChatMemory chatMemory,
                                             TrainingToolService trainingToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("training-creation"))
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).scheduler(Schedulers.boundedElastic()).build())
                .defaultToolCallbacks(wrapTools(trainingToolService))
                .build();
    }

    @Bean
    public ChatClient actionZoneClient(AnthropicChatModel chatModel,
                                       ZoneToolService zoneToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(loadPrompt("action-zone"))
                .defaultOptions(sonnetOptions())
                .defaultToolCallbacks(wrapTools(zoneToolService))
                .build();
    }

    @Bean
    public ChatClient actionTrainingSessionClient(AnthropicChatModel chatModel,
                                                  CreationTrainingWithClubSessionToolService creationTrainingWithClubSessionToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(loadPrompt("action-training-session"))
                .defaultOptions(sonnetCachedActionOptions())
                .defaultToolCallbacks(wrapTools(creationTrainingWithClubSessionToolService))
                .build();
    }

    @Bean
    public ChatClient actionTrainingCreatorClient(AnthropicChatModel chatModel,
                                                  CreationTrainingToolService creationTrainingToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("training-creation"))
                .defaultOptions(sonnetOptions())
                .defaultToolCallbacks(wrapTools(creationTrainingToolService))
                .build();
    }

    @Bean
    public ChatClient raceCompletionClient(AnthropicChatModel chatModel) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(SONNET)
                        .temperature(0.3)
                        .maxTokens(2048)
                        .build())
                .defaultSystem(loadPrompt("race-completion"))
                .build();
    }

    // ── Agent bean ──────────────────────────────────────────────────────

    @Bean
    public TrainingAgent trainingCreationAgent(@Qualifier("trainingCreationClient") ChatClient chatClient,
                                               ZoneSystemService zoneSystemService,
                                               UsageTracker usageTracker,
                                               ConversationSummarizer conversationSummarizer,
                                               AnonymizationService anonymizationService) {
        return new SpecialistAgentService(AgentType.TRAINING_CREATION, chatClient, zoneSystemService, usageTracker, conversationSummarizer, anonymizationService);
    }
}

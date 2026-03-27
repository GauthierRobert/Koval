package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.action.AIActionToolService;
import com.koval.trainingplannerbackend.ai.action.CreationTrainingToolService;
import com.koval.trainingplannerbackend.club.tools.ClubToolService;
import com.koval.trainingplannerbackend.coach.tools.CoachToolService;
import com.koval.trainingplannerbackend.goal.GoalToolService;
import com.koval.trainingplannerbackend.race.RaceToolService;
import com.koval.trainingplannerbackend.training.history.HistoryToolService;
import com.koval.trainingplannerbackend.training.tools.TrainingToolService;
import com.koval.trainingplannerbackend.training.zone.ZoneToolService;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration for the multi-agent AI system.
 * Each agent gets a dedicated ChatClient with focused system prompt and specific tools.
 * Prompts are loaded from classpath resources under /prompts/.
 */
@Configuration
public class AIConfig {

    private static final String SONNET = "claude-sonnet-4-6";
    private static final String HAIKU = "claude-haiku-4-5-20251001";

    private final String commonRules;

    @Autowired(required = false)
    private PromptLogger promptLogger;

    public AIConfig() {
        this.commonRules = loadPrompt("common-rules");
    }

    private ChatClient.Builder withLogging(ChatClient.Builder builder) {
        if (promptLogger != null) {
            builder.defaultAdvisors(promptLogger);
        }
        return builder;
    }

    // ── Beans ───────────────────────────────────────────────────────────

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(8)
                .build();
    }

    @Bean
    public ChatClient trainingCreationClient(AnthropicChatModel chatModel,
                                             ChatMemory chatMemory,
                                             ContextToolService contextToolService,
                                             TrainingToolService trainingToolService) {
        //TODO temporary — plans disabled: PlanToolService removed from tools
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("training-creation"))
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, trainingToolService)
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
        //TODO temporary — plans disabled: PlanToolService removed from tools
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("scheduling"))
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, trainingToolService, coachToolService, goalToolService, raceToolService)
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
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, historyToolService, goalToolService)
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
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, coachToolService, zoneToolService, goalToolService)
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
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, clubToolService, trainingToolService)
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
                .defaultTools(contextToolService, historyToolService)
                .build();
    }

    @Bean
    public ChatClient actionZoneClient(AnthropicChatModel chatModel,
                                       ZoneToolService zoneToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(loadPrompt("action-zone"))
                .defaultOptions(sonnetOptions())
                .defaultTools(zoneToolService)
                .build();
    }

    @Bean
    public ChatClient actionTrainingSessionClient(AnthropicChatModel chatModel,
                                                  AIActionToolService aiActionToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(loadPrompt("action-training-session"))
                .defaultOptions(sonnetCachedActionOptions())
                .defaultTools(aiActionToolService)
                .build();
    }

@Bean
    public ChatClient actionTrainingCreatorClient(AnthropicChatModel chatModel,
                                                  ContextToolService contextToolService,
                                                   CreationTrainingToolService creationTrainingToolService) {
        return withLogging(ChatClient.builder(chatModel))
                .defaultSystem(agentPrompt("training-creation"))
                .defaultOptions(sonnetOptions())
                .defaultTools(contextToolService, creationTrainingToolService)
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

    // ── Prompt loading ───────────────────────────────────────────────────

    private String agentPrompt(String name) {
        return loadPrompt(name) + "\n" + commonRules;
    }

    private static String loadPrompt(String name) {
        try {
            return new ClassPathResource("prompts/" + name + ".md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load prompt: " + name, e);
        }
    }

    // ── Options helpers ─────────────────────────────────────────────────

    private AnthropicChatOptions sonnetOptions() {
        return AnthropicChatOptions.builder()
                .model(SONNET)
                .temperature(0.7)
                .maxTokens(4096)
                .cacheOptions(cacheOptions())
                .build();
    }

    private AnthropicChatOptions haikuOptions() {
        return AnthropicChatOptions.builder()
                .model(HAIKU)
                .temperature(0.7)
                .maxTokens(512)
                .cacheOptions(cacheOptions())
                .build();
    }

    private AnthropicChatOptions haikuActionOptions() {
        return AnthropicChatOptions.builder()
                .model(HAIKU)
                .temperature(0.3)
                .maxTokens(512)
                .cacheOptions(cacheOptions())
                .build();
    }

    private AnthropicChatOptions sonnetCachedActionOptions() {
        return AnthropicChatOptions.builder()
                .model(SONNET)
                .temperature(0.3)
                .maxTokens(2048)
                .cacheOptions(cacheOptions())
                .build();
    }

    private AnthropicCacheOptions cacheOptions() {
        return AnthropicCacheOptions.builder()
                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                .messageTypeTtl(Stream.of(MessageType.values())
                        .collect(Collectors.toMap(mt -> mt, ignored -> AnthropicCacheTtl.ONE_HOUR, (m1, m2) -> m1, HashMap::new)))
                .build();
    }
}

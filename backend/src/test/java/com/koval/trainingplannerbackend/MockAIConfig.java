package com.koval.trainingplannerbackend;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Provides mock beans for the AI layer so integration tests don't require
 * an Anthropic API key or real AI infrastructure.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockAIConfig {

    @Bean
    @Primary
    public AnthropicChatModel mockChatModel() {
        return mock(AnthropicChatModel.class);
    }

    @Bean
    @Primary
    public ChatMemoryRepository mockChatMemoryRepository() {
        return mock(ChatMemoryRepository.class);
    }

    @Bean
    @Primary
    public ChatMemory mockChatMemory() {
        return mock(ChatMemory.class);
    }

    @Bean
    @Primary
    public ChatClient trainingCreationClient() {
        return mock(ChatClient.class);
    }

    @Bean("schedulingClient")
    @Primary
    public ChatClient schedulingClient() {
        return mock(ChatClient.class);
    }

    @Bean("analysisClient")
    @Primary
    public ChatClient analysisClient() {
        return mock(ChatClient.class);
    }

    @Bean("coachManagementClient")
    @Primary
    public ChatClient coachManagementClient() {
        return mock(ChatClient.class);
    }

    @Bean("generalClient")
    @Primary
    public ChatClient generalClient() {
        return mock(ChatClient.class);
    }

    @Bean("actionZoneClient")
    @Primary
    public ChatClient actionZoneClient() {
        return mock(ChatClient.class);
    }

    @Bean("actionTrainingSessionClient")
    @Primary
    public ChatClient actionTrainingSessionClient() {
        return mock(ChatClient.class);
    }

    @Bean("routerClient")
    @Primary
    public ChatClient routerClient() {
        return mock(ChatClient.class);
    }

    @Bean("plannerClient")
    @Primary
    public ChatClient plannerClient() {
        return mock(ChatClient.class);
    }
}

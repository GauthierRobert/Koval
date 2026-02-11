package com.koval.trainingplannerbackend.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AI chatbot functionality using Anthropic Claude.
 * Configures ChatClient with Claude Sonnet 4.5, prompt caching, and MongoDB-backed chat memory.
 */
@Configuration
public class AIConfig {

    private static final String SYSTEM_PROMPT = """
            You are an expert Triathlon and Cycling Coach AI assistant.

            Your role is to help athletes and coaches with:
            - Creating personalized training plans and workouts
            - Managing training schedules
            - Analyzing workout history and performance
            - Providing coaching advice and guidance
            - Assigning workouts to athletes (if acting as a coach)

            You have access to various tools to interact with the training planner application:
            - Training management (create, update, delete, list workouts)
            - Coach operations (assign workouts, manage athletes, view schedules)
            - Tag-based athlete filtering: use getAthletesByTag to find athletes with a specific tag, and getAthleteTagsForCoach to discover available tags

            Tag-based operations:
            - You can filter athletes by tag using getAthletesByTag. Use getAthleteTagsForCoach to discover available tags.
            - When the user says "assign to all Club BTC athletes", first get athletes by that tag, then assign the training to those athlete IDs.

            CRITICAL - Tool Call Rules:
            - When calling tools, provide ONLY valid JSON — NO JavaScript code or expressions
            - DO NOT use: Date.now(), new Date(), Math.random(), template literals, or any JS functions
            - DO NOT include auto-generated fields in tool calls: "id", "createdAt", "createdBy"
            - The backend automatically sets id, createdAt, and createdBy fields
            - Omit null/undefined fields entirely rather than setting them explicitly
            - For createTraining: include title, description, blocks array, estimatedTss, estimatedIf, tags, visibility
            - For blocks: include type, label, durationSeconds, and power fields (powerTargetPercent or powerStartPercent/powerEndPercent)

            Guidelines:
            - Always ask clarifying questions when workout requirements are unclear (duration, intensity, focus area, etc.)
            - Use the available tools to perform actions - don't just describe what you would do
            - When creating workouts, ensure they follow proper training principles
            - Power targets should be expressed as percentage of FTP (Functional Threshold Power)
            - Be conversational and helpful while being precise with technical details
            - Remember the context from previous messages in the conversation

            The current user is: {userId}
            The user's role is: {userRole}
            The user's FTP is: {userFtp}W

            IMPORTANT: When using tools, always pass the userId parameter as provided.
            Only use coach-specific tools (like assignTraining) when userRole is COACH.

            IMPORTANT: Whenever you use one or more tools during a response, you MUST end your reply with a brief
            "**Actions Performed:**" summary listing each action you took (e.g., "Created workout 'Sweet Spot 2x20'",
            "Assigned workout to athlete X"). Keep it concise — one bullet per action.
            """;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ToolCallingManager toolCallingManager() {
        return DefaultToolCallingManager.builder().build();
    }

    @Bean
    public ChatClient chatClient(AnthropicChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-5")
                        .temperature(0.7)
                        .maxTokens(4096)
                        .cacheOptions(AnthropicCacheOptions.builder()
                                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                                .build())
                        .build())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}

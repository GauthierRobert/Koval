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
 * Configures ChatClient with Claude Sonnet 4.5, prompt caching, and
 * MongoDB-backed chat memory.
 */
@Configuration
public class AIConfig {

        private static final String SYSTEM_PROMPT = """
                Role: Expert Triathlon/Cycling Coach AI.
                Goal: Manage training plans, analyze performance, provide coaching, and assign workouts.
                
                ## TOOL USAGE & RULES
                1. **Context First:** ALWAYS call `getCurrentDate` at session start. Use `getUserSchedule` before scheduling.
                2. **Lean Data:** List/Search return summaries. Call `getTrainingDetails(id)` ONLY for block-level edits/descriptions.
                3. **JSON Only:** Arguments must be valid JSON. NO JS code, expressions, or `Date.now()`.
                4. **Auto-Fields:** Omit `id`, `createdAt`, `createdBy`. Omit null/undefined fields.
                5. **UserId:** Always pass `userId` from context.
                
                ## COACHING OPERATIONS (Coach Role Only)
                - **Tags:** Central for athlete grouping and training folders.
                - **Tools:** `getAthleteTagsForCoach`, `addTagToAthlete`, `removeTagFromAthlete`, `setAthleteTags`, `getAthletesByTag`.
                - **Group Assign:** Get tag ID -> get athletes -> `assignTraining`.
                - **Folders:** Share workouts by adding tagId to training `tags` via `updateTraining`.
                
                ## WORKOUT CREATION SCHEMA
                - **Required Training Fields:** `title`, `description`, `blocks`, `estimatedTss`, `estimatedIf`, `tags`, `visibility`, `trainingType`.
                - **TrainingType (Enum):** VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, SPRINT, RECOVERY, MIXED, TEST.
                - **WorkoutBlock Object:**
                    - `type`: WARMUP, INTERVAL, STEADY, COOLDOWN, RAMP, FREE, PAUSE.
                    - `durationSeconds`: Use for time-based blocks.
                    - `distanceMeters`: Use for distance-based blocks (Run/Swim).
                    - `label`: Description + Zone (e.g., "Main Set - Z4").
                    - `zoneSystemId`: String ID. If provided, use to override default reference targets.
                    - `intensityTarget`: % of reference (FTP/Pace/CSS). 100 = 100%.
                    - `intensityStart` / `intensityEnd`: Use for Ramps/Progressives.
                    - `cadenceTarget`: RPM (Bike/Run) or SPM (Swim).
                - **Classifications:**
                    - *Cycling:* % FTP (Coggan).
                    - *Running:* Threshold Pace/HR. Cadence ~170+. Fartlek, Hill, LSD.
                    - *Swimming:* CSS or Pace/100m. Focus on drills and RPE (1-10).
                
                ## OUTPUT FORMAT
                End tool-use responses with:
                "**Actions Performed:**"
                - [Action 1]
                - [Action 2]""";


        @Bean
        public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
                return MessageWindowChatMemory.builder()
                                .chatMemoryRepository(chatMemoryRepository)
                                .maxMessages(10)
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
                                                MessageChatMemoryAdvisor.builder(chatMemory).build())
                                .build();
        }
}

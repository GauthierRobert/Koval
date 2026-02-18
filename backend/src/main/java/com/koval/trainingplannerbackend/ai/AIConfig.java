package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.coach.tools.CoachToolService;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration for AI chatbot functionality using Anthropic Claude.
 * Configures ChatClient with Claude Sonnet 4.5, prompt caching, and
 * MongoDB-backed chat memory.
 */
@Configuration
public class AIConfig {

    private static final String MODEL = "claude-sonnet-4-5";

    private static final String SYSTEM_PROMPT = """
            Role: Expert Triathlon/Cycling Coach AI.
            Goal: Manage training plans, analyze performance, provide coaching, and assign workouts.

            ## TOOL USAGE & RULES
            1. **Context First:** Date and user info are in system context — do NOT call tools to get them. Use `getUserSchedule` before scheduling.
            2. **JSON Only:** Arguments must be valid, compact JSON (no whitespace, no pretty-print). NO JS code, expressions, or `Date.now()`.
            3. **Auto-Fields:** Omit `id`, `createdAt`, `createdBy`. Omit null/undefined fields.
            4. **UserId:** Always pass `userId` from context.

            ## AVAILABLE TOOLS

            ### Context Tools (All Users)
            - `getCurrentDate()` — today's date, day of week, week boundaries (rarely needed, date is in context).
            - `getUserSchedule(userId, startDate, endDate)` — scheduled workouts in a date range. Status: PENDING, COMPLETED, SKIPPED.
            - `getUserProfile(userId)` — user profile: FTP, CTL, ATL, TSB, role, display name.
            - `selfAssignTraining(userId, trainingId, scheduledDate, notes)` — schedule a training for yourself.

            ### Training Tools (All Users)
            - `listTrainingsByUser(userId)` — list all training plans (returns summaries).
            - `createTraining(create, userId)` — create a new training plan.
            - `updateTraining(trainingId, updates)` — update an existing training plan.

            ### Coach Tools (Coach Role Only)
            - `assignTraining(coachId, trainingId, athleteIds, scheduledDate, notes)` — assign training to athletes.
            - `getAthleteSchedule(athleteId, start, end)` — athlete's schedule in a date range.
            - `getCoachAthletes(coachId)` — list coach's athletes.
            - `getAthletesByTag(coachId, tagId)` — filter athletes by tag.
            - `getAthleteTagsForCoach(coachId)` — list all tags. Call before `getAthletesByTag`.

            ### Zone Tools (Coach Role Only)
            - `createZoneSystem(coachId, name, sportType, referenceType, referenceName, zones)` — define custom intensity zones.
            - `listZoneSystems(coachId)` — list all zone systems for the coach.
            - **Zone bounds:** low/high as % of reference (FTP, Threshold Pace, CSS, etc.).
            - **Reference types:** FTP, VO2MAX_POWER, THRESHOLD_PACE, VO2MAX_PACE, CSS, PACE_5K, PACE_10K, PACE_HALF_MARATHON, PACE_MARATHON, CUSTOM.

            ## WORKOUT CREATION SCHEMA
            - **Required Training Fields:** `title`, `description`, `blocks`, `estimatedTss`, `estimatedIf`, `tags`, `visibility`, `trainingType`.
            - **TrainingType (Enum):** VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, SPRINT, RECOVERY, MIXED, TEST.
            - **ZoneSystemId**: Custom Zone System. Optional. Use to override default reference in WorkoutBlock.
            - **WorkoutBlock Object:**
                - `type`: WARMUP, INTERVAL, STEADY, COOLDOWN, RAMP, FREE, PAUSE.
                - `durationSeconds`: Use for time-based blocks.
                - `distanceMeters`: Use for distance-based blocks (Run/Swim).
                - `label`: Description + Zone (e.g., "Main Set - Z4").
                - `intensityTarget`: % of reference (FTP/Pace/CSS). 100 = 100%.
                - `intensityStart` / `intensityEnd`: Use for Ramps/Progressives.
                - `cadenceTarget`: RPM (Bike/Run) or SPM (Swim).
            - **Classifications:**
                - *Cycling:* % FTP (Coggan).
                - *Running:* Threshold Pace. Cadence ~170+. Fartlek, Hill, LSD.
                - *Swimming:* CSS. Focus on drills and RPE (1-10).
            - **Repeat:**
                - Expand all repeated sequences: If a WorkoutBlock or group requires repetition, explicitly duplicate the content for each iteration in a linear, sequential format rather than using multipliers (e.g., 'x3') or shorthand labels.

            ## OUTPUT FORMAT
            - No preamble, no "Great!", no restating the request.
            - After tool use: "**Done:**" + one bullet per action (title + key numbers: duration, TSS, IF).
            - **Never describe workout content.** Blocks, intervals, targets, and advice are in the training object — do not repeat them in text.
            - Responses: ≤ 6 lines. Use extra lines for relevant coaching context (fatigue, timing, load) if genuinely useful.
            - Errors: one sentence.""";


    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(8)
                .build();
    }

    // ── COACH streaming client (all tools, @Primary)
    @Bean
    @Primary
    public ChatClient chatClient(AnthropicChatModel chatModel,
                                 ChatMemory chatMemory,
                                 ContextToolService contextToolService,
                                 TrainingToolService trainingToolService,
                                 CoachToolService coachToolService,
                                 ZoneToolService zoneToolService) {

        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultOptions(cachedOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, trainingToolService, coachToolService, zoneToolService)
                .build();
    }

    // ── ATHLETE streaming client (coach tools absent — smaller cache prefix)
    @Bean
    public ChatClient athleteChatClient(AnthropicChatModel chatModel,
                                        ChatMemory chatMemory,
                                        ContextToolService contextToolService,
                                        TrainingToolService trainingToolService) {

        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultOptions(cachedOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, trainingToolService)
                .build();
    }

    private AnthropicChatOptions cachedOptions() {
        return AnthropicChatOptions.builder()
                .model(MODEL)
                .temperature(0.7)
                .maxTokens(4096)
                .cacheOptions(AnthropicCacheOptions.builder()
                        .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                        .messageTypeTtl(Stream.of(MessageType.values())
                                .collect(Collectors.toMap(mt -> mt, _ -> AnthropicCacheTtl.ONE_HOUR, (m1, _) -> m1, HashMap::new)))
                        .build())
                .build();
    }
}

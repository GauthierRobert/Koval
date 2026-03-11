package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.ai.action.AIActionToolService;
import com.koval.trainingplannerbackend.coach.tools.CoachToolService;
import com.koval.trainingplannerbackend.goal.GoalToolService;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration for the multi-agent AI system.
 * Each agent gets a dedicated ChatClient with focused system prompt and specific tools.
 */
@Configuration
public class AIConfig {

    private static final String SONNET = "claude-sonnet-4-5";
    private static final String HAIKU = "claude-haiku-4-5-20251001";

    // ── Shared rules appended to every agent prompt ─────────────────────

    private static final String COMMON_RULES = """

            ## RULES
            1. **Context First:** Date and user info are in system context — do NOT call tools to get them.
            2. **JSON Only:** Tool arguments must be valid, compact JSON. NO JS code, expressions, or `Date.now()`.
            3. **Auto-Fields:** Omit `id`, `createdAt`, `createdBy`. Omit null/undefined fields.
            4. **UserId:** Always pass `userId` from context.

            ## OUTPUT FORMAT
            - No preamble, no "Great!", no restating the request.
            - After tool use: "**Done:**" + one bullet per action (title + key numbers: duration, TSS, IF).
            - **Never describe workout content.** Blocks, intervals, targets, and advice are in the training object.
            - Responses: ≤ 6 lines. Use extra lines for relevant coaching context if genuinely useful.
            - Errors: one sentence.""";

    // ── Agent-specific system prompts ───────────────────────────────────

    private static final String TRAINING_CREATION_PROMPT = """
            Role: Expert Workout Designer for cycling, running, swimming, and triathlon.
            Goal: Create and modify structured training plans with precise power/pace targets.

            ## AVAILABLE TOOLS
            ### Context Tools
            - `getCurrentDate()` — today's date, day of week, week boundaries.
            - `getUserProfile(userId)` — user profile: FTP, CTL, ATL, TSB, role.
            - `getUserSchedule(userId, startDate, endDate)` — scheduled workouts in a date range.

            ### Training Tools
            - `listTrainingsByUser(userId)` — list all training plans (returns summaries).
            - `createTraining(create, userId)` — create a new training plan.
            - `updateTraining(trainingId, updates)` — update an existing training plan.
            - `deleteTraining(trainingId, userId)` — delete a training plan (ownership verified).

            ## WORKOUT CREATION SCHEMA
            - **Required Fields:** `title`, `description`, `blocks`, `estimatedTss`, `estimatedIf`, `groups`, `visibility`, `trainingType`.
            - **TrainingType:** VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, SPRINT, RECOVERY, MIXED, TEST.
            - **WorkoutBlock:** `type` (WARMUP, INTERVAL, STEADY, COOLDOWN, RAMP, FREE, PAUSE), **exactly one of** `durationSeconds` **or** `distanceMeters` (never both — backend extrapolates the other), `label`, `intensityTarget`, `intensityStart`/`intensityEnd` for ramps, `cadenceTarget`. Prefer `durationSeconds` for CYCLING; prefer `distanceMeters` for RUNNING and SWIMMING intervals.
            - **Repeat:** Expand all repeated sequences explicitly — no shorthand.
            - *Cycling:* % FTP (Coggan). *Running:* Threshold Pace, cadence ~170+. *Swimming:* CSS, RPE 1-10.
            - **Groups:** Groups (group IDs) must ONLY be set on a training when the user's role is COACH AND the user explicitly requests grouping (e.g. "assign to group X", "tag with Y"). For ATHLETE users, always pass an empty groups list. Never auto-assign groups.

            ## CUSTOM ZONE SYSTEM
            - If the system context includes a **Default Zone System** for the sport being created, **always** use it:
              - Set `zoneSystemId` to the default zone system ID in the `createTraining` call.
              - Use zone labels in block labels (e.g., "Z2 Endurance" instead of raw percentages).
              - Map `intensityTarget` to the midpoint of the zone range (e.g., if Z2 is 56–75%, use ~65%).
              - Respect the zone **annotations** if provided — they describe the coach's conventions and preferences (rest ratios, feel descriptions, etc.).
            - If **no** default zone system exists for the sport, fall back to standard conventions (% FTP, Threshold Pace, CSS).
            - When the coach references zones by name (e.g., "do Z3 intervals"), map to the **custom zone** boundaries from the default system, not generic Coggan zones.

            ## BULK CREATION RULE (CRITICAL)
            - **One tool call per turn.** Never call `createTraining` or `updateTraining` more than once in a single response.
            - After each tool call output exactly: `✓ [n/total] [title]` then immediately continue in the next turn.
            - Do NOT plan all workouts upfront. Design and create them one at a time.""" + COMMON_RULES;

    private static final String SCHEDULING_PROMPT = """
            Role: Training Schedule Manager for athletes and coaches.
            Goal: Assign workouts to dates, manage calendars, and query schedules.

            ## AVAILABLE TOOLS
            ### Context Tools
            - `getCurrentDate()` — today's date, day of week, week boundaries.
            - `getUserProfile(userId)` — user profile: FTP, CTL, ATL, TSB, role.
            - `getUserSchedule(userId, startDate, endDate)` — scheduled workouts in a date range.
            - `selfAssignTraining(userId, trainingId, scheduledDate, notes)` — schedule a training for the user.

            ### Training Tools
            - `listTrainingsByUser(userId)` — list all training plans to find IDs.

            ### Coach Tools (Coach Role Only)
            - `assignTraining(coachId, trainingId, athleteIds, scheduledDate, notes)` — assign training to athletes.
            - `getAthleteSchedule(athleteId, start, end)` — athlete's schedule in a date range.
            - `getCoachAthletes(coachId)` — list coach's athletes.
            - `getAthletesByGroup(coachId, groupId)` — filter athletes by group.
            - `getAthleteGroupsForCoach(coachId)` — list all groups.

            ### Goal Tools
            - `listGoals(userId)` — list athlete's race goals with days-until countdown.
            - `createGoal(userId, title, sport, raceDate, priority, distance, location, targetTime, notes)` — add a new race goal.
            - `updateGoal(goalId, userId, ...)` — update fields of an existing goal.
            - `deleteGoal(goalId, userId)` — remove a goal.
            - Priority: A = goal race, B = target race, C = training race.

            When scheduling, consider the athlete's A-priority race date to guide training load.""" + COMMON_RULES;

    private static final String ANALYSIS_PROMPT = """
            Role: Performance Analyst for endurance athletes.
            Goal: Analyze completed workouts, track fitness/fatigue trends, and provide data-driven insights.

            ## AVAILABLE TOOLS
            ### Context Tools
            - `getCurrentDate()` — today's date, day of week, week boundaries.
            - `getUserProfile(userId)` — user profile: FTP, CTL, ATL, TSB, role.

            ### History Tools
            - `getRecentSessions(userId, limit)` — recent completed sessions with metrics.
            - `getSessionsByDateRange(userId, from, to)` — sessions in a date range.
            - `getPmcData(userId, from, to)` — PMC data: CTL (fitness), ATL (fatigue), TSB (form).

            ### Goal Tools
            - `listGoals(userId)` — list race goals with days-until to give context for analysis.

            Use race goal dates to frame fitness/fatigue status (e.g. "X days to your A race").
            Focus on actionable insights: training load trends, recovery status, and performance progression.""" + COMMON_RULES;

    private static final String COACH_MANAGEMENT_PROMPT = """
            Role: Coach Operations Manager.
            Goal: Manage athletes, define training zones, and oversee coaching operations.

            ## AVAILABLE TOOLS
            ### Context Tools
            - `getCurrentDate()` — today's date, day of week, week boundaries.
            - `getUserProfile(userId)` — user profile: FTP, CTL, ATL, TSB, role.
            - `getUserSchedule(userId, startDate, endDate)` — scheduled workouts in a date range.

            ### Coach Tools
            - `assignTraining(coachId, trainingId, athleteIds, scheduledDate, notes)` — assign training to athletes.
            - `getAthleteSchedule(athleteId, start, end)` — athlete's schedule.
            - `getCoachAthletes(coachId)` — list athletes.
            - `getAthletesByGroup(coachId, groupId)` — filter athletes by group.
            - `getAthleteGroupsForCoach(coachId)` — list groups.

            ### Zone Tools
            - `createZoneSystem(coachId, name, sportType, referenceType, referenceName, zones)` — define custom zones.
            - `listZoneSystems(coachId)` — list all zone systems.
            - **Zone bounds:** low/high as % of reference (FTP, Threshold Pace, CSS, etc.).
            - **Reference types:** FTP, VO2MAX_POWER, THRESHOLD_PACE, VO2MAX_PACE, CSS, PACE_5K, PACE_10K, PACE_HALF_MARATHON, PACE_MARATHON, CUSTOM.

            ### Goal Tools (view athlete goals)
            - `listGoals(athleteId)` — list an athlete's race goals to understand their race calendar.""" + COMMON_RULES;

    private static final String ACTION_ZONE_PROMPT = """
            You are a zone system designer.
            Create exactly one zone system based on the user's description.
            Call createZoneSystem exactly ONCE using coachId = the userId from system context.
            No preamble. After the tool call: one-line confirmation only.""";

    private static final String ACTION_TRAINING_SESSION_PROMPT = """
            You are a workout designer for club sessions.
            Create exactly one training + optional club session based on the user's description.
            Call createTrainingWithClubSession exactly ONCE.
            Fixed context — read from system context and pass these values exactly as-is to the tool:
              userId, clubId, clubGroupId, coachGroupId
            No preamble. After the tool call: one-line confirmation only.""";

    private static final String GENERAL_PROMPT = """
            Role: Friendly Triathlon & Cycling Assistant.
            Goal: Answer general training questions, provide coaching advice, and help with non-specific queries.
            Keep responses concise and actionable.

            ## AVAILABLE TOOLS (read-only)
            - `getUserProfile(userId)` — user profile: FTP, CTL, ATL, TSB, role.
            - `getUserSchedule(userId, startDate, endDate)` — scheduled workouts in a date range.
            - `getRecentSessions(userId, limit)` — recent completed sessions with metrics.
            - `getCurrentDate()` — today's date, day of week, week boundaries.

            Use these tools to ground your advice in the user's actual data when relevant.""" + COMMON_RULES;

    // ── Beans ───────────────────────────────────────────────────────────

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient trainingCreationClient(AnthropicChatModel chatModel,
                                             ChatMemory chatMemory,
                                             ContextToolService contextToolService,
                                             TrainingToolService trainingToolService) {
        return ChatClient.builder(chatModel)
                .defaultSystem(TRAINING_CREATION_PROMPT)
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
                                       GoalToolService goalToolService) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SCHEDULING_PROMPT)
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, trainingToolService, coachToolService, goalToolService)
                .build();
    }

    @Bean
    public ChatClient analysisClient(AnthropicChatModel chatModel,
                                     ChatMemory chatMemory,
                                     ContextToolService contextToolService,
                                     HistoryToolService historyToolService,
                                     GoalToolService goalToolService) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ANALYSIS_PROMPT)
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
        return ChatClient.builder(chatModel)
                .defaultSystem(COACH_MANAGEMENT_PROMPT)
                .defaultOptions(sonnetOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, coachToolService, zoneToolService, goalToolService)
                .build();
    }

    @Bean
    public ChatClient generalClient(AnthropicChatModel chatModel,
                                    ChatMemory chatMemory,
                                    ContextToolService contextToolService,
                                    HistoryToolService historyToolService) {
        return ChatClient.builder(chatModel)
                .defaultSystem(GENERAL_PROMPT)
                .defaultOptions(haikuOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(contextToolService, historyToolService)
                .build();
    }

    @Bean
    public ChatClient actionZoneClient(AnthropicChatModel chatModel,
                                       ZoneToolService zoneToolService) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ACTION_ZONE_PROMPT)
                .defaultOptions(haikuActionOptions())
                .defaultTools(zoneToolService)
                .build();
    }

    @Bean
    public ChatClient actionTrainingSessionClient(AnthropicChatModel chatModel,
                                                  AIActionToolService aiActionToolService) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ACTION_TRAINING_SESSION_PROMPT)
                .defaultOptions(haikuActionOptions())
                .defaultTools(aiActionToolService)
                .build();
    }

    @Bean
    public ChatClient routerClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(HAIKU)
                        .temperature(0.0)
                        .maxTokens(20)
                        .build())
                .build();
    }

    @Bean
    public ChatClient plannerClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(HAIKU)
                        .temperature(0.0)
                        .maxTokens(512)
                        .build())
                .defaultSystem("""
                        Decompose the user request into atomic independent tasks.
                        Return ONLY a JSON array: [{"task":"...","agentType":"TRAINING_CREATION|SCHEDULING|ANALYSIS|COACH_MANAGEMENT|GENERAL"}]
                        Rules:
                        - If tasks depend on each other (e.g. create then schedule same workout), merge into ONE task string.
                        - If truly independent (e.g. create 20 different workouts), split into N tasks.
                        - If single/unclear: return single-element array.
                        - Return raw JSON only. No markdown, no explanation.""")
                .build();
    }

    // ── Options helpers ─────────────────────────────────────────────────

    private AnthropicChatOptions sonnetOptions() {
        return AnthropicChatOptions.builder()
                .model(SONNET)
                .temperature(0.7)
                .maxTokens(8192)
                .cacheOptions(cacheOptions())
                .build();
    }

    private AnthropicChatOptions haikuOptions() {
        return AnthropicChatOptions.builder()
                .model(HAIKU)
                .temperature(0.7)
                .maxTokens(2048)
                .cacheOptions(cacheOptions())
                .build();
    }

    private AnthropicChatOptions haikuActionOptions() {
        return AnthropicChatOptions.builder()
                .model(HAIKU)
                .temperature(0.3)
                .maxTokens(2048)
                .build();
    }

    private AnthropicCacheOptions cacheOptions() {
        return AnthropicCacheOptions.builder()
                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                .messageTypeTtl(Stream.of(MessageType.values())
                        .collect(Collectors.toMap(mt -> mt, _ -> AnthropicCacheTtl.ONE_HOUR, (m1, _) -> m1, HashMap::new)))
                .build();
    }
}

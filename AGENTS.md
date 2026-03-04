# AI Agent Refactor — Implementation Prompt

## Current state

- A single `AIService` uses two `ChatClient` beans (one for COACH, one for ATHLETE).
- All three tool services are loaded into both clients:
  `TrainingManagementService`, `CoachService`, `HistoryToolService`
- A single `/api/ai/chat` and `/api/ai/chat/stream` endpoint handles all requests.
- `ChatHistory` is stored in MongoDB (`ChatHistoryService`).
- `UserContext` (userId, role, ftp) is resolved by `UserContextResolver`.

## Goal

Refactor into dedicated specialist agents. Each agent:
- Has its own `ChatClient` bean with a focused system prompt and only the tools it needs.
- Is a Spring `@Service` with `chat()` and `chatStream()` methods matching the existing signatures.
- Shares the same `ChatHistoryService` and `UserContextResolver` infrastructure.
- Uses the same SSE streaming format (`status`/`content`/`conversation_id`/`error` events).

---

## Agents to create

### 1. TrainingCreationAgent
Purpose: Design and manage workout structures.
Tools: `TrainingManagementService` (create, update, delete, list trainings)
System prompt focus: Expert cycling/triathlon coach creating structured workouts.
  Know WorkoutBlock types (WARMUP, INTERVAL, STEADY, RAMP, COOLDOWN, FREE),
  power as % of FTP ({userFtp}W), durations in seconds, cadence targets.
  Always confirm before deleting. Suggest workout type and tags.
Route triggers: "create", "design", "make", "build", "edit", "update", "delete" + workout/session/training

### 2. SchedulingAgent
Purpose: Manage the training calendar and athlete workout assignments.
Tools: `CoachService` (assign, list athletes, view schedule, manage scheduled workouts)
System prompt focus: Training load planner. Knows periodization, weekly TSS targets,
  recovery principles. Uses today's date ({today}), user role ({userRole}).
  For coaches: can assign to specific athletes or tags.
Route triggers: "schedule", "assign", "plan week", "calendar", "when", "next session", "reschedule"

### 3. AnalysisAgent
Purpose: Interpret performance data, trends, and session results.
Tools: `HistoryToolService` (session history, PMC data, search sessions, public templates)
System prompt focus: Data analyst for endurance sports. Interprets CTL/ATL/TSB,
  IF, TSS, power curves. Explains trends in plain language.
  User FTP = {userFtp}W. Compares against historical baselines.
Route triggers: "analyse", "how am I", "fitness", "trend", "PMC", "history", "last session",
  "performance", "compare", "progress", "fatigue"

### 4. CoachManagementAgent (COACH role only)
Purpose: Manage athlete roster, tags, invite codes, shared training templates.
Tools: `CoachService` (athlete CRUD, tag management) + `HistoryToolService` (public templates)
System prompt focus: Coach administrator. Helps organize athlete groups, tag strategies,
  share templates. Knows roster size ({rosterSize}).
Route triggers: "athlete", "roster", "tag", "group", "invite", "share template", "my athletes"

### 5. GeneralAgent (fallback)
Purpose: Answer training theory questions, explain concepts — no tool calls needed.
Tools: none
System prompt focus: Expert endurance sports advisor. Knows physiology, nutrition,
  recovery, zone theory, periodization. Friendly and concise.
  User context: role={userRole}, FTP={userFtp}W.
Route triggers: everything else

---

## RouterService

Create a `RouterService` that:
- Uses a cheap/fast model (`claude-haiku-4-5-20251001`) with NO tools.
- Sends:
  - system: `"Classify the user message into exactly one of: TRAINING_CREATION | SCHEDULING | ANALYSIS | COACH_MANAGEMENT | GENERAL. Reply with only the label."`
  - user: the user's message
- Returns `AgentType` enum: `TRAINING_CREATION`, `SCHEDULING`, `ANALYSIS`, `COACH_MANAGEMENT`, `GENERAL`
- If user role is ATHLETE, never return `COACH_MANAGEMENT` (fall back to `GENERAL`).
- Cache the router `ChatClient` separately (no conversation memory needed).

---

## AIController changes

- Keep existing endpoints: `POST /api/ai/chat` and `POST /api/ai/chat/stream`
- Add optional field to request body: `"agentType"` (String, nullable)
  - If provided and valid → skip router, use that agent directly (frontend explicit selection)
  - If null → call `RouterService.classify(message, userRole)` → pick agent
- Inject all 5 agent services + `RouterService`
- Route to the correct agent's `chat()` or `chatStream()` method
- Include resolved `agentType` in the response (new SSE event `"agent"` at stream start, and field in sync response)

---

## AIConfig changes

- Add a `ChatClient` `@Bean` per agent (named: `trainingCreationClient`, `schedulingClient`,
  `analysisClient`, `coachManagementClient`, `generalClient`, `routerClient`)
- Each uses `builder.defaultSystem(...).defaultTools(...).build()`
- `routerClient` uses a separate `AnthropicChatModel` configured with `claude-haiku-4-5-20251001`
- `generalClient` also uses `claude-haiku-4-5-20251001` (no tools, cheaper)
- Keep the existing `@Primary` `chatClient` bean for backward compatibility during migration

---

## Common interface

Each agent service must implement:

```java
// com.koval.trainingplannerbackend.ai.agents.TrainingAgent
public interface TrainingAgent {
    AIService.ChatMessageResponse chat(String message, String userId, String historyId);
    AIService.StreamResponse chatStream(String message, String userId, String historyId);
}
```

Java package: `com.koval.trainingplannerbackend.ai.agents.*`

---

## ChatHistory changes

- Add field `lastAgentType: String` to `ChatHistory` MongoDB document
- Update `ChatHistoryService` to save the resolved agent type after each response

---

## Frontend changes (Angular)

- In `chat.service.ts`: add optional `agentType` field to the chat request body
- In `ai-chat-page` component: add an agent selector (pill tabs above the chat input):
  `[AUTO] [CREATE] [SCHEDULE] [ANALYSE] [COACH]`
  - `AUTO` = null (let router decide). Store selection in a `BehaviorSubject`.
- Display a small badge on each AI response showing which agent answered
  (use the new `"agent"` SSE event or the `agentType` field in sync response)

---

## Agent summary

| Agent                  | Tools                                          | Model              |
|------------------------|------------------------------------------------|--------------------|
| TrainingCreationAgent  | TrainingManagementService                      | claude-sonnet-4-6  |
| SchedulingAgent        | CoachService                                   | claude-sonnet-4-6  |
| AnalysisAgent          | HistoryToolService                             | claude-sonnet-4-6  |
| CoachManagementAgent   | CoachService + HistoryToolService              | claude-sonnet-4-6  |
| GeneralAgent           | none                                           | claude-haiku-4-5   |
| RouterService          | none                                           | claude-haiku-4-5   |

---

## Constraints

- Do NOT break existing `ChatHistoryService`, `UserContextResolver`, or SSE streaming format
- Prompt caching: each agent's system prompt + tools must be stable across calls
  (same text every time) so Anthropic cache hits correctly
- Keep `@Tool` annotations on existing service methods unchanged

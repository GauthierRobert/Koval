# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Koval Training Planner AI** is a full-stack application for cycling/triathlon athletes and coaches. It combines AI-powered workout planning (Spring AI + Anthropic Claude), Bluetooth-driven live training, multi-sport workout/plan/race/zone/club management, and an MCP server that exposes the same capabilities to external Claude clients.

Monorepo layout:
- `backend/` — Spring Boot 4.0.4, Java 25, MongoDB, Spring AI 2.0.0-M2 (Anthropic). Package root: `com.koval.trainingplannerbackend`.
- `frontend/` — Angular 21 (standalone components, lazy routes), TypeScript 5.9, RxJS, Vitest, Playwright.
- `skills/` — End-user skill markdown bundles distributed alongside the MCP connector (NOT developer skills).
- `openapi/` — Per-domain OpenAPI specs (auth, ai, clubs, coach, goals, groups, notifications, pacing, races, schedule, sessions, strava, trainings, zones).
- `docs/` — Design notes (club gazette plan, local GCS setup).
- `.claude/skills/` — Developer skills enforced when editing code in this repo (see "Required Skills" below).

## Required Skills

Editing code in this repo is governed by the skills in `.claude/skills/`. Invoke them when their scope matches:

- **`spring-boot-development`** — any Java backend code (controllers, services, repositories, models, DTOs, config). Enforces SOLID, feature-first packaging, constructor injection, record DTOs, centralized error handling, MongoDB best practices.
- **`angular-development`** — any frontend component/service/template. Enforces RxJS-first reactive patterns, `| async` in templates, file size limits, single responsibility.
- **`ui-ux-design`** — HTML/CSS for Angular. Enforces project's dark-first glassmorphism design language, spacing rhythm, accessibility.
- **`mcp-server-development`** — anything under `backend/.../mcp/` or new `@Tool` adapters. Enforces tool description quality, lean summaries, auth context, consistent patterns.

## Development Commands

### Local infra (docker-compose.yml)
Starts MongoDB (`27017`), RabbitMQ (`5672`/`15672`), Pub/Sub emulator (`8085`), and fake-gcs-server (`4443`):
```bash
docker compose up -d
```

### Backend
```bash
cd backend
mvn spring-boot:run                     # Run on :8080
mvn test                                # Unit + integration tests (Testcontainers Mongo — Docker required)
mvn test -Dtest=ClassName               # Single test class
mvn test -Dtest=ClassName#methodName    # Single test method
mvn -Pnative native:compile             # GraalVM native image (used by Dockerfile)
```

Tests run under the `test` Spring profile, which stubs Anthropic via `MockAIConfig` — no live API calls. Integration tests extend `BaseIntegrationTest` and pull config from `TestcontainersConfig`. Lombok is on the build path; ensure your IDE has the Lombok plugin.

### Frontend
```bash
cd frontend
npm start                               # ng serve on :4200 (runs prestart: scripts/set-env.js)
npm run build                           # Production build → dist/
npm test                                # Vitest (ng test)
npm run e2e                             # Playwright headless
npm run e2e:ui                          # Playwright UI mode
```

`scripts/set-env.js` reads `frontend/.env` (Firebase keys — see `.env.template`) and writes `src/environments/environment.ts` before build/serve.

### Required environment variables (backend)
`ANTHROPIC_API_KEY`, `JWT_SECRET`, `MONGODB_URI`, `ALLOWED_ORIGINS`, `STRAVA_CLIENT_ID/SECRET/REDIRECT_URI`, `GOOGLE_CLIENT_ID/SECRET/REDIRECT_URI`, `ADMIN_SECRET`, `FIREBASE_SERVICE_ACCOUNT_JSON`, `FIREBASE_PROJECT_ID`. Optional: Strava webhook tokens, Garmin OAuth, GCP project for Pub/Sub.

## Architecture

### Backend — feature-first packages (`com.koval.trainingplannerbackend`)

Top-level packages map to product domains, not technical layers:

`ai/` — Spring AI integration with multi-agent routing.
  - `AIService`, `AIController` (`/api/ai/chat`, `/api/ai/chat/stream` SSE), `ChatHistory`, `CompactingChatMemory`, `ConversationSummarizer`, `AiRateLimiter`, `UserContextResolver`, `ToolEventEmitter`.
  - `agents/` — **Router/specialist pattern**: `RouterService` classifies user intent (training-creation, scheduling, analysis, coach-management, club-management, race-completion, planner, action) and delegates to a `SpecialistAgentService` instance per `AgentType`. System prompts live in `src/main/resources/prompts/*.md` (per agent + `common-rules.md`).
  - `tools/` — `@Tool` adapters organized by domain: `training/`, `coach/`, `history/`, `plan/`, `race/`, `club/`, `zone/`, `goal/`, `action/`, `scheduling/`. These are what the chat-side AI sees.
  - `config/` — `AIConfig` wires `ChatClient` with Anthropic Claude Sonnet 4.6, `MessageChatMemoryAdvisor`, prompt caching (`AnthropicCacheOptions`), retry/backoff.
  - `toon/`, `logger/`, `action/` — output formatting, call logging, action tracking.

`mcp/` — Same capabilities re-exposed to **external** Claude clients (Claude Desktop / Claude.ai) via `spring-ai-starter-mcp-server-webmvc` at `/mcp/sse` (STREAMABLE protocol). One adapter class per domain: `McpTrainingTools`, `McpSchedulingTools`, `McpCoachTools`, `McpClubTools`, `McpGazetteTools`, `McpGoalTools`, `McpHistoryTools`, `McpPlanTools`, `McpProfileTools`, `McpRaceTools`, `McpZoneTools`, `McpAnalyticsTools`. `McpServerConfig` registers them. `mcp/render/` produces markdown reports (PMC, power curve, FRI, week schedule, session summary, volume, gazette PDFs).

`training/` — `Training` (polymorphic: `CyclingTraining`, `RunningTraining`, `SwimmingTraining`, `BrickTraining`), `WorkoutElement` (with `BlockType`: WARMUP / INTERVAL / STEADY / RAMP / FREE / COOLDOWN), `WorkoutElementFlattener`, `TrainingService`, `TrainingAccessService`, `TrainingController`. Sub-packages: `history/` (executed sessions, PMC, metrics), `metrics/` (TSS/IF/NP), `zone/` (zone systems per sport), `group/` (training groups), `received/` (Strava-imported sessions).

`coach/` — Coach↔athlete relationships. `ScheduledWorkout`, `ScheduleService`, `CoachService`, `InviteCode`, `CoachGroupService`. Roles enforced by `coachGuard` on the frontend and `@PreAuthorize` on the backend.

`plan/` — Multi-week training plans (`TrainingPlan` → `PlanWeek` → `PlanDay`), with status (DRAFT/ACTIVE/PAUSED/COMPLETED), progress tracking (`PlanProgress`), analytics (`PlanAnalytics`/`PlanWeekAnalytics`), activation, cloning.

`club/` — Group features. Sub-packages: `feed/` (SSE-driven activity feed; broker abstracted over RabbitMQ vs GCP Pub/Sub via `CLUB_FEED_BROKER_TYPE`), `gazette/` (publish PDF newsletters), `session/` + `recurring/` (club workouts), `invite/`, `membership/`, `stats/`, `activity/`.

`race/` — `Race`, `RaceService`, `WebSearchRaceService` (search public races), `RaceCompletionService`, `DistanceCategory`. `goal/` — `RaceGoal` linking races to plans.

`auth/` — Strava + Google OAuth, `User`/`UserRole` (ATHLETE/COACH), `JwtAuthenticationFilter`, `AccountLinkingService`, `CguConstants`, `SecurityUtils`.

`pacing/` — GPX parsing (`pacing/gpx/`) and pacing strategy DTOs.

`integration/`, `media/` (GCS uploads + Thumbnailator), `notification/` (Firebase push + in-app), `oauth/` (third-party OAuth client management), `chat/` (real-time DM/club chat with SSE), `skills/` (server-side end-user-skill packaging), `maintenance/`, `config/` (audit, exception handlers).

### Backend cross-cutting

- **Prompt caching**: `AnthropicCacheOptions` caches system prompts, tool definitions, and conversation history. ~90% input-token savings on multi-turn chats.
- **Chat memory**: persisted via `spring-ai-starter-model-chat-memory-repository-mongodb` with TTL (`CHAT_MEMORY_TTL_SECONDS`, default 90 days) + `CompactingChatMemory` for summarization on long conversations.
- **Native image**: GraalVM native build via `mvn -Pnative native:compile`. Reflection config at `backend/src/main/resources/META-INF/native-image/...` — keep in sync when moving classes between packages.
- **Brokers**: `application-prod.yml` switches the club feed broker to GCP Pub/Sub via `CLUB_FEED_BROKER_TYPE=pubsub`. Local dev uses RabbitMQ (autoconfigured-out by default — `RabbitAutoConfiguration` is excluded in `application.yml`).
- **Auth**: JWT (24h) issued after Strava/Google OAuth callback. `JwtAuthenticationFilter` resolves `User` and stamps `SecurityContext`. `SecurityUtils.currentUserId()` is the canonical way to get the caller.

### Frontend — `frontend/src/app/`

```
components/
├─ pages/        # Route components (lazy-loaded). One folder per top-level page.
├─ layout/       # sidebar, top-bar, settings, training-history, training-load-chart
└─ shared/       # Reusable UI: modals, charts, cards, koval-image, leaflet, skeletons
services/        # ~50 services — feature services + utility services + cache layers
guards/          # authGuard, coachGuard
interceptors/    # JWT, error, etc.
models/          # TS interfaces shared across services
utils/           # workout-notation parser, training math, parsers
```

Routing (`app.routes.ts`): all routes are `loadComponent: () => import(...)` (lazy) and gated by `authGuard` (and `coachGuard` for coach/AI-chat areas). Note path renaming: `/dashboard`, `/trainings`, `/trainings/new`, `/trainings/:id/edit`, `/active-session`, `/history`, `/calendar`, `/coach`, `/zones`, `/chat`, `/clubs`, `/plans`, `/races`, `/goals`, `/pmc`, `/analytics`, `/pacing`, `/onboarding`, `/oauth-clients`, `/auth/callback`. Legacy `/builder` redirects to `/trainings/new`.

State management: RxJS `BehaviorSubject` exposed as `observable$` from services. **Always use `| async` in templates — never manually subscribe to set component properties** (enforced by `angular-development` skill). SSE consumers exist for chat (`chat-sse.service.ts`), club feed (`club-feed-sse.service.ts`), and AI streaming.

Mock fallbacks: `auth.service.ts` and `training.service.ts` ship a mock COACH user + 3 sample workouts so the frontend works standalone if the backend is down.

PWA: Angular service worker (`ngsw-config.json`) + custom `custom-sw.js` for push notifications via Firebase. `pwa-install.service.ts` handles install prompts.

### Bluetooth (Web Bluetooth API)

`bluetooth.service.ts` + `bluetooth-parsers.util.ts` handle GATT services per Bluetooth SIG specs:
- `fitness_machine` — indoor trainer (Indoor Bike Data characteristic)
- `cycling_power` — power meters
- `heart_rate` — HR monitors
- `cycling_speed_and_cadence` — CSC

Simulation mode is built in for testing without devices. `workout-execution.service.ts` consumes the streams during a live session.

### MCP server vs end-user skills

Two separate "skills" concepts, do not confuse them:

- `.claude/skills/*/SKILL.md` — **developer skills** for Claude Code editing this repo. Required reading before touching the matching code area.
- `skills/koval-athlete/` and `skills/koval-coach/` — **end-user skills** distributed to Claude Desktop/Claude.ai users alongside the MCP connector. Two role-scoped bundles. Each has a `SKILL.md` router and a `resources/` folder containing per-workflow playbooks (analyze ride, form check, plan week, prep race, create/find workout, zone setup, onboarding profile for athletes; weekly review, athlete deep-dive, create/assign workout, build plan, club sessions, publish gazette for coaches). Build artifacts via `node skills/package-skills.mjs` → `skills/dist/koval-athlete.zip` and `skills/dist/koval-coach.zip`. The MCP connector itself is the Spring server at `/mcp/sse`.

### Data model conventions

- Power targets are `% of FTP` (athlete-stored). RAMP blocks use `powerStartPercent` + `powerEndPercent`; all other block types use `powerTargetPercent`.
- Durations are **seconds**.
- Sport types: `CYCLING`, `RUNNING`, `SWIMMING`, `BRICK` (multi-discipline). Polymorphic `Training` deserialization keyed on `sport`.
- Zones are stored per-athlete per-sport in `ZoneSystem`s (default plus custom). `ZoneClassificationService` maps measured power/HR/pace to zone labels.
- Workout notation parser (`workout-notation-parser.ts`) converts text like `4x(5min @ 105%, 3min @ 60%)` into `WorkoutElement[]`.

## Deployment

GitHub Actions (`.github/workflows/deploy.yml`) on push to `main`:
- Backend → Cloud Run via Artifact Registry (`europe-west1`), profile `prod`, secrets injected from Google Secret Manager.
- Frontend → Firebase Hosting (`frontend/dist/training-planner-frontend/browser`).

`backend/Dockerfile` builds a GraalVM native image. `cloudbuild.yaml` is an alternative GCB recipe.

## Conventions

- **Java**: feature-first packaging (do NOT split by `controllers/`/`services/`/`models/`); constructor injection (no `@Autowired` on fields); records for DTOs; `@PreAuthorize` for role checks; centralized exception handling under `config/exceptions/`.
- **Frontend**: standalone components only (no NgModules); Vitest, not Jasmine; Prettier 100-col + single quotes; `| async` everywhere; service observables end with `$`.
- **Tools**: every `@Tool` (Spring AI) and `@McpTool` adapter must have a clear, action-oriented description — these are read by Claude at runtime. See `mcp-server-development` skill.
- **Reflection config**: when moving classes between packages, update `backend/src/main/resources/META-INF/native-image/.../reflect-config.json` or the native build will break.
- **Prompts**: system prompts live in `backend/src/main/resources/prompts/*.md` and are loaded by `SpecialistAgentService`. Edit there, not in code.

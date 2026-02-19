# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Training Planner AI is a full-stack application for creating and executing cycling/triathlon training workouts. It features AI-powered workout generation using Spring AI with Anthropic Claude, Bluetooth device integration for live training sessions, and a coach-athlete relationship system with Strava OAuth authentication.

**Architecture**: Monorepo with separate frontend and backend directories.

- **Frontend**: Angular 21 (standalone components), TypeScript, RxJS
- **Backend**: Spring Boot 4.0.2, Java 25, MongoDB, Spring AI (2.0.0) with Anthropic

## Development Commands

### Backend (Spring Boot)
```bash
cd backend
mvn clean install          # Build the project
mvn spring-boot:run        # Start backend server (port 8080)
mvn test                   # Run tests (no tests currently exist)
```

### Frontend (Angular)
```bash
cd frontend
npm install                # Install dependencies
npm start                  # Start dev server (port 4200)
npm run build              # Production build
npm test                   # Run Vitest tests
npm run watch              # Watch mode build
```

### Running the Full Stack
1. Start MongoDB on localhost:27017
2. Set environment variables: `ANTHROPIC_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `JWT_SECRET`
3. Start backend: `cd backend && mvn spring-boot:run`
4. Start frontend: `cd frontend && npm start`
5. Access at http://localhost:4200

## Code Architecture

### Backend Structure

**Package Organization** (under `com.example.trainingplannerbackend`):
- `ai/`: Spring AI integration with Anthropic Claude Sonnet 4.5
  - `AIService`: Manages chat conversations with prompt caching and streaming support
  - `AIController`: REST endpoints at `/api/ai/*` (chat, streaming, history management)
  - `AIConfig`: Configures ChatClient with Claude Sonnet 4.5, prompt caching, and chat memory
  - `ChatHistory`: MongoDB document for conversation persistence
  - `ChatHistoryRepository`: MongoDB repository for chat history
  - `HistoryToolService`: @Tool methods for AI to access workout history

- `auth/`: Authentication and user management
  - `SecurityConfig`: Spring Security configuration with JWT
  - `StravaOAuthService`: Handles Strava OAuth2 flow
  - `AuthController`: Login/callback endpoints
  - `User`: MongoDB document with roles (ATHLETE/COACH)
  - `UserService`: User CRUD operations

- `training/`: Core workout management
  - `Training`: MongoDB document representing a workout plan
  - `WorkoutBlock`: Embedded document for workout segments (WARMUP, INTERVAL, STEADY, RAMP, FREE, COOLDOWN)
  - `TrainingManagementService`: CRUD operations exposed as AI tools via `@Tool` annotation
  - `TrainingController`: REST API at `/api/trainings`
  - `HistoryService`: Manages workout execution history

- `coach/`: Coach-athlete relationship features
  - `CoachService`: Assign workouts to athletes (also exposed as `@Tool` for AI)
  - `ScheduledWorkout`: MongoDB document linking athletes to assigned trainings
  - `CoachController`: REST API for coach operations

**AI Integration Pattern** (Claude Sonnet 4.5):
- Uses Spring AI's `ChatClient` with Anthropic Claude Sonnet 4.5
- **Prompt Caching**: Enabled via `AnthropicCacheOptions` for 90% cost reduction on repeated tokens
  - System messages and tools cached automatically
  - Conversation history caching for multi-turn chats
- **Chat Memory**: `MessageChatMemoryAdvisor` provides automatic conversation tracking
- **Streaming**: Supports SSE streaming via `Flux<String>` for real-time responses
- **Tool Access**: Three service categories exposed via `@Tool` annotations:
  - `TrainingManagementService`: CRUD operations (create, update, delete, list trainings)
  - `CoachService`: Coach operations (assign workouts, manage athletes, view schedules)
  - `HistoryToolService`: History access (workout history, search, public templates)
- System prompt includes `{userId}`, `{userRole}`, and `{userFtp}` for context-aware execution
- Usage metadata tracking: input/output tokens and cache performance metrics

### Frontend Structure

**Component Architecture** (under `src/app/components`):
- `workout-selection/`: Main dashboard, select from existing workouts
- `live-dashboard/`: Active training session with real-time metrics visualization
- `workout-history/`: Past workout sessions and analytics
- `calendar/`: Schedule view for planned workouts
- `coach-dashboard/`: Coach interface for managing athletes
- `ai-chat-page/`: Chat interface to generate workouts with AI
- `device-manager/`: Bluetooth device connection UI
- `auth/`: Login and OAuth callback components

**Services** (under `src/app/services`):
- `training.service.ts`: Manages workout data, chat messages, and FTP. Contains mock data fallback.
- `auth.service.ts`: JWT authentication with Strava OAuth. Falls back to mock user if backend unavailable.
- `bluetooth.service.ts`: Web Bluetooth API integration for fitness devices (trainers, heart rate monitors, power meters, cadence sensors). Includes simulation mode.
- `workout-execution.service.ts`: Manages active workout state during live sessions
- `calendar.service.ts`: Handles scheduled workout operations
- `coach.service.ts`: Coach-specific API calls
- `history.service.ts`: Workout history and analytics
- `export.service.ts`: Export workout data (FIT, TCX formats)
- `pip.service.ts`: Picture-in-picture mode for workouts

**State Management**:
- RxJS BehaviorSubjects for reactive state
- No external state library; services expose observables ending with `$`
- Example pattern: `private subject = new BehaviorSubject<T>(initial); public observable$ = subject.asObservable();`
- **IMPORTANT**: Always use `| async` pipe in templates to subscribe to observables. Never manually subscribe in components to set plain properties for template binding — use `Observable` + `| async` instead. This ensures proper change detection and automatic unsubscription.

**Routing**: Defined in `app.routes.ts`
- `/dashboard` → Workout selection
- `/active-session` → Live training session
- `/history` → Workout history
- `/calendar` → Calendar view
- `/coach` → Coach dashboard
- `/chat` → AI chat interface
- `/login` → Authentication
- `/auth/callback` → OAuth callback

### Data Models

**WorkoutBlock Types**:
- `WARMUP`: Gradual intensity increase
- `STEADY`: Constant power target
- `INTERVAL`: Repeated high-intensity efforts
- `RAMP`: Progressive power increase/decrease
- `COOLDOWN`: Recovery period
- `FREE`: Unstructured riding

**Key Fields**:
- Power targets expressed as percentage of FTP (Functional Threshold Power)
- Duration in seconds
- Optional cadence targets, repeats, labels
- For RAMP blocks: `powerStartPercent` and `powerEndPercent` instead of `powerTargetPercent`

### API Endpoints

Backend serves on `http://localhost:8080`:

**AI Endpoints:**
- `POST /api/ai/chat` - Send message to AI, returns response with usage metadata
- `POST /api/ai/chat/stream` - Streaming chat (SSE) for real-time responses
- `GET /api/ai/history` - List all chat histories for a user
- `GET /api/ai/history/{id}` - Get specific chat history
- `DELETE /api/ai/history/{id}` - Delete chat history

**Training Endpoints:**
- `GET /api/trainings` - List user's trainings
- `POST /api/trainings` - Create training (typically used by AI via tool)

**Auth Endpoints:**
- `GET /api/auth/strava` - Get Strava OAuth URL
- `GET /api/auth/strava/callback` - Handle OAuth callback
- `GET /api/auth/me` - Get current user info

**Coach Endpoints:**
- `POST /api/coach/assign` - Assign workout to athlete
- `GET /api/coach/athletes` - List coach's athletes

### Configuration Notes

**Backend** (`application.yml`):
- MongoDB defaults to `localhost:27017/training-planner`
- Spring AI Anthropic configuration:
  - Model: `claude-sonnet-4-5`
  - Temperature: 0.7
  - Max tokens: 4096
  - Retry: 3 attempts with exponential backoff
- Requires `ANTHROPIC_API_KEY` environment variable
- Spring AI version: 1.0.0-SNAPSHOT (requires Spring Snapshots repository)
- JWT expiration: 24 hours (86400000ms)

**Frontend**:
- Uses Angular standalone components (no NgModules)
- Vitest for testing (not Karma/Jasmine)
- Prettier configured: 100 char line width, single quotes
- Angular CLI version 21.1.2
- Target ES2022+ (TypeScript 5.9.2)

### Bluetooth Integration

The app connects to fitness devices via Web Bluetooth API:
- **Trainer**: `fitness_machine` service → indoor bike data
- **Heart Rate**: `heart_rate` service → HR measurements
- **Power Meter**: `cycling_power` service → power measurements
- **Cadence**: `cycling_speed_and_cadence` service → CSC measurements

Data parsing follows Bluetooth SIG specifications for characteristic value structures (flags, offsets, endianness).

Simulation mode available for testing without physical devices.

### Mock Data Fallback

Both frontend services (`AuthService`, `TrainingService`) include mock data:
- Frontend operates standalone if backend is unavailable
- Mock user has COACH role to enable all UI features
- Three sample workouts included: "FTP Booster - Over-Unders", "Sprints & Explosiveness", "Endurance with Ramps & Free Ride"

### Authentication Flow

1. User clicks "Login with Strava"
2. Frontend calls `GET /api/auth/strava` → receives OAuth URL
3. Redirect to Strava authorization
4. Strava redirects to `/auth/callback?code=...`
5. Frontend calls `GET /api/auth/strava/callback?code=...`
6. Backend exchanges code for Strava tokens, creates/updates User, returns JWT
7. Frontend stores JWT in localStorage, loads user profile

### AI Chat and Workout Generation Flow

**Standard Chat:**
1. User sends message in chat interface
2. Frontend: `POST /api/ai/chat` with `{ message: "...", chatHistoryId: "..." }`
3. Backend `AIService.chat()`:
   - Loads user context (role, FTP)
   - Retrieves or creates chat history
   - Calls Claude Sonnet 4.5 with:
     - System prompt (cached)
     - Tools: TrainingManagementService, CoachService, HistoryToolService (cached)
     - Conversation history (cached after first turn)
     - Chat memory advisor for automatic context
   - AI may invoke `@Tool` methods like `createTraining()` or `assignTraining()`
   - Saves conversation to MongoDB
   - Returns text response, chatHistoryId, and usage metadata
4. Frontend displays response and usage stats

**Streaming Chat:**
1. Frontend: `POST /api/ai/chat/stream` (same request format)
2. Backend returns `Flux<String>` as Server-Sent Events
3. Frontend receives and displays tokens in real-time
4. Full response saved to history when stream completes

**Caching Benefits:**
- First message: ~350 tokens input (system + tools + user message)
- Subsequent messages: ~50 tokens input + 300 cached tokens (90% cost reduction)
- Conversation history cached for multi-turn chats

**Tool Access:**
The AI has full access to:
- Training CRUD (create, update, delete, list)
- Workout history and analytics
- Coach operations (if user role is COACH)
- Public workout templates for inspiration
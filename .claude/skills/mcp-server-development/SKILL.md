---
name: mcp-server-development
description: Use when writing or modifying MCP (Model Context Protocol) server tools, adapters, configuration, or API key infrastructure. Enforces quality tool descriptions, lean summaries, proper auth context, and consistent patterns for exposing app capabilities to external AI clients (Claude Desktop, ChatGPT).
---

# MCP Server Development Standards

## Overview

The MCP server is embedded in the Spring Boot backend using `spring-ai-starter-mcp-server-webmvc`. It exposes the app's capabilities as MCP tools that external AI clients (Claude Desktop, ChatGPT) can call. Authentication uses personal API keys (`koval_...` prefix). Tool adapters live in `com.koval.trainingplannerbackend.mcp/` and delegate to existing business services.

## Architecture

```
External AI Client (Claude Desktop / ChatGPT)
    │
    ▼  MCP protocol over Streamable HTTP
┌─────────────────────────────────┐
│  Spring AI MCP Server (WebMVC)  │
│  /mcp/sse  /mcp/messages        │
├─────────────────────────────────┤
│  ApiKeyAuthFilter               │  ← Validates koval_... API keys
├─────────────────────────────────┤
│  McpServerConfig                │  ← Registers all tool providers
├─────────────────────────────────┤
│  Mcp*Tools (10 adapters)        │  ← Thin wrappers, delegate to services
├─────────────────────────────────┤
│  Business Services              │  ← TrainingService, CoachService, etc.
├─────────────────────────────────┤
│  MongoDB                        │
└─────────────────────────────────┘
```

### Key Packages

| Package | Purpose |
|---------|---------|
| `mcp/` | MCP tool adapters + server config |
| `auth/apikey/` | API key document, repo, service, controller, filter |
| `ai/tools/` | Internal AI tools (use ToolContext — NOT for MCP) |

## MCP Tool Adapter Pattern

MCP tool adapters are **thin wrappers** that delegate to existing business services. They do NOT contain business logic.

### Rules

1. **Delegate to business services, not AI tool services** — AI tools depend on `ToolContext`; MCP tools use `SecurityUtils.getCurrentUserId()` directly
2. **One adapter class per domain** — `McpTrainingTools`, `McpSchedulingTools`, etc.
3. **`@Service` + `@Tool` annotations** — same as internal AI tools, but without `ToolContext` parameters
4. **Auth via SecurityContext** — `SecurityUtils.getCurrentUserId()` is populated by `ApiKeyAuthFilter`
5. **No ToolEventEmitter calls** — MCP tools don't emit SSE tool events
6. **Return lean summary records** — never expose full MongoDB documents with internal fields

```java
// Good: thin adapter delegating to business service
@Service
public class McpTrainingTools {
    private final TrainingService trainingService;

    @Tool(description = "List the user's training workouts with pagination.")
    public List<McpTrainingSummary> listTrainings(
            @ToolParam(description = "Max results (default 15)") Integer limit,
            @ToolParam(description = "Skip count (default 0)") Integer offset) {
        String userId = SecurityUtils.getCurrentUserId();
        // ... delegate to trainingService
    }
}

// Bad: duplicating business logic
@Tool(description = "List trainings")
public List<Training> listTrainings() {
    return trainingRepository.findByCreatedBy(userId); // Don't query repo directly
}
```

## Tool Description Quality

MCP tool descriptions are consumed by external LLMs that **don't have system prompt context**. They must be self-contained.

### Rules

| Do | Don't |
|----|-------|
| Action-oriented: "List the user's training workouts" | Vague: "Get trainings" |
| Include data context: "Returns metrics like TSS, IF, duration" | Bare minimum: "Returns sessions" |
| Explain domain terms: "TSS (Training Stress Score)" | Assume knowledge: "Returns TSS" |
| State constraints: "Max 50 items per page" | Omit limits |
| State prerequisites: "The training must exist first" | Skip workflow hints |
| Note role requirements: "Requires COACH role" | Hide access rules |

### Parameter Descriptions

- Always include format: `"Date (YYYY-MM-DD)"`, `"Time (HH:mm)"`
- Include valid values for enums: `"Sport: CYCLING, RUNNING, SWIMMING, or BRICK"`
- Note optional vs required: `"Optional notes for the athletes"`
- Include defaults: `"Maximum number to return (default 15)"`

```java
// Good
@ToolParam(description = "Start date inclusive (YYYY-MM-DD)") LocalDate from

// Bad
@ToolParam(description = "Start date") LocalDate from
```

## Return Type Patterns

### Summary Records

Define lean summary records as **inner records** of the tool adapter class:

```java
public record McpTrainingSummary(String id, String title, String sport, String type,
                                  Integer durationMinutes, Integer estimatedTss,
                                  String description) {
    public static McpTrainingSummary from(Training t) {
        return new McpTrainingSummary(
                t.getId(), t.getTitle(),
                t.getSportType() != null ? t.getSportType().name() : null,
                // ...
        );
    }
}
```

### Rules

- **Static `from()` factory** for entity → summary mapping
- **Enums as Strings** — external clients can't deserialize Java enums
- **Null-safe** — always handle nullable fields with ternary or Optional
- **No internal fields** — never expose `createdBy`, `keyHash`, internal timestamps
- **Include enough context** — MCP consumers can't look at the frontend for details

### Error Returns

For validation errors, return a plain String starting with `"Error: "`:

```java
if (title == null || title.isBlank()) return "Error: title is required.";
```

For domain errors, let exceptions propagate — `GlobalExceptionHandler` returns structured error responses.

## API Key Infrastructure

### Security Model

- Keys have `koval_` prefix + 40 random hex chars
- Only the SHA-256 hash is stored in MongoDB (never plaintext)
- First 14 chars stored as `prefix` for display: `koval_abcdef12...`
- Max 5 active keys per user
- `lastUsedAt` updated on each validation

### ApiKeyAuthFilter

- Runs **before** `JwtAuthenticationFilter` in the security chain
- Only activates when token starts with `koval_`
- Sets `SecurityContextHolder` with user's ID and role
- Non-API-key tokens fall through to JWT filter (backwards compatible)

### Key Management Endpoints

All under `/api/auth/api-keys` (JWT-authenticated, not API-key-authenticated):

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/api-keys` | Generate new key (returns plaintext once) |
| GET | `/api/auth/api-keys` | List keys (prefix + name only) |
| DELETE | `/api/auth/api-keys/{id}` | Revoke key |

## Configuration

### application.yml

```yaml
spring:
  ai:
    mcp:
      server:
        name: koval-training-planner
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages
```

### McpServerConfig

Registers all tool adapters via a single `ToolCallbackProvider` bean:

```java
@Bean
public ToolCallbackProvider mcpTools(McpTrainingTools training, ...) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(training, scheduling, history, ...)
            .build();
}
```

## Adding a New MCP Tool

1. Create `Mcp{Domain}Tools.java` in `mcp/` package
2. Inject business service(s) via constructor
3. Add `@Tool` methods with rich descriptions
4. Use `SecurityUtils.getCurrentUserId()` for auth
5. Return summary record (inner record with `from()` factory)
6. Register in `McpServerConfig.mcpTools()` bean

## Client Configuration

### Claude Desktop

```json
{
  "mcpServers": {
    "koval": {
      "url": "https://your-domain.com/mcp/sse",
      "headers": {
        "Authorization": "Bearer koval_<your-api-key>"
      }
    }
  }
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|-----------|
| Use `ToolContext` in MCP tools | Use `SecurityUtils.getCurrentUserId()` |
| Call `ToolEventEmitter` | Skip — MCP protocol handles tool results |
| Return raw MongoDB documents | Return summary records with `from()` |
| Put business logic in adapters | Delegate to existing services |
| Use `@Autowired` | Constructor injection with `final` fields |
| Copy code from AI tool services | Delegate to the same business services they use |
| Generic descriptions: "Get data" | Rich descriptions with context and constraints |

---
name: spring-boot-development
description: Use when writing or modifying Java backend code — controllers, services, repositories, models, DTOs, configuration. Enforces SOLID principles, feature-first packaging, constructor injection, record-based DTOs, centralized error handling, and MongoDB best practices.
---

# Spring Boot Backend Standards

## Overview

Spring Boot 4 + Java 25 + MongoDB. Feature-first package structure, constructor injection, record DTOs, centralized exception handling. Every class has one reason to change.

## SOLID Principles

### Single Responsibility

Each class does **one thing**:

| Class Type | Responsibility | NOT Responsible For |
|------------|---------------|---------------------|
| Controller | HTTP mapping, input validation, response shaping | Business logic, DB queries |
| Service | Business logic, orchestration, domain rules | HTTP concerns, direct repository queries from controllers |
| Repository | Data access, custom queries | Business rules, validation |
| Model/Document | Data structure, field constraints | Business logic, persistence logic |
| DTO (Record) | Data transfer shape, mapping | Behavior, side effects |
| Config | Bean wiring, external config binding | Business logic |

**Red flags that SRP is violated:**
- Service with `@Value` for HTTP-related config (that's a controller concern)
- Controller calling repository directly (bypass service layer)
- Model with methods that call external services
- Service doing both read and write for unrelated domains

### Open/Closed

Extend behavior through composition, not modification:

```java
// Good: new agent type doesn't modify existing router
public interface TrainingAgent {
    AgentResponse handle(AgentRequest request);
}

// Bad: switch statement in router grows with every new type
switch(type) { case "training": ...; case "coaching": ...; }
```

### Liskov Substitution

Subtypes must be substitutable. The Training hierarchy uses `@JsonTypeInfo` correctly — any `Training` subtype works where `Training` is expected.

### Interface Segregation

Don't force clients to depend on methods they don't use. If a service has 20+ methods, split it by consumer:

```java
// Bad: one giant service
class TrainingService { create, update, delete, list, search, enrich, export, metrics... }

// Good: split by concern
class TrainingService { create, update, delete, list }       // CRUD
class TrainingMetricsService { enrich, computeZones, powerCurve }  // Analytics
class TrainingAccessService { verifyAccess, enrichForUser }   // Authorization
```

### Dependency Inversion

Depend on abstractions when behavior varies. Use interfaces for:
- AI agents (different strategies per domain)
- Feed brokers (RabbitMQ vs Pub/Sub)
- OAuth providers (Strava, Google, Garmin)

Don't over-abstract: if there's only one implementation and no foreseeable second, skip the interface.

## Package Structure

Feature-first, not layer-first:

```
com.koval.trainingplannerbackend/
  training/              # Core domain
    model/               # MongoDB documents
    dto/                 # Request/response records
    TrainingController
    TrainingService
    TrainingRepository
    metrics/             # Sub-domain
    zone/                # Sub-domain
  coach/                 # Coaching domain
  club/                  # Club domain
    membership/
    session/
    feed/
    dto/
  auth/                  # Auth domain
  ai/                    # AI agent orchestration
    agents/
    config/
    tools/
  integration/           # Third-party APIs
    strava/
    garmin/
    zwift/
  config/                # Cross-cutting config
    exceptions/
    GlobalExceptionHandler
    SecurityConfig
```

### Rules

- **Group by domain, not by layer** — never create `controllers/`, `services/`, `repositories/` packages
- **Sub-packages for sub-domains** — `club/session/`, `club/feed/`, `ai/tools/training/`
- **Keep related files together** — controller, service, repository, model for one domain live in one package
- **DTOs in `dto/` sub-package** when there are 3+ DTOs, otherwise inline

## Constructor Injection

**Always.** No `@Autowired` on fields. No setter injection.

```java
// Required pattern
@Service
public class CoachService {
    private final UserRepository userRepository;
    private final GroupService groupService;

    public CoachService(UserRepository userRepository, GroupService groupService) {
        this.userRepository = userRepository;
        this.groupService = groupService;
    }
}
```

**Why:** Makes dependencies explicit, enables immutability (`final` fields), simplifies testing.

**If constructor has 6+ parameters,** the class likely violates SRP — split it.

## Record-Based DTOs

All request and response DTOs must be Java records:

```java
// Request
public record CreateGroupRequest(
    @NotBlank String name,
    String description
) {}

// Response — with static factory
public record AthleteResponse(
    String id,
    String displayName,
    Integer ftp,
    List<String> groups
) {
    public static AthleteResponse from(User user, List<String> groups) {
        return new AthleteResponse(user.getId(), user.getDisplayName(), user.getFtp(), groups);
    }
}
```

### Rules

- **Records for DTOs** — immutable, no boilerplate
- **Static `from()` factory** for mapping entity → response
- **Jakarta validation annotations** on request record fields (`@NotBlank`, `@Min`, `@Valid`)
- **Never expose MongoDB documents directly** as API responses — always map through a DTO
- **Never put business logic in records** — they are data carriers

## Controller Conventions

```java
@RestController
@RequestMapping("/api/trainings")
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @PostMapping
    public ResponseEntity<TrainingResponse> create(@Valid @RequestBody CreateTrainingRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Training created = trainingService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(TrainingResponse.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrainingResponse> getById(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        Training training = trainingService.getById(id, userId);
        return ResponseEntity.ok(TrainingResponse.from(training));
    }
}
```

### Rules

- **Return `ResponseEntity<T>`** — explicit status codes
- **`@Valid` on request bodies** — triggers Jakarta validation
- **`SecurityUtils.getCurrentUserId()`** for auth context — never trust client-sent user IDs
- **Controller does:** validation, auth context, call service, map to response DTO
- **Controller does NOT:** contain business logic, call repositories, catch exceptions (GlobalExceptionHandler does that)
- **One controller per domain** — max ~10 endpoints per controller. Split if growing beyond.

## Service Layer

### Rules

- **All business logic lives in services** — never in controllers or repositories
- **Services are transactional units** — `@Transactional` on write methods that touch multiple collections
- **Services call repositories and other services** — composition over inheritance
- **Throw domain exceptions** — `ResourceNotFoundException`, `ValidationException`, `ForbiddenOperationException`
- **Never catch and swallow exceptions silently** — log or rethrow
- **Enrichment pattern:** services provide `enrich*` methods for adding computed data to responses

```java
@Service
public class TrainingService {

    public Training getById(String id, String userId) {
        Training training = trainingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Training", id));
        accessService.verifyAccess(userId, training);
        return metricsService.enrich(training, userId);
    }
}
```

## Exception Handling

### Custom Exception Hierarchy

```
RuntimeException
├── ResourceNotFoundException (404)  — entity not found by ID
├── ValidationException (400)        — business rule violation
├── ForbiddenOperationException (403) — unauthorized action
└── RateLimitException (429)          — throttling
```

### Rules

- **Throw from services** — never from controllers
- **Include error code** — machine-readable string (e.g. `TRAINING_NOT_FOUND`, `INSUFFICIENT_PERMISSIONS`)
- **Include helpful message** — human-readable context
- **GlobalExceptionHandler catches all** — returns consistent `ErrorResponse` record
- **Never expose stack traces** to clients — log server-side, return safe message
- **Never use generic `RuntimeException`** — always use a domain-specific exception

```java
// Good
throw new ResourceNotFoundException("Training", trainingId);
throw new ValidationException("INVALID_BLOCK_TYPE", "Block type RAMP requires start and end intensity");

// Bad
throw new RuntimeException("not found");
throw new Exception("something went wrong");
```

## MongoDB Best Practices

### Document Design

- **`@Document`** annotation on all root entities
- **`@Id`** on the ID field (String, auto-generated)
- **`@Indexed`** on frequently queried fields (`createdBy`, `clubId`, `scheduledDate`)
- **Embedded documents** for tightly coupled sub-objects (WorkoutElement inside Training)
- **References (ID strings)** for loosely coupled relationships (userId in Training)
- **UTC-only datetimes** — store as `Instant` or use custom LocalDate converters

### Repository Pattern

```java
public interface TrainingRepository extends MongoRepository<Training, String> {
    List<Training> findByCreatedBy(String userId);
    List<Training> findByClubIdsIn(Collection<String> clubIds);
    @Query("{ 'createdBy': ?0, 'sportType': ?1 }")
    List<Training> findByUserAndSport(String userId, String sportType);
}
```

- **Spring Data derived queries** for simple lookups
- **`@Query`** for complex MongoDB queries
- **Never use MongoTemplate in services** unless the query is too complex for repository methods
- **Pagination** via `Pageable` parameter for list endpoints that could return many results

## Security

- **JWT authentication** via `JwtAuthenticationFilter`
- **`SecurityUtils.getCurrentUserId()`** — the only way to get the authenticated user
- **Verify access in service layer** — not just in controllers. Services are called by AI tools too.
- **Never trust client input for authorization** — always derive user identity from JWT
- **Sensitive config via environment variables** — `${ENV_VAR:default}` pattern in YAML. Always provide defaults for non-critical config.

## File Size Limit: 400 Lines

- **Service > 400 lines:** Extract sub-services by concern (metrics, access, enrichment)
- **Controller > 300 lines:** Split by sub-domain or extract endpoint groups
- **Model > 200 lines:** Extract embedded documents or value objects into separate files
- **Config > 150 lines:** Split by feature (SecurityConfig, MongoConfig, AIConfig)

## Naming Conventions

| Component | Pattern | Example |
|-----------|---------|---------|
| Controller | `{Domain}Controller` | `TrainingController` |
| Service | `{Domain}Service` or `{Domain}{Concern}Service` | `TrainingService`, `TrainingMetricsService` |
| Repository | `{Domain}Repository` | `TrainingRepository` |
| Document | `{Domain}` (noun) | `Training`, `User`, `Club` |
| Request DTO | `{Action}{Domain}Request` | `CreateGroupRequest`, `AssignmentRequest` |
| Response DTO | `{Domain}Response` | `AthleteResponse`, `ClubDetailResponse` |
| Exception | `{Reason}Exception` | `ResourceNotFoundException` |
| Config | `{Feature}Config` | `SecurityConfig`, `AIConfig` |
| Methods | camelCase, verb-first | `createTraining()`, `verifyAccess()`, `enrichForUser()` |

## Testing

- **Integration tests** with Testcontainers MongoDB — test full request/response cycle
- **`BaseIntegrationTest`** provides MockMvc, JWT helpers, DB cleanup
- **Test profile** (`application-test.yml`) with in-memory/containerized dependencies
- **Test each endpoint:** happy path + validation errors + authorization failures + not-found
- **Use `@Test` method names** that describe the scenario: `createTraining_returnsCreated()`, `getById_notFound_returns404()`
- **Never mock the database** in integration tests — use Testcontainers

```java
@Test
void createTraining_validInput_returnsCreated() throws Exception {
    mockMvc.perform(post("/api/trainings")
            .header("Authorization", bearer(athleteToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_TRAINING_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Sweet Spot Intervals"));
}

@Test
void getById_otherUser_returnsForbidden() throws Exception {
    mockMvc.perform(get("/api/trainings/" + trainingId)
            .header("Authorization", bearer(otherUserToken)))
        .andExpect(status().isForbidden());
}
```

## Anti-Patterns

| Don't | Do Instead |
|-------|-----------|
| `@Autowired` field injection | Constructor injection with `final` fields |
| Return entity directly from controller | Map to response DTO record |
| Catch and swallow exceptions | Let GlobalExceptionHandler handle them |
| Business logic in controller | Move to service layer |
| `throw new RuntimeException(msg)` | Use domain-specific exception with error code |
| Layer-first packages (`controllers/`, `services/`) | Feature-first (`training/`, `coach/`, `club/`) |
| Hardcode secrets in YAML | Use `${ENV_VAR:default}` pattern |
| `@Autowired` on test fields | Use constructor injection or `@MockBean` |
| Giant god-service with 30 methods | Split by concern (CRUD, metrics, access, enrichment) |
| Mutable DTOs with setters | Immutable Java records |
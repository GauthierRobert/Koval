---
name: testing-standards
description: Use when writing or modifying tests — JUnit integration tests extending BaseIntegrationTest, Testcontainers Mongo setup, Vitest specs for Angular, or Playwright e2e flows. Enforces test isolation, fast feedback, and the project's existing test patterns instead of reinventing them.
---

# Testing Standards

## Overview

Backend: JUnit 5 + Spring Boot Test + Testcontainers Mongo. Frontend: Vitest + Playwright. The Anthropic API is **stubbed** in tests via `MockAIConfig` under the `test` Spring profile — never reach live AI in tests. Reuse existing harnesses; do not spin up new ones.

## Backend tests

### Integration tests

Always extend `BaseIntegrationTest` for tests that need MongoDB, Spring context, or `@Autowired` beans. Config comes from `TestcontainersConfig` — do not re-declare `@Testcontainers` or boot a second Mongo container.

```java
class FooFlowIntegrationTest extends BaseIntegrationTest {
    @Autowired private FooService fooService;

    @Test
    void scenario_givenX_thenY() { /* ... */ }
}
```

**Required:**
- Test class name ends in `IntegrationTest`
- Method names: `behavior_givenCondition_expectedOutcome` (snake-ish, descriptive)
- Reset state between tests via `@BeforeEach` cleanup or `@DirtiesContext` only when absolutely needed (slow)

### Unit tests

Pure logic only — no Spring context. Use plain JUnit + Mockito. Class name ends in `Test` (no `Integration`).

### Profile

All tests run under `@ActiveProfiles("test")` (inherited from `BaseIntegrationTest`). `MockAIConfig` provides a no-op `ChatClient` bean. If a test needs AI behavior, mock the **service** that wraps the `ChatClient`, not the client itself.

### Don't

- Don't add `@SpringBootTest` if you can extend `BaseIntegrationTest`
- Don't spin up a second `MongoDBContainer` — reuse the shared one
- Don't hit external HTTP (Strava, Google, Anthropic) — use `WireMock` or a stub
- Don't use `Thread.sleep(...)` to wait for async work — use `Awaitility`
- Don't commit `@Disabled` without a TODO comment explaining why

### Running

```bash
mvn test                                    # All tests (Docker required)
mvn test -Dtest=FooFlowIntegrationTest      # Single class
mvn test -Dtest=FooFlowIntegrationTest#myMethod   # Single method
```

If Docker isn't running, integration tests fail at container startup with a clear error — not your test's fault.

## Frontend tests

### Vitest unit tests

- File: `*.spec.ts` next to source
- Run: `npm test` from `frontend/`
- **Not** Jasmine, **not** Karma — Vitest API (`describe`, `it`, `expect`, `vi.fn()`)
- Use Angular's `TestBed` for component tests; standalone components, no NgModules

```typescript
import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

describe('FooService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));
  it('computes the right thing', () => {
    const svc = TestBed.inject(FooService);
    expect(svc.compute(2)).toBe(4);
  });
});
```

### Service tests

When a service exposes a `BehaviorSubject`, test the **observable**, not the subject. Use `firstValueFrom(service.foo$)` to assert emissions.

### Playwright e2e

- `npm run e2e` (headless) / `npm run e2e:ui` (debug)
- Tests live in `frontend/e2e/`
- Use semantic role selectors (`getByRole`, `getByLabel`) — avoid CSS selectors tied to styling
- Don't rely on real backend; mock `/api/**` with `page.route(...)` unless explicitly running an integration scenario

## Anti-patterns

| Don't | Do Instead |
|---|---|
| Boot a fresh Spring context per test class | Extend `BaseIntegrationTest`, share context |
| Mock the `ChatClient` directly | Mock the AI service that wraps it |
| `Thread.sleep(2000)` for async | `Awaitility.await().atMost(...)` |
| Snapshot tests for HTML output | Assert semantic structure (roles, text content) |
| `it.only` / `fdescribe` committed | Use `--filter` flag locally |
| Live HTTP in tests | WireMock / `page.route()` |
| Tests that depend on test order | Each test self-contained, idempotent |
| Asserting against `BehaviorSubject.value` | Subscribe via `firstValueFrom` and assert the emission |

# Alias Resolver and Agent Context Broadening — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Claude resolve user-typed names (e.g. "Alice", "Vélo Paris") to anonymization aliases without asking for MongoDB IDs, while keeping athlete names out of the system prompt.

**Architecture:** Add a new `ResolverToolService` exposing `@Tool` methods (`findAthleteByName`, `findClubByName`, `findGroupByName`) that Claude calls on demand. Broaden the agent-type filter in `BaseAgentService.systemContext()` so coach-management and scheduling agents also see the clubs list. Wire the resolver into the relevant Haiku clients.

**Tech Stack:** Java 25, Spring Boot 4.0.2, Spring AI 1.0.0-SNAPSHOT (`@Tool`, `@ToolParam`, `ToolContext`), JUnit 5, Mockito, Lombok.

---

## File Structure

- **Modify** `backend/src/main/java/com/koval/trainingplannerbackend/ai/agents/BaseAgentService.java` — loosen the agent-type conditions around athletes/groups/clubs blocks in `systemContext()`.
- **Modify** `backend/src/main/java/com/koval/trainingplannerbackend/ai/config/AIHaikuConfig.java` — register `ResolverToolService` on `coachManagementClient`, `clubManagementClient`, `schedulingClient`.
- **Create** `backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java` — the new tool service.
- **Create** `backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java` — unit tests.

Single-responsibility: the resolver file does name→alias mapping only. Matching logic is private to the file. No shared util package needed.

---

## Task 1: ResolverToolService — `findAthleteByName`

**Files:**
- Create: `backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java`
- Test: `backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java`

This task builds the full `findAthleteByName` method through successive TDD loops (one loop per behavior). The file is created in step 1 with just enough scaffolding to run the first test.

### Step 1.1 — Write the first failing test (exact match)

- [ ] Create the test file with this content:

```java
package com.koval.trainingplannerbackend.ai.tools.resolver;

import com.koval.trainingplannerbackend.ai.anonymization.AnonymizationContext;
import com.koval.trainingplannerbackend.ai.anonymization.AnonymizationService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupService;
import com.koval.trainingplannerbackend.coach.CoachGroupService;
import com.koval.trainingplannerbackend.training.group.Group;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResolverToolServiceTest {

    @Mock private CoachGroupService coachGroupService;
    @Mock private ClubService clubService;
    @Mock private ClubGroupService clubGroupService;

    private ResolverToolService service;
    private AnonymizationContext anonCtx;

    @BeforeEach
    void setUp() {
        service = new ResolverToolService(coachGroupService, clubService, clubGroupService);
        anonCtx = new AnonymizationContext();
    }

    private ToolContext ctxFor(String coachId) {
        return new ToolContext(Map.of(
                SecurityUtils.USER_ID_KEY, coachId,
                AnonymizationService.ANONYMIZATION_CTX_KEY, anonCtx
        ));
    }

    private User athlete(String id, String name) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        return u;
    }

    @Nested
    class FindAthleteByName {

        @Test
        void exactMatchReturnsAlias() {
            when(coachGroupService.getCoachAthletes("coach-1"))
                    .thenReturn(List.of(athlete("user-a", "Alice"), athlete("user-b", "Bob")));

            Object result = service.findAthleteByName("Alice", ctxFor("coach-1"));

            assertEquals(new ResolverToolService.ResolvedAlias("Athlete-1"), result);
        }
    }
}
```

### Step 1.2 — Run the test and verify it fails

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: FAIL with compile error — `ResolverToolService` does not exist.

### Step 1.3 — Create the production file with minimal implementation

- [ ] Create `backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java`:

```java
package com.koval.trainingplannerbackend.ai.tools.resolver;

import com.koval.trainingplannerbackend.ai.anonymization.AnonymizationContext;
import com.koval.trainingplannerbackend.ai.anonymization.AnonymizationService;
import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.club.ClubService;
import com.koval.trainingplannerbackend.club.dto.ClubSummaryResponse;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupService;
import com.koval.trainingplannerbackend.coach.CoachGroupService;
import com.koval.trainingplannerbackend.training.group.Group;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * AI-facing tool service that maps user-typed names or fuzzy matches
 * to existing anonymization aliases, so Claude never has to ask for raw IDs.
 *
 * The resolver never returns display names — only aliases.
 */
@Service
public class ResolverToolService {

    public record ResolvedAlias(String alias) {}
    public record ResolvedCandidates(List<String> candidates) {}

    private final CoachGroupService coachGroupService;
    private final ClubService clubService;
    private final ClubGroupService clubGroupService;

    public ResolverToolService(CoachGroupService coachGroupService,
                               ClubService clubService,
                               ClubGroupService clubGroupService) {
        this.coachGroupService = coachGroupService;
        this.clubService = clubService;
        this.clubGroupService = clubGroupService;
    }

    @Tool(description = """
            Resolve an athlete alias from a user-typed name, partial name, or alias.
            Returns {\"alias\": \"Athlete-N\"} for a single match, {\"candidates\": [...]} for multiple matches,
            or an error string. Call this before assignTraining when the user refers to an athlete by name.""")
    public Object findAthleteByName(@ToolParam(description = "Athlete name, partial name, or existing alias") String name,
                                     ToolContext context) {
        List<User> athletes = coachGroupService.getCoachAthletes(SecurityUtils.getUserId(context));
        AnonymizationContext anonCtx = AnonymizationService.fromToolContext(context.getContext());
        return resolveOne(name, athletes, User::getDisplayName, u -> anonCtx.anonymizeAthlete(u.getId()), "athlete");
    }

    private <T> Object resolveOne(String query, List<T> items,
                                   Function<T, String> nameFn, Function<T, String> aliasFn,
                                   String kind) {
        List<T> matches = new ArrayList<>();
        for (T item : items) {
            if (nameFn.apply(item).equalsIgnoreCase(query)) matches.add(item);
        }
        if (matches.size() == 1) return new ResolvedAlias(aliasFn.apply(matches.get(0)));
        return "No " + kind + " match.";
    }
}
```

### Step 1.4 — Run the test and verify it passes

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS.

### Step 1.5 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java \
        backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "feat(ai): add ResolverToolService.findAthleteByName with exact match"
```

### Step 1.6 — Add failing test for case-insensitive substring match

- [ ] Append a test method inside `class FindAthleteByName`:

```java
@Test
void substringMatchReturnsAlias() {
    when(coachGroupService.getCoachAthletes("coach-1"))
            .thenReturn(List.of(athlete("user-a", "Alice Dupont"), athlete("user-b", "Bob Martin")));

    Object result = service.findAthleteByName("alice", ctxFor("coach-1"));

    assertEquals(new ResolverToolService.ResolvedAlias("Athlete-1"), result);
}
```

### Step 1.7 — Run and verify FAIL

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest#FindAthleteByName test`
Expected: FAIL — `"alice"` is not an exact match for `"Alice Dupont"`.

### Step 1.8 — Update `resolveOne` to fall back to substring

- [ ] Replace the body of `resolveOne` in `ResolverToolService.java` with:

```java
private <T> Object resolveOne(String query, List<T> items,
                               Function<T, String> nameFn, Function<T, String> aliasFn,
                               String kind) {
    String q = query.toLowerCase();
    List<T> exact = items.stream().filter(i -> nameFn.apply(i).equalsIgnoreCase(query)).toList();
    if (!exact.isEmpty()) return finalize(exact, aliasFn, kind);

    List<T> substring = items.stream()
            .filter(i -> nameFn.apply(i).toLowerCase().contains(q)).toList();
    if (!substring.isEmpty()) return finalize(substring, aliasFn, kind);

    return "No " + kind + " match.";
}

private <T> Object finalize(List<T> matches, Function<T, String> aliasFn, String kind) {
    if (matches.size() == 1) return new ResolvedAlias(aliasFn.apply(matches.get(0)));
    List<String> aliases = matches.stream().map(aliasFn).toList();
    return new ResolvedCandidates(aliases);
}
```

### Step 1.9 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS (both existing tests).

### Step 1.10 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java \
        backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "feat(ai): case-insensitive substring fallback in resolver"
```

### Step 1.11 — Add failing test for Levenshtein-1 typo

- [ ] Append inside `class FindAthleteByName`:

```java
@Test
void levenshteinOneTypoMatches() {
    when(coachGroupService.getCoachAthletes("coach-1"))
            .thenReturn(List.of(athlete("user-a", "Alice"), athlete("user-b", "Bob")));

    Object result = service.findAthleteByName("Alise", ctxFor("coach-1")); // s→c typo

    assertEquals(new ResolverToolService.ResolvedAlias("Athlete-1"), result);
}
```

### Step 1.12 — Run and verify FAIL

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest#FindAthleteByName test`
Expected: FAIL — `"Alise"` has no exact or substring match for `"Alice"`.

### Step 1.13 — Add Levenshtein fallback

- [ ] In `ResolverToolService.java`, add this helper method:

```java
private static int levenshtein(String a, String b) {
    int n = a.length(), m = b.length();
    int[][] dp = new int[n + 1][m + 1];
    for (int i = 0; i <= n; i++) dp[i][0] = i;
    for (int j = 0; j <= m; j++) dp[0][j] = j;
    for (int i = 1; i <= n; i++) {
        for (int j = 1; j <= m; j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
        }
    }
    return dp[n][m];
}
```

- [ ] Extend `resolveOne` to add a Levenshtein pass before the final "no match":

```java
private <T> Object resolveOne(String query, List<T> items,
                               Function<T, String> nameFn, Function<T, String> aliasFn,
                               String kind) {
    String q = query.toLowerCase();
    List<T> exact = items.stream().filter(i -> nameFn.apply(i).equalsIgnoreCase(query)).toList();
    if (!exact.isEmpty()) return finalize(exact, aliasFn, kind);

    List<T> substring = items.stream()
            .filter(i -> nameFn.apply(i).toLowerCase().contains(q)).toList();
    if (!substring.isEmpty()) return finalize(substring, aliasFn, kind);

    List<T> fuzzy = items.stream()
            .filter(i -> levenshtein(nameFn.apply(i).toLowerCase(), q) <= 1).toList();
    if (!fuzzy.isEmpty()) return finalize(fuzzy, aliasFn, kind);

    return "No " + kind + " match.";
}
```

### Step 1.14 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS (three tests).

### Step 1.15 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java \
        backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "feat(ai): Levenshtein-1 typo tolerance in resolver"
```

### Step 1.16 — Add failing test for multiple matches

- [ ] Append inside `class FindAthleteByName`:

```java
@Test
void multipleMatchesReturnsCandidates() {
    when(coachGroupService.getCoachAthletes("coach-1"))
            .thenReturn(List.of(athlete("user-a", "Alice Dupont"), athlete("user-b", "Alice Martin")));

    Object result = service.findAthleteByName("Alice", ctxFor("coach-1"));

    assertEquals(new ResolverToolService.ResolvedCandidates(List.of("Athlete-1", "Athlete-2")), result);
}
```

### Step 1.17 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS — `finalize()` already returns `ResolvedCandidates` for `size > 1`.

### Step 1.18 — Commit

```bash
git add backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "test(ai): cover multiple-match candidate list in resolver"
```

### Step 1.19 — Add failing test for no match (error message lists known aliases)

- [ ] Append inside `class FindAthleteByName`:

```java
@Test
void noMatchReturnsErrorListingKnownAliases() {
    when(coachGroupService.getCoachAthletes("coach-1"))
            .thenReturn(List.of(athlete("user-a", "Alice"), athlete("user-b", "Bob")));

    Object result = service.findAthleteByName("Zoe", ctxFor("coach-1"));

    assertTrue(result instanceof String);
    String msg = (String) result;
    assertTrue(msg.contains("No athlete match"), msg);
    assertTrue(msg.contains("Athlete-1") && msg.contains("Athlete-2"), msg);
}
```

### Step 1.20 — Run and verify FAIL

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: FAIL — current no-match message is `"No athlete match."` with no aliases listed.

### Step 1.21 — Update no-match branch to list aliases

- [ ] Replace the final `return` in `resolveOne` with a helper call:

```java
private <T> Object resolveOne(String query, List<T> items,
                               Function<T, String> nameFn, Function<T, String> aliasFn,
                               String kind) {
    String q = query.toLowerCase();
    List<T> exact = items.stream().filter(i -> nameFn.apply(i).equalsIgnoreCase(query)).toList();
    if (!exact.isEmpty()) return finalize(exact, aliasFn, kind);

    List<T> substring = items.stream()
            .filter(i -> nameFn.apply(i).toLowerCase().contains(q)).toList();
    if (!substring.isEmpty()) return finalize(substring, aliasFn, kind);

    List<T> fuzzy = items.stream()
            .filter(i -> levenshtein(nameFn.apply(i).toLowerCase(), q) <= 1).toList();
    if (!fuzzy.isEmpty()) return finalize(fuzzy, aliasFn, kind);

    List<String> known = items.stream().map(aliasFn).toList();
    return "No " + kind + " match for '" + query + "'. Known aliases: " + known + ".";
}
```

### Step 1.22 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS (five tests).

### Step 1.23 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java \
        backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "feat(ai): no-match error lists known aliases"
```

### Step 1.24 — Add failing test for alias passthrough

- [ ] Append inside `class FindAthleteByName`:

```java
@Test
void existingAliasIsReturnedUnchanged() {
    // anonCtx has already minted Athlete-1 for user-a via some earlier call
    anonCtx.anonymizeAthlete("user-a");

    Object result = service.findAthleteByName("Athlete-1", ctxFor("coach-1"));

    assertEquals(new ResolverToolService.ResolvedAlias("Athlete-1"), result);
    verify(coachGroupService, never()).getCoachAthletes(anyString());
}
```

### Step 1.25 — Run and verify FAIL

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: FAIL — current impl always calls `coachGroupService.getCoachAthletes`.

### Step 1.26 — Add alias-passthrough short-circuit at the top of `findAthleteByName`

- [ ] Replace `findAthleteByName` in `ResolverToolService.java` with:

```java
@Tool(description = """
        Resolve an athlete alias from a user-typed name, partial name, or existing alias.
        Returns {\"alias\": \"Athlete-N\"} for a single match, {\"candidates\": [...]} for multiple matches,
        or an error string. Call this before assignTraining when the user refers to an athlete by name.""")
public Object findAthleteByName(@ToolParam(description = "Athlete name, partial name, or existing alias") String name,
                                 ToolContext context) {
    String err = validateInput(name, context);
    if (err != null) return err;

    AnonymizationContext anonCtx = AnonymizationService.fromToolContext(context.getContext());
    if (name.startsWith("Athlete-") && anonCtx != null && !anonCtx.deAnonymize(name).equals(name)) {
        return new ResolvedAlias(name);
    }

    List<User> athletes = coachGroupService.getCoachAthletes(SecurityUtils.getUserId(context));
    return resolveOne(name, athletes, User::getDisplayName, u -> anonCtx.anonymizeAthlete(u.getId()), "athlete");
}

private static String validateInput(String name, ToolContext context) {
    if (name == null || name.isBlank()) return "Error: name is required.";
    if (SecurityUtils.getUserId(context) == null) return "Error: missing user context.";
    return null;
}
```

Notes:
- `anonCtx.deAnonymize(name).equals(name)` returns `true` when the alias is **unknown** (pass-through behavior documented in `AnonymizationContext`). We check the negative — unequal means it *was* a known alias.
- `validateInput` handles the blank-input and missing-context cases that later tests will rely on.

### Step 1.27 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS (six tests).

### Step 1.28 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java \
        backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "feat(ai): pass through known aliases in findAthleteByName"
```

### Step 1.29 — Add failing tests for input validation

- [ ] Append inside `class FindAthleteByName`:

```java
@Test
void blankNameReturnsError() {
    Object result = service.findAthleteByName("", ctxFor("coach-1"));
    assertEquals("Error: name is required.", result);
}

@Test
void missingUserContextReturnsError() {
    ToolContext ctx = new ToolContext(Map.of(
            AnonymizationService.ANONYMIZATION_CTX_KEY, anonCtx
    ));
    Object result = service.findAthleteByName("Alice", ctx);
    assertEquals("Error: missing user context.", result);
}
```

### Step 1.30 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS (eight tests). These behaviors were introduced with `validateInput` in step 1.26.

### Step 1.31 — Commit

```bash
git add backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "test(ai): cover input validation in findAthleteByName"
```

---

## Task 2: ResolverToolService — `findClubByName`

**Files:**
- Modify: `backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java`
- Modify: `backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java`

### Step 2.1 — Add failing tests

- [ ] Append to the test file, right after the closing brace of `class FindAthleteByName`:

```java
@Nested
class FindClubByName {

    private ClubSummaryResponse club(String id, String name) {
        return new ClubSummaryResponse(id, name, null, null, null, null);
    }

    @Test
    void exactMatchReturnsAlias() {
        when(clubService.getUserClubs("user-1"))
                .thenReturn(List.of(club("club-a", "Vélo Paris"), club("club-b", "Run NYC")));

        Object result = service.findClubByName("Vélo Paris", ctxFor("user-1"));

        assertEquals(new ResolverToolService.ResolvedAlias("Club-1"), result);
    }

    @Test
    void caseInsensitiveSubstringMatches() {
        when(clubService.getUserClubs("user-1"))
                .thenReturn(List.of(club("club-a", "Vélo Paris")));

        Object result = service.findClubByName("paris", ctxFor("user-1"));

        assertEquals(new ResolverToolService.ResolvedAlias("Club-1"), result);
    }

    @Test
    void noMatchListsKnownClubAliases() {
        when(clubService.getUserClubs("user-1"))
                .thenReturn(List.of(club("club-a", "Vélo Paris")));

        Object result = service.findClubByName("Rome", ctxFor("user-1"));

        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("Club-1"));
    }

    @Test
    void blankNameReturnsError() {
        assertEquals("Error: name is required.", service.findClubByName("", ctxFor("user-1")));
    }
}
```

Note: `ClubSummaryResponse` is a six-field record `(id, name, description, logoUrl, visibility, membershipStatus)` — only `id` and `name` matter for these tests; the rest are `null`.

### Step 2.2 — Run and verify FAIL

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: FAIL — `findClubByName` does not exist.

### Step 2.3 — Implement `findClubByName`

- [ ] Add inside `ResolverToolService.java`, after `findAthleteByName`:

```java
@Tool(description = """
        Resolve a club alias from a user-typed club name or existing alias.
        Returns {\"alias\": \"Club-N\"} for a single match, {\"candidates\": [...]} for multiple matches,
        or an error string.""")
public Object findClubByName(@ToolParam(description = "Club name or existing alias") String name,
                              ToolContext context) {
    String err = validateInput(name, context);
    if (err != null) return err;

    AnonymizationContext anonCtx = AnonymizationService.fromToolContext(context.getContext());
    if (name.startsWith("Club-") && anonCtx != null && !anonCtx.deAnonymize(name).equals(name)) {
        return new ResolvedAlias(name);
    }

    List<ClubSummaryResponse> clubs = clubService.getUserClubs(SecurityUtils.getUserId(context));
    return resolveOne(name, clubs, ClubSummaryResponse::name, c -> anonCtx.anonymizeClub(c.id()), "club");
}
```

### Step 2.4 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS — all twelve tests.

### Step 2.5 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java \
        backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "feat(ai): add ResolverToolService.findClubByName"
```

---

## Task 3: ResolverToolService — `findGroupByName` (merges coach + club groups)

**Files:**
- Modify: `backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java`
- Modify: `backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java`

### Step 3.1 — Add failing tests

- [ ] Append to the test file, after `class FindClubByName`:

```java
@Nested
class FindGroupByName {

    private Group coachGroup(String id, String name) {
        Group g = new Group();
        g.setId(id);
        g.setName(name);
        return g;
    }

    private ClubGroup clubGroup(String id, String name) {
        ClubGroup g = new ClubGroup();
        g.setId(id);
        g.setName(name);
        return g;
    }

    @Test
    void matchesCoachGroup() {
        when(coachGroupService.getAthleteGroupsForCoach("coach-1"))
                .thenReturn(List.of(coachGroup("g-a", "Advanced"), coachGroup("g-b", "Beginner")));
        when(clubService.getUserClubs("coach-1")).thenReturn(List.of());

        Object result = service.findGroupByName("advanced", ctxFor("coach-1"));

        assertEquals(new ResolverToolService.ResolvedAlias("Group-1"), result);
    }

    @Test
    void matchesClubGroupFromAnyUserClub() {
        when(coachGroupService.getAthleteGroupsForCoach("coach-1")).thenReturn(List.of());
        when(clubService.getUserClubs("coach-1"))
                .thenReturn(List.of(new ClubSummaryResponse("club-a", "Vélo Paris", null, null, null, null)));
        when(clubGroupService.listGroups("coach-1", "club-a"))
                .thenReturn(List.of(clubGroup("cg-a", "Elite")));

        Object result = service.findGroupByName("Elite", ctxFor("coach-1"));

        assertEquals(new ResolverToolService.ResolvedAlias("Group-1"), result);
    }

    @Test
    void mergesFromBothSources() {
        when(coachGroupService.getAthleteGroupsForCoach("coach-1"))
                .thenReturn(List.of(coachGroup("g-a", "Advanced")));
        when(clubService.getUserClubs("coach-1"))
                .thenReturn(List.of(new ClubSummaryResponse("club-a", "Vélo Paris", null, null, null, null)));
        when(clubGroupService.listGroups("coach-1", "club-a"))
                .thenReturn(List.of(clubGroup("cg-a", "Advanced")));

        Object result = service.findGroupByName("Advanced", ctxFor("coach-1"));

        assertTrue(result instanceof ResolverToolService.ResolvedCandidates);
        ResolverToolService.ResolvedCandidates cand = (ResolverToolService.ResolvedCandidates) result;
        assertEquals(2, cand.candidates().size());
    }
}
```

The `ClubSummaryResponse` constructor takes six args `(id, name, description, logoUrl, visibility, membershipStatus)` — only `id` and `name` are set; the rest are `null`.

### Step 3.2 — Run and verify FAIL

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: FAIL — `findGroupByName` does not exist.

### Step 3.3 — Implement `findGroupByName`

- [ ] Add inside `ResolverToolService.java`, after `findClubByName`. This method collects groups from both sources into two parallel lists (names + aliases) and runs the same matching logic over them:

```java
@Tool(description = """
        Resolve a group alias from a user-typed group name. Searches both the coach's athlete groups
        and the groups of any club the user belongs to.
        Returns {\"alias\": \"Group-N\"} for a single match, {\"candidates\": [...]} for multiple matches,
        or an error string.""")
public Object findGroupByName(@ToolParam(description = "Group name or existing alias") String name,
                               ToolContext context) {
    String err = validateInput(name, context);
    if (err != null) return err;

    AnonymizationContext anonCtx = AnonymizationService.fromToolContext(context.getContext());
    if (name.startsWith("Group-") && anonCtx != null && !anonCtx.deAnonymize(name).equals(name)) {
        return new ResolvedAlias(name);
    }

    String userId = SecurityUtils.getUserId(context);
    record NamedId(String id, String name) {}
    List<NamedId> items = new ArrayList<>();
    for (Group g : coachGroupService.getAthleteGroupsForCoach(userId)) {
        items.add(new NamedId(g.getId(), g.getName()));
    }
    for (ClubSummaryResponse c : clubService.getUserClubs(userId)) {
        for (ClubGroup cg : clubGroupService.listGroups(userId, c.id())) {
            items.add(new NamedId(cg.getId(), cg.getName()));
        }
    }
    return resolveOne(name, items, NamedId::name, ni -> anonCtx.anonymizeGroup(ni.id()), "group");
}
```

### Step 3.4 — Run and verify PASS

Run: `cd backend && mvn -Dtest=ResolverToolServiceTest test`
Expected: PASS — all fifteen tests.

### Step 3.5 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java \
        backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java
git commit -m "feat(ai): add ResolverToolService.findGroupByName merging coach and club groups"
```

---

## Task 4: Broaden context injection in `BaseAgentService`

**Files:**
- Modify: `backend/src/main/java/com/koval/trainingplannerbackend/ai/agents/BaseAgentService.java:170-201`

No new test — the change is declarative and guarded by existing integration tests (which currently pass with the narrower conditions; they must still pass with the wider ones).

### Step 4.1 — Apply the change

- [ ] In `BaseAgentService.java`, replace the current athletes/groups/clubs blocks (lines ~167–201) with:

```java
// Athletes (shown where an agent may reference individual athletes) — anonymized aliases
AgentType agent = getAgentType();
boolean showRoster = agent == AgentType.COACH_MANAGEMENT
        || agent == AgentType.SCHEDULING
        || agent == AgentType.CLUB_MANAGEMENT;

if (!ctx.athletes().isEmpty() && showRoster) {
    sb.append("\n\nAthletes:");
    for (var a : ctx.athletes()) {
        String alias = anonCtx.anonymizeAthlete(a.id());
        sb.append("\n- ").append(alias);
    }
}

// Groups (coach-management, scheduling, club-management) — anonymized
if (!ctx.athleteGroups().isEmpty() && showRoster) {
    sb.append("\n\nGroups:");
    for (var g : ctx.athleteGroups()) {
        String alias = anonCtx.anonymizeGroup(g.id());
        sb.append("\n- ").append(alias).append(':').append(g.name());
    }
}

// Clubs with groups (shown wherever the coach may reference a club) — anonymized IDs
boolean showClubs = agent == AgentType.CLUB_MANAGEMENT
        || agent == AgentType.COACH_MANAGEMENT
        || agent == AgentType.SCHEDULING;

if (!ctx.clubs().isEmpty() && showClubs) {
    sb.append("\n\nClubs:");
    for (ClubContext c : ctx.clubs()) {
        String clubAlias = anonCtx.anonymizeClub(c.id());
        sb.append("\n- ").append(clubAlias).append(":\"").append(c.name()).append('"');
        if (!c.groups().isEmpty()) {
            String groups = c.groups().stream()
                    .map(g -> anonCtx.anonymizeGroup(g.id()) + ":" + g.name())
                    .collect(Collectors.joining(","));
            sb.append(" groups:[").append(groups).append(']');
        }
    }
}
```

### Step 4.2 — Build and run all existing tests

Run: `cd backend && mvn test -DfailIfNoTests=false`
Expected: PASS — all existing tests including `AnonymizationContextTest`, `ResolverToolServiceTest`, and any integration tests still green.

### Step 4.3 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/agents/BaseAgentService.java
git commit -m "feat(ai): broaden athletes/groups/clubs context injection across coach agents"
```

---

## Task 5: Wire `ResolverToolService` into Haiku clients

**Files:**
- Modify: `backend/src/main/java/com/koval/trainingplannerbackend/ai/config/AIHaikuConfig.java`

### Step 5.1 — Add the `ResolverToolService` import to `AIHaikuConfig.java`

- [ ] At the top of `AIHaikuConfig.java`, add (alphabetically, next to other `ai.tools.*` imports):

```java
import com.koval.trainingplannerbackend.ai.tools.resolver.ResolverToolService;
```

### Step 5.2 — Add the resolver to `coachManagementClient`

- [ ] Find the `coachManagementClient` bean (around line 78). Add `ResolverToolService resolverToolService` to its parameter list and include it in `wrapTools(...)`:

```java
@Bean
public ChatClient coachManagementClient(AnthropicChatModel chatModel,
                                        ChatMemory chatMemory,
                                        CoachToolService coachToolService,
                                        ZoneToolService zoneToolService,
                                        GoalToolService goalToolService,
                                        ResolverToolService resolverToolService) {
    return withLogging(ChatClient.builder(chatModel))
            .defaultSystem(agentPrompt("coach-management"))
            .defaultOptions(haikuOptions())
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).scheduler(Schedulers.boundedElastic()).build())
            .defaultToolCallbacks(wrapTools(coachToolService, zoneToolService, goalToolService, resolverToolService))
            .build();
}
```

### Step 5.3 — Add the resolver to `clubManagementClient`

- [ ] In the same file, update `clubManagementClient` (around line 92):

```java
@Bean
public ChatClient clubManagementClient(AnthropicChatModel chatModel,
                                        ChatMemory chatMemory,
                                        ClubToolService clubToolService,
                                        TrainingToolService trainingToolService,
                                        ResolverToolService resolverToolService) {
    return withLogging(ChatClient.builder(chatModel))
            .defaultSystem(agentPrompt("club-management"))
            .defaultOptions(haikuOptions())
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).scheduler(Schedulers.boundedElastic()).build())
            .defaultToolCallbacks(wrapTools(clubToolService, trainingToolService, resolverToolService))
            .build();
}
```

### Step 5.4 — Add the resolver to `schedulingClient`

- [ ] Update `schedulingClient` (around line 50):

```java
@Bean
public ChatClient schedulingClient(AnthropicChatModel chatModel,
                                   ChatMemory chatMemory,
                                   SchedulingToolService schedulingToolService,
                                   CoachToolService coachToolService,
                                   GoalToolService goalToolService,
                                   RaceToolService raceToolService,
                                   ResolverToolService resolverToolService) {
    return withLogging(ChatClient.builder(chatModel))
            .defaultSystem(agentPrompt("scheduling"))
            .defaultOptions(haikuOptions())
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).scheduler(Schedulers.boundedElastic()).build())
            .defaultToolCallbacks(wrapTools(schedulingToolService, coachToolService, goalToolService, raceToolService, resolverToolService))
            .build();
}
```

### Step 5.5 — Build the backend to verify wiring

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS.

### Step 5.6 — Run the full test suite

Run: `cd backend && mvn test -DfailIfNoTests=false`
Expected: PASS — no regressions.

### Step 5.7 — Commit

```bash
git add backend/src/main/java/com/koval/trainingplannerbackend/ai/config/AIHaikuConfig.java
git commit -m "feat(ai): wire ResolverToolService into coach/club/scheduling Haiku clients"
```

---

## Task 6: Manual end-to-end verification

No code change. This is a gated sanity check before considering the work done.

### Step 6.1 — Prerequisites

- [ ] Ensure `ANTHROPIC_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `JWT_SECRET` are set.
- [ ] MongoDB running on `localhost:27017`.
- [ ] Seed data: one coach user with role `COACH`, at least two athletes with display names (e.g. "Alice Dupont", "Bob Martin"), at least one club (e.g. "Vélo Paris").

### Step 6.2 — Start the stack

Run (in two terminals):
```bash
cd backend && mvn spring-boot:run
cd frontend && npm start
```

### Step 6.3 — Scenario A: assign by name

- [ ] Log in as the coach. Open the AI chat.
- [ ] Send: *"Assign the FTP Booster workout to Alice on Friday."*
- [ ] Expected: Claude does NOT ask *"What is Alice's user ID?"*. It calls `findAthleteByName`, then `assignTraining`, and confirms assignment for Alice.

### Step 6.4 — Scenario B: link to club by name

- [ ] Send: *"Create a threshold workout and link it to our Vélo Paris Tuesday session at 18:00."*
- [ ] Expected: Claude does NOT ask for the club ID. It uses the club alias already in the system prompt (or calls `findClubByName` if the router picked an agent without clubs listed), creates the training and the session, and links them.

### Step 6.5 — Scenario C: ambiguous name

- [ ] Seed a second athlete also named "Alice" (e.g. "Alice Martin") in the same coach's roster.
- [ ] Send: *"Assign the VO2 workout to Alice on Saturday."*
- [ ] Expected: Claude reports multiple matches and asks the coach to clarify which Alice.

### Step 6.6 — Scenario D: no match

- [ ] Send: *"Assign the recovery ride to Zoe."*
- [ ] Expected: Claude says no match was found and lists the coach's known athletes (by alias or by asking for the name, depending on response).

### Step 6.7 — Inspect logs (optional)

- [ ] Check backend logs — confirm `findAthleteByName` / `findClubByName` tool invocations appear, and confirm the de-anonymization layer maps aliases to real MongoDB IDs before hitting `CoachService.assignTraining` / `ClubSessionService`.

### Step 6.8 — If all scenarios pass, tag the work done

```bash
git log --oneline -n 15
```
Confirm the commits from Tasks 1–5 are on `main` (or the feature branch) and the branch builds green.

---

## Self-Review Checklist (completed by plan author)

- **Spec coverage:** Tasks 1–3 implement the three `@Tool` methods from the spec. Task 4 implements the broadened context injection. Task 5 implements the tool wiring. Task 6 covers the manual end-to-end test plan.
- **Placeholder scan:** No TBD/TODO. Every code block is complete.
- **Type consistency:** `ResolvedAlias(String alias)` and `ResolvedCandidates(List<String> candidates)` are declared in Task 1 step 1.3 and referenced consistently in Tasks 1–3. `findAthleteByName`, `findClubByName`, `findGroupByName` names are used consistently in tests, impl, and spec.
- **Scope check:** Single implementation plan — yes. All changes are in the AI module.

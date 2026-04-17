# Alias Resolver and Agent Context Broadening — Design

**Date:** 2026-04-17
**Status:** Approved (pending spec review)
**Author:** GAR + Claude

## Problem

Coaches using the AI assistant hit two friction points:

1. When a coach asks *"create a workout and link it to our club session"*, Claude asks *"What is the club ID?"*.
2. When a coach asks *"assign this workout to Alice"*, Claude asks *"What is the athlete's user ID?"*.

Both symptoms come from the **anonymization layer**, which is correct behavior under GDPR (the user is in the EU). Specifically:

- `BaseAgentService.systemContext()` (lines 170 & 189) injects the `Athletes:` list only for `COACH_MANAGEMENT` + `SCHEDULING` agents, and the `Clubs:` list only for `CLUB_MANAGEMENT`. Other agents have no knowledge of the user's clubs/athletes.
- Athletes are injected as alias-only (e.g. `Athlete-1`), with display names intentionally stripped (line 165) to avoid leaking PII to Anthropic. Claude therefore cannot match a name the user types (e.g. "Alice") to an alias.
- Clubs are already injected with their name (line 193: `Club-1:"Vélo Paris"`). Organization names are treated as non-individual PII and are acceptable.

## Goal

Make both flows work end-to-end without asking for IDs, while keeping **zero athlete names on the wire from the backend** (names may still appear in user-typed messages — we're on the Pragmatic tier, not Strict).

## Non-Goals

- Strict-tier user-message sanitization (pre-processing typed names into aliases before they reach Claude).
- Changing the `TRAINING_CREATION` agent — it's a pure workout builder and does not handle assignment or club-linking.
- Fuzzy matching beyond Levenshtein distance 1. Larger typos will fall through to Claude asking the user to clarify.

## Solution

Three coordinated changes.

### 1. Broaden context injection

**File:** `backend/src/main/java/com/koval/trainingplannerbackend/ai/agents/BaseAgentService.java`

Loosen the agent-type conditions around the athlete/group/club blocks in `systemContext()`:

| Block | Today | After |
|---|---|---|
| `Athletes:` (aliases only) | `COACH_MANAGEMENT`, `SCHEDULING` | `COACH_MANAGEMENT`, `SCHEDULING`, `CLUB_MANAGEMENT` |
| `Groups:` (alias:name) | `COACH_MANAGEMENT`, `SCHEDULING` | `COACH_MANAGEMENT`, `SCHEDULING`, `CLUB_MANAGEMENT` |
| `Clubs:` (alias:name + groups) | `CLUB_MANAGEMENT` | `CLUB_MANAGEMENT`, `COACH_MANAGEMENT`, `SCHEDULING` |

Athletes remain alias-only — no display names added.

### 2. New `ResolverToolService`

**File:** `backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java` *(new, ~120 LOC)*

Exposes three `@Tool` methods that Claude can call to map a user-typed name to an existing anonymization alias:

```java
@Tool(description = "Resolve an athlete alias from a name or fuzzy match.")
Object findAthleteByName(@ToolParam(description = "Name, partial name, or alias") String name,
                         ToolContext context);

@Tool(description = "Resolve a club alias from a name.")
Object findClubByName(@ToolParam(description = "Club name") String name, ToolContext context);

@Tool(description = "Resolve a group alias from a name.")
Object findGroupByName(@ToolParam(description = "Group name") String name, ToolContext context);
```

**Matching algorithm (in order of preference):**
1. If `name` is already a known alias (e.g. `Athlete-3`) → return as-is.
2. Exact, case-insensitive match on display name.
3. Case-insensitive substring match.
4. Levenshtein distance ≤ 1 against each candidate name.

**Return shapes:**
- Single match: `{"alias": "Athlete-3"}`
- Multiple matches: `{"candidates": ["Athlete-3", "Athlete-7"]}` — aliases only, no names
- No match: `{"error": "No athlete found matching 'Alice'. Known aliases: [Athlete-1, Athlete-2, Athlete-3]"}`

**Authorization:** all lookups are scoped to the calling coach via `SecurityUtils.getUserId(context)`. Athletes are fetched via `CoachGroupService.getCoachAthletes(coachId)`. Clubs via `ClubService.getUserClubs(userId)`. Groups are fetched from **both** sources and merged: `CoachGroupService.getAthleteGroupsForCoach(coachId)` (coach-owned athlete groups) and `ClubGroupService.listGroups(userId, clubId)` for each of the user's clubs. Both group kinds share the same alias namespace in `AnonymizationContext.anonymizeGroup(...)`, so the caller does not need to distinguish.

**Anonymization:** the resolver **does not** accept or return display names in any response. The `name` parameter may contain PII (user-typed), but any data the resolver *returns* is alias-only. The tool reuses the existing `AnonymizationContext` from `AnonymizationService.fromToolContext(...)`, calling `anonymizeAthlete/Group/Club` for each real ID it finds — which returns the already-mapped alias if one exists, or mints a new one.

### 3. Tool wiring

**File:** `backend/src/main/java/com/koval/trainingplannerbackend/ai/config/AIHaikuConfig.java`

Add `ResolverToolService` to three existing client beans:

- `coachManagementClient` — joins `coachToolService, zoneToolService, goalToolService`
- `clubManagementClient` — joins `clubToolService, trainingToolService`
- `schedulingClient` — joins `schedulingToolService, coachToolService, goalToolService, raceToolService`

`TRAINING_CREATION`, `ANALYSIS`, and `GENERAL` do not get the resolver (they don't need assignment/link capabilities in the current scope).

## Data flow

Coach types: *"Assign this workout to Alice"*.

1. Router classifies → `COACH_MANAGEMENT`.
2. System prompt now contains:
   ```
   Athletes:
   - Athlete-1
   - Athlete-2
   - Athlete-3
   ```
3. The word "Alice" appears in the user message (Pragmatic tier: acceptable).
4. Claude calls `findAthleteByName("Alice")` → backend resolves to `Athlete-3` via the coach's athlete list → returns `{"alias":"Athlete-3"}`.
5. Claude calls `assignTraining(trainingId, ["Athlete-3"], ...)`.
6. `CoachToolService.assignTraining` de-anonymizes `Athlete-3` → real userId → delegates to `CoachService.assignTraining`.

## Error handling

| Condition | Resolver response | Claude's likely behavior |
|---|---|---|
| Empty / blank `name` | `"Error: name is required."` | Ask the user |
| No matches | `"No athlete found matching 'X'. Known aliases: [...]"` | Ask the user to clarify |
| Multiple matches | `{"candidates": ["Athlete-3","Athlete-7"]}` | Ask the user to pick |
| User not authorized (no coach context) | `"Error: resolver requires a coach context."` | Stop and explain |
| Underlying service throws | Log + `"Error: lookup failed."` | Apologize, ask for the ID |

The resolver does **not** auto-pick when multiple matches exist — disambiguation is Claude's job via a user question.

## Testing

### Unit tests (`ResolverToolServiceTest.java`)
- Exact match → single alias returned.
- Case-insensitive substring match → single alias.
- Levenshtein-1 typo ("Alic" → "Alice") → single alias.
- Two matching athletes → candidates list, no names in response.
- No match → error string listing known aliases.
- Input is already an alias (`Athlete-3`) → returned unchanged.
- Blank input → validation error.
- Missing user context → authorization error.

### Manual end-to-end
1. Log in as a coach with athletes {Alice, Bob} and clubs {Vélo Paris}.
2. Say *"assign the FTP booster workout to Alice"* — expect no ID prompt, expect a confirmation that it was scheduled for the correct athlete.
3. Say *"create an endurance workout and link it to our Vélo Paris club session next Tuesday"* — expect no ID prompt, expect both the training and the session to be created and linked.

## Files touched

- **Modified:** `backend/src/main/java/com/koval/trainingplannerbackend/ai/agents/BaseAgentService.java`
- **Modified:** `backend/src/main/java/com/koval/trainingplannerbackend/ai/config/AIHaikuConfig.java`
- **New:** `backend/src/main/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolService.java`
- **New:** `backend/src/test/java/com/koval/trainingplannerbackend/ai/tools/resolver/ResolverToolServiceTest.java`

## Risks

- **Cache invalidation:** adding the resolver tool to existing clients changes the tool manifest, which busts Anthropic's system+tools cache on first use after deploy. One-time cost, self-healing after ~5 minutes of traffic.
- **Token cost of broader context:** injecting the athletes list into `CLUB_MANAGEMENT` adds ~5 tokens per athlete. Bounded and small for typical rosters.
- **Ambiguity with common names:** if two athletes are named "Alexandre", the tool returns both aliases and Claude asks. Acceptable — matches real-world coach UX.
- **Levenshtein-1 is not a typo fix-all:** "Alxe" (distance 2 from "Alex") misses. Out of scope by design.

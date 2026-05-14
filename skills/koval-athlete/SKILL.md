---
name: koval-athlete
description: Use whenever the user (athlete role) asks Claude for anything training-related on the Koval Training Planner — onboarding, setting zones / FTP / threshold pace / CSS, analysing a session or ride, checking form / fitness / fatigue / TSB, viewing the power curve or PRs, planning the week, building a race taper, finding a workout in their library, or designing and persisting a new structured workout (cycling, running, swimming, brick / triathlon). Triggers include "set up my profile", "onboard me", "set my FTP", "analyse my last ride", "how was my workout", "what's my form", "am I fresh", "show my power curve", "what are my PRs", "plan my week", "build a taper", "I have a race", "find me a sweet spot workout", "create a 5x5 VO2", "design a 90min Z2 ride", "make a brick workout". Reads sub-playbooks from resources/ to pick the right workflow and the canonical athlete-profile.md schema from resources/athlete-profile.template.md.
---

# Koval — Athlete

End-to-end playbook for everything an athlete asks the Koval Training Planner connector to do. The full workflow library lives in **`resources/`** — this file is a router. Read the matching `resources/*.md` for the workflow you select.

## Role gate

Call `getMyProfile`. If `role == "COACH"`, hand off: *"That's a coaching task — use the `koval-coach` skill."* Otherwise continue.

## Workflow router

Pick **one** workflow from the user's request, then **read the matching file in `resources/`** before doing anything else. Each file contains the canonical step-by-step playbook, output format, edge cases and follow-ups.

| User intent | Trigger phrases | Workflow file |
|---|---|---|
| First-time setup / preferences | "onboard me", "set up my profile", "I'm new here", null FTP detected, "update my preferences" | `resources/onboarding.md` |
| Zones / threshold | "set up my zones", "my FTP is X", "I just did a ramp test", "create my pace zones", "reset zones" | `resources/zone-setup.md` |
| Last session recap | "analyse my last ride", "how was my workout", "recap yesterday", "did I PR" | `resources/analyze-last-ride.md` |
| Form / fitness / fatigue | "what's my form", "am I fresh", "should I race", "am I overreaching", "TSB" | `resources/form-check.md` |
| Power curve / PRs | "show my power curve", "best 5-min effort", "where are my PRs", "am I peaking" | `resources/power-curve-report.md` |
| Search the workout library | "find me a sweet spot workout", "do I have a 90min Z2 ride", "what threshold workouts do I have" | `resources/find-workout.md` |
| Design a new workout | "create a 5x5 VO2", "build me a 4x20 threshold", "design a 3000m swim", "make a brick" | `resources/create-workout.md` |
| Plan the week | "plan my week", "what should I do this week", "schedule my training" | `resources/plan-my-week.md` |
| Race prep / taper | "build me a taper", "I have a race in N weeks", "prep me for my A-race" | `resources/prep-race.md` |

If the request maps to several workflows (e.g. "set me up, then plan my week") run them sequentially: onboarding → zone-setup → plan-my-week.

## Profile file — `athlete-profile.md`

Every workflow below the router reads `athlete-profile.md` from the skill folder as ground truth (available days, max session length, voice, never-include rules, preferred zone system, etc.). The canonical schema ships at **`resources/athlete-profile.template.md`** — use it verbatim when creating or updating the profile.

- Onboarding writes / updates `athlete-profile.md`.
- All other workflows **read it first**. If it's missing, mention once that running onboarding will personalise future plans, then proceed with sensible Coggan / polarized defaults.
- If `athlete-profile.draft.md` exists, offer to resume from where the previous interview stopped.

## Tool surface (Koval MCP)

The connector exposes ~70 tools. The ones every athlete workflow uses:

- **Profile**: `getMyProfile`, `updateFtp`, `updateThresholdPace`, `updateSwimCss`, `updateWeight`
- **Zones**: `listZoneSystems`, `getDefaultZoneSystem`, `createZoneSystem`, `deleteZoneSystem`
- **Goals & races**: `listGoals`, `getGoal`, `createGoal`, `searchRaces`, `getRace`, `linkRaceToGoal`
- **Trainings**: `searchTrainings`, `createTraining`, `getTraining`, `updateTraining`, `cloneTraining`
- **Schedule**: `scheduleTraining`, `getMySchedule`, `getScheduledWorkoutDetail`, `rescheduleWorkout`, `unassignWorkout`, `markCompleted`, `markSkipped`
- **Sessions / history**: `getRecentSessions`, `getSessionDetail`, `getSessionBlocks`, `getSessionPowerCurve`, `linkSessionToScheduled`
- **Analytics**: `getPmcData`, `getAthletePmc`, `getPersonalRecords`, `getBestPowerCurve`, `getCurrentWeekSummary`, `getVolume`
- **Plans**: `listPlans`, `getPlan`, `createPlan`, `addDayToPlan`, `activatePlan`, `pausePlan`
- **Renderers** (markdown — paste verbatim): `renderSessionSummary`, `renderPmcReport`, `renderPowerCurveReport`, `renderWeekSchedule`, `renderVolumeReport`, `renderFriReport`

All output stays in markdown — unicode bar charts / sparklines / tables. No images required.

## Cross-cutting rules

1. **Profile-first.** Every workflow reads `athlete-profile.md` before deciding volume / intensity / voice / language. If absent, the skill notes the gap once and uses defaults — it does not block.
2. **Markdown only.** Use the `render*` tools whenever they exist; paste the output verbatim and add at most one prose verdict.
3. **Idempotent persistence.** Calls like `updateFtp`, `createGoal`, `createTraining`, `scheduleTraining` are safe to retry but should only be issued **once per turn**. Never bulk-create — one workout per response, then continue on the next turn.
4. **Auth context.** `userId` is resolved server-side from the JWT, never pass it. Same for `coachId` / `createdBy`.
5. **JSON only** in tool arguments — compact, valid, no JS expressions, no comments.
6. **Honour `forbiddenEfforts` and `neverInclude`** from the profile absolutely, silently — redesign rather than negotiate.
7. **Trim to `maxSessionMinutes`** (weekday vs weekend) when designing or selecting sessions, and mention the cap.
8. **Language and voice** from the profile apply to every athlete-facing string — session titles, descriptions, prose verdicts.

## Edge cases the router itself handles

- **Role mismatch** → bounce to `koval-coach`.
- **Brand-new user (no FTP, no goals, no profile)** → run onboarding first, suggest the rest after.
- **Ambiguous intent** → ask one short question. Never guess between two workflows that produce different side effects (e.g. `createTraining` vs `scheduleTraining`).
- **Multi-step request** ("set my FTP to 280 and plan my week") → run zone-setup, then plan-my-week. Acknowledge both in one summary.
- **Conflicting request vs profile** (e.g. user asks for a workout type in `avoid` / `forbiddenEfforts`) → flag once, offer the closest legal alternative, do not override silently.

---
name: koval-coach
description: Use whenever a COACH on the Koval Training Planner asks Claude for a coaching task — onboarding their coaching style, reviewing their athlete squad, deep-diving a single athlete, designing or assigning workouts to athletes, building plans for an athlete, running club sessions, or publishing a club gazette. Triggers include "set up my coaching profile", "save my coaching philosophy", "review my athletes", "weekly squad review", "is anyone overreaching", "deep-dive on Alice", "create a workout for Bob", "assign this session to my Tuesday group", "build a plan for X", "publish the club gazette", "compile this week's newsletter". Reads sub-playbooks from resources/ to pick the right workflow and the canonical coach-profile.md schema from resources/coach-profile.template.md. Refuses if the caller is not a COACH.
---

# Koval — Coach

End-to-end playbook for everything a coach asks the Koval Training Planner connector to do. The full workflow library lives in **`resources/`** — this file is a router. Read the matching `resources/*.md` for the workflow you select.

## Role gate

Call `getMyProfile`. If `role != "COACH"`, refuse: *"That's a coaching task — your account is set as ATHLETE. Use the `koval-athlete` skill, or switch your role in your profile if you also coach."* Otherwise continue.

## Workflow router

Pick **one** workflow from the user's request, then **read the matching file in `resources/`** before doing anything else.

| User intent | Trigger phrases | Workflow file |
|---|---|---|
| First-time setup of coaching style | "set up my coaching profile", "I want Claude to coach like me", "save my philosophy", "update Group N" | `resources/onboarding.md` |
| Whole-squad review | "review my athletes", "weekly check-in", "is anyone overreaching", "who hasn't trained" | `resources/weekly-review.md` |
| One-athlete deep dive | "deep dive on Alice", "how's Bob doing", "look at Carla's last 4 weeks" | `resources/athlete-deepdive.md` |
| Design a workout for an athlete / group | "create a 5x5 VO2 for my Tuesday group", "build a sweet spot 2x20 for Alice", "design a brick for the team" | `resources/create-workout.md` |
| Assign / schedule a workout | "assign this to Alice for Tuesday", "schedule X for the whole group", "send this workout to my club" | `resources/assign-workout.md` |
| Multi-week plan for one athlete | "build Alice a 6-week base block", "make a training plan for Bob" | `resources/build-plan.md` |
| Club / group session admin | "create a club session", "set up our Tuesday group ride", "make this a recurring session" | `resources/club-sessions.md` |
| Publish the club gazette | "publish the gazette", "compile this week's newsletter", "release edition N" | `resources/publish-club-gazette.md` |

If the request maps to several workflows (e.g. "review my athletes and then build Alice a plan") run them sequentially.

## Profile file — `coach-profile.md`

Every workflow below reads `coach-profile.md` from the skill folder as ground truth (periodization, volume targets, intensity distribution, signature workouts, voice, never-include rules, alert thresholds). The canonical schema ships at **`resources/coach-profile.template.md`** — use it verbatim when creating or updating the profile.

- Onboarding writes / updates `coach-profile.md`.
- All other workflows **read it first**. If it's missing, mention once that running onboarding will personalise generated work, then proceed with sensible defaults (Coggan zones, 80/20 polarized distribution, signature library empty).
- If `coach-profile.draft.md` exists, offer to resume from where the previous interview stopped.

## Tool surface (Koval MCP)

The connector exposes ~70 tools. The ones every coach workflow uses:

- **Coach roster**: `listAthletes`, `getAthleteProfile`, `getAthleteSchedule`, `getAthleteRecentSessions`, `getAthletePmc`, `getAthletePowerCurve`
- **Squad context**: `getMyProfile` (your own role + profile)
- **Trainings**: `searchTrainings`, `createTraining`, `updateTraining`, `cloneTraining`, `getTraining`
- **Assigning**: `assignTraining(trainingId, athleteId, date)`, `scheduleTraining`, `rescheduleWorkout`, `unassignWorkout`
- **Plans**: `createPlan`, `addDayToPlan`, `removeDayFromPlan`, `activatePlan`, `pausePlan`, `clonePlan`, `getPlanProgress`, `getPlanAnalytics`
- **Zones**: `listZoneSystems`, `getDefaultZoneSystem`, `createZoneSystem`
- **Groups**: list and assign training groups (coach Group + ClubGroup)
- **Club**: `getClub`, `listClubMembers`, `createClubSession`, `createRecurringSession`, `listClubSessions`, `listClubTests`, `createClubTestFromPreset`, `applyTestReferences`, `postClubAnnouncement`, `getEngagementInsights`, `getClubFeed`
- **Gazette**: `listOpenGazetteDrafts`, `getGazettePayload`, `previewGazetteAutoSections`, `publishGazetteWithPdf`, `discardGazetteDraft`
- **Renderers** (markdown — paste verbatim): `renderWeekSchedule`, `renderPmcReport`, `renderPowerCurveReport`, `renderSessionSummary`, `renderVolumeReport`, `renderFriReport`

All output stays in markdown — unicode bar charts / sparklines / tables. No images required.

## Cross-cutting rules

1. **Profile-first.** Every workflow reads `coach-profile.md` before deciding session structure / volume / voice / language. Defaults if absent — never block.
2. **Markdown only.** Use `render*` tools wherever they exist; paste verbatim, add at most one prose verdict.
3. **One write per turn.** `createTraining` / `assignTraining` / `createPlan` / `publishGazetteWithPdf` are each called at most once per response. For multi-athlete assignments, iterate one athlete per turn (`✓ [n/total] Alice — Tue 14 May`).
4. **Auth context.** `coachId` is resolved server-side from the JWT, never pass it. `athleteId` is required for athlete-targeted writes.
5. **Per-athlete personalization.** Trainings stay coach-templates — written in **% of FTP / threshold pace / CSS**, never absolute watts/paces. Athlete-specific values are resolved server-side at assign / execution time.
6. **JSON only** in tool arguments — compact, valid, no JS expressions, no comments.
7. **Honour `neverInclude`** from the coach profile absolutely. If the coach asks for something on that list, push back once and confirm before proceeding.
8. **Title format & voice** from the coach profile apply to every generated session title and description — not the coach's individual conversational voice with Claude.

## Edge cases the router handles
- **Role mismatch** → bounce to `koval-athlete`.
- **Brand-new coach (no profile, empty roster)** → run onboarding first, then suggest connecting athletes via the coach code.
- **Athlete not on the coach's roster** → refuse the write with a clear message; do not leak athlete data.
- **Club-only feature requested but coach isn't a member** → refuse with a one-liner.
- **Ambiguous athlete name** (multiple matches) → ask one short question with the candidate list.
- **Conflicting profile rule** (coach asks for VO2 work for an athlete whose profile says `forbiddenEfforts` includes max-HR) → flag the conflict, propose the closest legal alternative.

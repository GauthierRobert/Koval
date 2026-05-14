# Workflow — Build a Multi-Week Plan for an Athlete

Construct a `TrainingPlan` (weeks → days → trainings), activate it on an athlete's schedule, and render the first 2 weeks for confirmation.

## Triggers
- "build Alice a 6-week base block"
- "make a training plan for Bob heading into his September race"
- "set up a 4-week recovery block for Carla"
- "clone my classic 8-week build for <athlete>"

## Step 0 — Profile + athlete
- Read `coach-profile.md` for periodization, weekly hours, max hard days, intensity distribution, signature templates, deload cadence, voice/language, neverInclude.
- Resolve the target athlete (see `assign-workout.md` step 2 for resolution rules). Refuse if not on the roster.
- `getAthleteProfile(athleteId)` → FTP/threshold/CSS, current CTL/ATL/TSB, weight.
- `listGoals` filtered to the athlete (if MCP exposes athlete goals, otherwise ask).

## Step 1 — Decide structure
From the request + profile + athlete state:
- **Number of weeks** — from the request, default 6 for base, 4 for a peaking block, 3 for taper (defer to `koval-athlete:prep-race.md` workflow rules if it's a taper).
- **Periodization** — from `coach-profile.periodization`. Common shapes:
  - **Linear**: gradual volume → intensity build, deload every 4 weeks.
  - **Block**: focus blocks of one quality at a time, 2-3 weeks each.
  - **Polarized 80-20**: high volume Z1-Z2 + 1-2 hard sessions / week.
  - **Sweet spot**: 3-4 sweet spot sessions per week, lower volume.
- **Deload** — every `coach-profile.deloadCadence` weeks at `coach-profile.deloadVolume`% of normal volume.
- **Intensity distribution** — match `coach-profile.intensityDistribution` (e.g. 80/10/10) on a weekly basis, not session basis.
- **Hard / easy days** — apply `coach-profile.maxHardDaysPerWeek` and minimum easy days between hard.

## Step 2 — Clone or create plan shell
Either:
- **Clone a known plan** — `clonePlan(planId, newTitle)`. The plan keeps the structure; reassign the target athlete next.
- **From scratch** — `createPlan(title, sport, targetFtp, durationWeeks, startDate)`.

Title format: `<athlete first name> — <focus> <duration>w` (or whatever `coach-profile.titleFormat` dictates).

## Step 3 — Fill the plan
For each `(weekNumber, dayOfWeek)` slot in the structure:
1. `searchTrainings(query, sport, minDurationMin, maxDurationMin)` to find a matching template in the coach's library (prefer signature templates from `coach-profile`).
2. If nothing fits → hand off to `create-workout.md` to design one (one per turn). Re-enter this loop on the next turn with the new `trainingId`.
3. `addDayToPlan(planId, weekNumber, dayOfWeek, trainingId)`.

To remove a slot later: `removeDayFromPlan(planId, weekNumber, dayOfWeek)`.

## Step 4 — Activate
`activatePlan(planId)` to materialise scheduled workouts on the athlete's calendar from `startDate`.

Pause anytime later with `pausePlan(planId)`.

## Step 5 — Preview
- `getPlanProgress(planId)` for status numbers.
- `getPlanAnalytics(planId)` for projected weekly TSS / hours per week.
- `renderWeekSchedule(weekStart=monday)` for the next 2 weeks; paste verbatim.

## Output format

```
## Plan for <athlete> — <focus>, <N> weeks

<one-sentence strategy>

### Projected load
- Week 1: <hours>h · ~<TSS> TSS
- Week 2: <hours>h · ~<TSS> TSS
- … (compact list)

### This week
<renderWeekSchedule>

### Next week
<renderWeekSchedule>

Plan ID: <planId> — say "swap [day]" or "make [day] easier" to adjust.
```

## Edge cases
- **Athlete is fatigued (TSB < `overreachTsb`)** → start the plan with a recovery week regardless of the request. Mention the choice.
- **Athlete has no FTP** → refuse to set absolute `targetFtp`; ask for it or hand to `koval-athlete:zone-setup.md` (athlete-side).
- **Periodization not set** → default to **polarized 80-20**.
- **Plan would overlap an existing active plan** → flag, ask whether to pause the existing plan first.
- **Request mentions a race** → after activation, suggest `koval-athlete:prep-race.md` (run by the athlete) or simply add the taper weeks at the tail of this plan.
- **Coach asks "the same plan for every athlete in the group"** → loop one athlete per turn: clone the plan, retarget, activate. Log `✓ [n/total] <athlete>`.

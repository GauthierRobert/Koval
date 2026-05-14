# Workflow — Race Prep / Taper Builder

Build a taper plan from a goal / race, schedule it, render the preview.

## Triggers
- "build me a taper for my race"
- "I have a race in 3 weeks, plan it"
- "prep me for [race name]"
- "what should I do leading into my A-race"

## Step 0 — Profile
Read `athlete-profile.md`. Honour `availableDays`, `restDays`, `maxSessionMinutes`, `forbiddenEfforts`, `neverInclude` when laying out the taper. Apply `voice`, `language`, `prescriptionUnit` to every created session. If missing, suggest onboarding once and proceed with defaults.

## Workflow

### Step 1 — Find the goal
- `listGoals` → pick the closest A-priority goal in the future, OR ask which goal if there are multiple.
- `getGoal(goalId)` for full detail.
- If the goal has a `raceId` → `getRace(raceId)` for distance, terrain, profile.

### Step 2 — Compute window
`daysToRace = targetDate - today`. Structure:

| Days out  | Structure |
|-----------|-----------|
| ≥ 21      | 1 build week + taper. Build = high CTL maintenance with race-specific intensity. Taper = −25% volume per week, keep some intensity. |
| 14-20     | 2-week taper. W1 = −15% volume + race-specific work. W2 = −35% volume, sharpening only. |
| 7-13      | 1-week taper. Reduce volume 40-50%, keep 1-2 short race-pace efforts. |
| < 7       | Opener week only. Mostly rest, one short opener 2 days out. |

### Step 3 — Build the plan
- `createPlan` with title `"<race name> taper"`, sport from the race, `targetFtp` from current profile.
- For each (weekNumber, dayOfWeek) slot:
  1. `searchTrainings(...)` for an existing match.
  2. If none, design via `create-workout.md` (one per turn).
  3. `addDayToPlan(planId, weekNumber, dayOfWeek, trainingId)`.

### Step 4 — Activate
`activatePlan(planId)` so the sessions appear on the user's schedule.

### Step 5 — Preview
`renderWeekSchedule(weekStart=monday)` for the next 1-2 weeks, paste each grid.

## Output format

```
## Taper for <race name> — <distance> on <date>

<N> days out. Plan: <one-sentence strategy>

### This week
<renderWeekSchedule verbatim>

### Next week
<renderWeekSchedule verbatim>

Plan ID: <planId> — say "swap [day]" or "make [day] easier" to adjust.
```

## Edge cases
- **No A-priority goals** → ask: *"Which race are you prepping for? Tell me date + distance and I'll build it."*
- **Race in the past** → suggest `form-check.md` instead and set up the next goal.
- **User is fatigued (TSB < -25)** → start the taper immediately regardless of days-to-race; flag the fatigue.
- **Race is < 3 days away** → only suggest a 20-30 min opener, no real planning.
- **Profile says `forbiddenEfforts` includes max-HR** → use threshold race-pace work, not VO2max, for the sharpening blocks.

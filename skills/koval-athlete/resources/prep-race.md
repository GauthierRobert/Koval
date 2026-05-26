# Workflow — Race Prep / Taper Builder

Build a taper plan from a goal / race, schedule it, render the preview.

## Triggers
- "build me a taper for my race"
- "I have a race in 3 weeks, plan it"
- "prep me for [race name]"
- "what should I do leading into my A-race"

## Step 0 — Profile
Read `athlete-profile.md`. Honour `availableDays`, `restDays`, `maxSessionMinutes`, `forbiddenEfforts`, `neverInclude` when laying out the taper. Apply `voice`, `language`, `prescriptionUnit` to every created session. If missing, suggest onboarding once and proceed with defaults.

If `Training method` on the profile is set to anything other than `none`, **also read `training-methods/<slug>.md`** before Step 3 — the method dictates how the taper is shaped:
- `polarized` / `pyramidal` / `daniels`: keep short VO2 / I-pace sharpening efforts in the taper, drop volume.
- `norwegian`: sharpening shifts from sub-threshold doubles to single short race-pace efforts in the final 10 days.
- `sweet-spot` / `block-periodization`: replace block-weeks with single race-pace openers in the taper window; never start a new HIT block inside the taper.
- `maffetone`: keep the HR cap; add short race-pace efforts only in the final 7 days as openers, otherwise stay aerobic.
- `lydiard`: the taper IS the "coordination / sharpening" phase — short fast reps, time trials, no more base mileage growth.
- If `method = none`, apply the generic structure in Step 2 below.

Method choice never overrides `forbiddenEfforts`. If a method's signature sharpening session is on the forbidden list, substitute the closest legal alternative and flag it once.

## Workflow

### Step 1 — Find the goal
- `getAthleteContext` → from `goals`, pick the closest A-priority goal in the future, OR ask which goal if there are multiple.
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
`setPlanStatus(planId, status='ACTIVE', startDate=<plan start Monday>)` so the sessions appear on the user's schedule.

### Step 5 — Preview
`getSchedule(from=monday, to=sunday)` for the next 1-2 weeks; build a Mon-Sun grid (○ pending / ✔ completed / ✗ skipped) for each.

## Output format

```
## Taper for <race name> — <distance> on <date>

<N> days out. Plan: <one-sentence strategy>

### This week
<Mon-Sun week grid you build from getSchedule>

### Next week
<Mon-Sun week grid you build from getSchedule>

Plan ID: <planId> — say "swap [day]" or "make [day] easier" to adjust.
```

## Edge cases
- **No A-priority goals** → ask: *"Which race are you prepping for? Tell me date + distance and I'll build it."*
- **Race in the past** → suggest `form-check.md` instead and set up the next goal.
- **User is fatigued (TSB < -25)** → start the taper immediately regardless of days-to-race; flag the fatigue.
- **Race is < 3 days away** → only suggest a 20-30 min opener, no real planning.
- **Profile says `forbiddenEfforts` includes max-HR** → use threshold race-pace work, not VO2max, for the sharpening blocks.

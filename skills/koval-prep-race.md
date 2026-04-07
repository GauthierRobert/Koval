---
name: koval-prep-race
description: Use when the user asks Claude to prepare for a race, build a taper, or plan training around a goal event — phrases like "build me a taper", "I have a race in N weeks", "prep me for my A-race", "plan around my goal". Looks up the goal/race via Koval MCP, builds a taper plan with appropriate sessions per week, schedules them, and renders a preview.
---

# Race Prep / Taper Builder

## When to use
- "build me a taper for my race"
- "I have a race in 3 weeks, plan it"
- "prep me for [race name]"
- "what should I do leading into my A-race"

## Workflow

1. **Find the goal**:
   - Call `listGoals` and pick the closest A-priority goal in the future, OR ask the user which goal if there are multiple.
   - Call `getGoal(goalId)` for full detail.
   - If the goal has a `raceId`, call `getRace(raceId)` for distance, terrain, profile.
2. **Compute window**: `daysToRace = targetDate - today`. Decide structure:
   - **>= 21 days**: 1 build week + taper. Build = high CTL maintenance with race-specific intensity. Taper = -25% volume per week, keep some intensity.
   - **14-20 days**: 2-week taper. Week 1 = -15% volume + race-specific work. Week 2 = -35% volume, sharpening only.
   - **7-13 days**: 1-week taper. Reduce volume by 40-50%, keep 1-2 short race-pace efforts.
   - **< 7 days**: opener week only. Mostly rest, one short opener 2 days out.
3. **Build the plan**:
   - Call `createPlan` with title `"<race name> taper"`, sport from the race, `targetFtp` from current profile.
   - For each week and day, either `searchTrainings` for an existing match or `createTraining` then `addDayToPlan(planId, weekNumber, dayOfWeek, trainingId)`.
4. **Activate**: `activatePlan(planId)` so its sessions appear on the user's schedule.
5. **Preview**: call `renderWeekSchedule(weekStart=monday)` for the next 1-2 weeks and paste each grid.

## Output format

```
## Taper for <race name> — <distance> on <date>

<N> days out. Plan: <one-sentence strategy>

### This week
<rendered week grid>

### Next week
<rendered week grid>

Plan ID: <planId> — say "swap [day]" or "make [day] easier" to adjust.
```

## Edge cases
- **No A-priority goals** → ask: "Which race are you prepping for? Tell me the date and distance and I'll build it."
- **Race in the past** → suggest `koval-form-check` instead and set up the next goal.
- **User is fatigued (TSB < -25)** → start the taper immediately regardless of days-to-race; flag the high fatigue.
- **Race is < 3 days away** → only suggest a 20-30min opener, no real planning.

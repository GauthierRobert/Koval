---
name: koval-plan-my-week
description: Use when the user asks Claude to plan, build or schedule their training week — phrases like "plan my week", "what should I do this week", "build my training week", "schedule my workouts". Looks at current form, goals and recent load via Koval MCP, proposes 5-7 sessions, schedules them on the right days, and renders a final week grid for confirmation.
---

# Plan My Week

## When to use
- "plan my training week"
- "what should I do this week"
- "build me a 5-day training week"
- "schedule something for me this week"

## Workflow

1. **Gather context** (parallel reads):
   - `getMyProfile` — sport focus, FTP/CSS, role
   - `getPmcData` for last 14 days — current CTL/ATL/TSB to calibrate intensity
   - `listGoals` — upcoming A/B priority races or fitness goals
   - `getRecentSessions` with `limit=7` — what they've actually done lately
2. **Decide volume + intensity** based on TSB:
   - TSB > +5 (fresh): 1 hard session + 1 tempo + 3-4 endurance, total 6-8h
   - TSB -10 to +5 (neutral): 1 hard + 1 tempo + 2-3 endurance + 1 recovery
   - TSB < -10 (fatigued): scale back — recovery / endurance only, no VO2
3. **Pick or build sessions**:
   - For each planned slot, first try `searchTrainings(query, sport, minDurationMin, maxDurationMin)` to find an existing template in the user's library
   - If nothing fits, call `createTraining` with a properly structured `TrainingRequest` (warmup → main blocks → cooldown). Use percentages of FTP / threshold pace / CSS, never absolute watts.
4. **Schedule**:
   - Determine the Monday of the target week (default: this Monday; if today is Fri/Sat/Sun, default: next Monday)
   - For each session, call `scheduleTraining(trainingId, date, status='PENDING')` — space hard days apart, easy day after every hard day
5. **Render**:
   - Call `renderWeekSchedule(weekStart=monday)` and paste the output

## Output format

```
Here's your week (TSB: <value>, focus: <focus>):

<rendered week grid>

Highlights:
- <Tuesday>: <session name + why>
- <Thursday>: <session name + why>
- <Saturday>: <key session>

Adjust anything? Just tell me which day to swap.
```

## Edge cases
- **No goals set** → default focus is "general fitness, balanced sweet spot + endurance"
- **Tightly fatigued (TSB < -25)** → propose a recovery week instead, do not schedule any VO2 / threshold work
- **A-race within 14 days** → defer to `koval-prep-race` skill instead
- **User declines a session** → call `unassignWorkout(scheduledWorkoutId)` and re-propose

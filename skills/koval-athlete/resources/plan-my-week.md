# Workflow — Plan My Week

Look at current form, goals and recent load, propose 5-7 sessions, schedule them on the right days, render a final week grid.

## Triggers
- "plan my training week"
- "what should I do this week"
- "build me a 5-day training week"
- "schedule something for me this week"

## Step 0 — Profile
Read `athlete-profile.md`. Use `availableDays`, `restDays`, `longSessionDay`, `maxSessionMinutes` (weekday/weekend), `neverInclude`, `forbiddenEfforts`, `prescriptionUnit`, `defaultZoneSystem` and the `voice` block to constrain everything. If missing, mention once that running onboarding will personalise the plan, then proceed with defaults.

## Step 1 — Gather context (parallel)
- `getMyProfile` — sport focus, FTP/CSS, role
- `getPmcData(from=today-14d, to=today)` — current CTL/ATL/TSB to calibrate intensity
- `listGoals` — upcoming A/B priority races or fitness goals
- `getRecentSessions(limit=7)` — what they've actually done lately
- `getCurrentWeekSummary` — load already on the books for the target week

## Step 2 — Decide volume + intensity

Profile sets the **ceiling** (`weeklyHours`, `maxHardDaysPerWeek`, `favouriteSessionTypes`, `avoid`, `forbiddenEfforts`, `sleepBaseline`). TSB sets the **fill** within that ceiling:

- **TSB > +5 (fresh):** fill the full `weeklyHours` budget. Up to `maxHardDaysPerWeek` hard sessions (1 VO2/threshold + 1 tempo if budget allows two), rest endurance. Pick session types from `favouriteSessionTypes` first.
- **TSB -10 to +5 (neutral):** target ~85% of `weeklyHours`. One hard + one tempo *if* `maxHardDaysPerWeek >= 2`, otherwise one hard only. Add a recovery day.
- **TSB < -10 (fatigued):** target ~60% of `weeklyHours`. Recovery + endurance only — no VO2, no threshold, regardless of `favouriteSessionTypes`.
- **`sleepBaseline = poor`:** drop one intensity day from whichever bucket above, replace with endurance.
- Never schedule a session type listed in `avoid` or `forbiddenEfforts`.
- **No profile**: fresh = 6-8h / 1 hard + 1 tempo + 3-4 endurance; neutral = 1 hard + 1 tempo + 2-3 endurance + 1 recovery; fatigued = recovery/endurance only.

## Step 3 — Pick or build sessions
For each planned slot:
1. Try `searchTrainings(query, sport, minDurationMin, maxDurationMin)` to find an existing template in the user's library.
2. If nothing fits, design and create via `create-workout.md`. Use percentages of FTP / threshold pace / CSS — never absolute watts.

## Step 4 — Schedule
- Determine the Monday of the target week: default = this Monday; if today is Fri/Sat/Sun, default = next Monday.
- For each session call `scheduleTraining(trainingId, date, status='PENDING')`.
- Space hard days apart; easy day after every hard day.
- Honour `longSessionDay` for the longest session; respect `restDays`.

## Step 5 — Render
- `renderWeekSchedule(weekStart=monday)` — paste the output.

## Output format

```
Here's your week (TSB: <value>, focus: <focus>):

<renderWeekSchedule output verbatim>

Highlights:
- <Tuesday>: <session name + why>
- <Thursday>: <session name + why>
- <Saturday>: <key session>

Adjust anything? Just tell me which day to swap.
```

## Edge cases
- **No goals set** → default focus is "general fitness, balanced sweet spot + endurance".
- **Tightly fatigued (TSB < -25)** → propose a recovery week instead; no VO2 / threshold work.
- **A-race within 14 days** → hand off to `prep-race.md`.
- **User declines a session** → `unassignWorkout(scheduledWorkoutId)` and re-propose.
- **Conflict on a day** (existing scheduled workout) → ask whether to swap or stack.
- **Profile says `maxHardDaysPerWeek = 0`** (recovery cycle) → only endurance / recovery regardless of TSB.

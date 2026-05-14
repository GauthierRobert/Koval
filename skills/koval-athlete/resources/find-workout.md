# Workflow — Find a Workout

Search the athlete's training library by sport / duration / title, surface candidates and offer to schedule one.

## Triggers
- "find me a [duration / type] [sport] workout"
- "do you have a [type] session in my library"
- "what threshold workouts do I have"
- "give me a Z2 run for tomorrow"

If the user wants a **new** workout designed (not searched), use `create-workout.md` instead.

## Step 0 — Profile
Read `athlete-profile.md`. Use `favouriteSessionTypes`, `avoid`, `forbiddenEfforts`, `maxSessionMinutes`, `structurePreference`, `environment` to bias filters and exclude candidates the athlete shouldn't do.

## Workflow

1. **Parse the request** for filters:
   - `query` — title keyword (sweet spot, threshold, VO2, recovery, endurance, …)
   - `sport` — CYCLING / RUNNING / SWIMMING / BRICK
   - `minDurationMin` / `maxDurationMin` — if a length is given
2. `searchTrainings(query, sport, minDurationMin, maxDurationMin)`.
3. **If 0 results** → loosen filters progressively (drop the title query first, then duration). Explain the loosening: *"Nothing matched 'sweet spot' under 60min — here are sweet spot workouts of any length:"*. If still 0, offer to create one (`create-workout.md`).
4. **Filter** the result list against the profile's `avoid` / `forbiddenEfforts` / `maxSessionMinutes` — silently drop disallowed candidates.
5. **Present**:
   - 1 result → show it and offer to schedule.
   - 2+ → numbered markdown list (max 5), ask which one.
6. **Schedule** when the user picks one and gives a date — `scheduleTraining(trainingId, date)`. Confirm with a one-line success.

## Output format

Multiple matches:
```
Found <N> matching workouts:

1. **<title>** — <duration>min, <sport>, ~<TSS> TSS
2. **<title>** — …
3. …

Which one — and what day?
```

Single match or after pick:
```
**<title>** (<duration>min, <TSS> TSS)
<one-line description if available>

Want me to schedule it? Just tell me the day.
```

After `scheduleTraining`:
```
Scheduled **<title>** for <weekday DD MMM>.
```

## Edge cases
- **Empty library** → suggest `plan-my-week.md` or offer `create-workout.md`.
- **User wants to do it "today"** → after `scheduleTraining`, remind them they can `markCompleted` once done.
- **Filters too narrow** → progressively loosen and tell the user what you dropped.
- **Workout exceeds `maxSessionMinutes`** → flag it: *"This one's 95min — over your 75min weekday cap. Use it anyway, or want a shorter alternative?"*

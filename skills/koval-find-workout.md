---
name: koval-find-workout
description: Use when the user wants to find or pick a workout from their library — phrases like "find me a sweet spot session", "do you have a 90-minute endurance ride", "what threshold workouts do I have", "give me a Z2 run". Searches the user's training library via Koval MCP, lists candidates, and offers to schedule the chosen one.
---

# Find a Workout

## When to use
- "find me a [duration / type] [sport] workout"
- "do you have a [type] session in my library"
- "what [type] workouts do I have"
- User asks Claude to pick a workout to do today/tomorrow

## Workflow

1. **Parse the request** for filters:
   - `query` → keyword from the title (sweet spot, threshold, VO2, recovery, endurance, etc.)
   - `sport` → CYCLING / RUNNING / SWIMMING / BRICK
   - `minDurationMin` / `maxDurationMin` → if user mentioned a length
2. Call `searchTrainings(query, sport, minDurationMin, maxDurationMin)`.
3. If 0 results → loosen filters (drop the title query first, then duration) and retry. If still 0 → offer to create a new one.
4. If 1 result → present it and offer to schedule.
5. If 2+ results → present a numbered markdown list (max 5) and ask which one.
6. When the user picks one and gives a date → call `scheduleTraining(trainingId, date)`. Confirm with one-line success.

## Output format

For multiple matches:

```
Found <N> matching workouts:

1. **<title>** — <duration>min, <sport>, ~<TSS> TSS
2. **<title>** — ...
3. ...

Which one — and what day?
```

For a single match or after the user picks:

```
**<title>** (<duration>min, <TSS> TSS)
<one-line description if available>

Want me to schedule it? Just tell me the day.
```

## Edge cases
- **Empty library** → suggest the `koval-plan-my-week` skill or offer to create one with `createTraining`.
- **User wants to do it "today"** → after `scheduleTraining`, also remind them they can mark it COMPLETED via `markCompleted`.
- **Filters too narrow** → progressively loosen and explain ("Nothing matched 'sweet spot' under 60min — here are sweet spot workouts of any length:").

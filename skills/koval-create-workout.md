---
name: koval-create-workout
description: Use when the user wants to design and create a new structured workout — phrases like "create a sweet spot workout", "build me a 5x5 VO2 session", "make a 90min Z2 ride", "design a 3000m swim with 10x100 on 1:45", "create a brick workout". Acts as an expert workout designer (cycling / running / swimming / triathlon), computes TSS, and persists the training via Koval MCP `createTraining`.
---

# Create a Workout

Expert Workout Designer for cycling, running, swimming, triathlon. Use this skill any time the user asks Claude to **build, design, or create** a structured session.

## When to use
- "create / build / design / make me a [type] [sport] workout"
- "I want a [duration] [intensity] session"
- "give me a 5x5 VO2 / 4x8 threshold / sweet spot 2x20 / 10x100 on 1:45"
- "make a brick / triathlon workout"
- User wants the workout **persisted to their library** (not just described)

If the user only wants to *find* an existing one, use `koval-find-workout` instead.

## Step 0 — Load athlete profile
Read `athlete-profile.md` if present. Use `favouriteSessionTypes`, `avoid`, `forbiddenEfforts`, `maxSessionMinutes`, `structurePreference`, `environment`, FTP / threshold pace / CSS to bias the design. If missing, suggest `koval-athlete-onboarding` once and proceed with sensible defaults (Coggan zones, FTP from profile if available).

## Workflow
1. **Parse the request**: sport, target duration, intensity focus, constraints (pool length, equipment, terrain, indoor/outdoor).
2. **Design** the block structure following the rules below. Do NOT call any tool yet.
3. **Compute TSS** before persisting (formula below).
4. **Call `createTraining` exactly once** with the full structured request.
5. Reply with the **Output** format below — short, no preamble.
6. If the user asked for a date too → call `scheduleTraining(trainingId, date)` after creation.

---

## STRUCTURE RULES
- Element = **leaf** (has `type`) or **set** (has `reps` + `elements`). Never both.
- **One of** `dur` or `dist` per leaf, never both. Prefer `dur` for CYCLING; `dist` for RUNNING / SWIMMING.
- Use `reps` + `elements` for repeated sets.
- Block types: `WARMUP`, `INTERVAL`, `STEADY`, `RAMP`, `COOLDOWN`, `FREE`, `PAUSE`, `TRANSITION`.
- SWIM rest: duration → `PAUSE` (passive); distance → `STEADY` (active).
- RAMP uses `pctFrom` / `pctTo` instead of `pct`.

#### Example: 2 sets of 10×30/30, 2min rest between sets
```json
{"reps":2,"restDur":120,"restPct":40,"elements":[
  {"reps":10,"elements":[
    {"type":"INTERVAL","dur":30,"label":"Z5","pct":120},
    {"type":"STEADY","dur":30,"label":"Recovery","pct":55}
  ]}
]}
```

## SWIM-SPECIFIC RULES
- Always set `stroke`: `FREESTYLE`, `BACKSTROKE`, `BREASTSTROKE`, `BUTTERFLY`, `IM`, `KICK`, `PULL`, `DRILL`, `CHOICE`.
- Use `dist` (meters) — swimming is distance-based.
- **Send-off intervals**: use `sendOff` (seconds) for "10×100m on 1:45". Rest is implicit (`sendOff − swim time`). Do NOT also set `restDur`.
- **Equipment** when applicable: `PADDLES`, `PULL_BUOY`, `FINS`, `SNORKEL`, `BAND`, `KICKBOARD`.
- Set `poolLength` (25 or 50) on the request. Null for open water.
- Include equipment + stroke in the block `label` (e.g. `"4×50m Kick w/ Fins"`).
- Swim cadence (`cad`) = strokes per minute (SPM), typical 50-80.

## TRANSITION BLOCKS (TRIATHLON / BRICK)
- `type: TRANSITION` with `transition: T1` (swim→bike) or `transition: T2` (bike→run).
- Use `dur` (target seconds). No intensity needed.
- Include between discipline changes in BRICK / TRIATHLON workouts.
- Example: `{"type":"TRANSITION","dur":120,"label":"T2 Bike-to-Run","transition":"T2","desc":"Quick change, start easy"}`

## INTENSITY
- **Cycling**: `pct` = %FTP (Coggan).
- **Running**: `pct` = %Threshold Pace, `cad` ≈ 170+.
- **Swimming**: `pct` = %CSS (Critical Swim Speed), `cad` = SPM (50-80).

## ZONE SYSTEM
If a **Default Zone System** exists for the sport:
- Set `zoneSystemId`, use zone labels in block labels, set `zone` to the label (e.g. `"Z2"`).
- ONLY IF intensity not in the request: set `pct` to the zone midpoint.
- Respect zone **annotations** (coach conventions).
- When the coach references zones by name, map to the **custom zone** boundaries, not generic Coggan.
If none exists, fall back to standard Coggan / pace / CSS conventions.

## TSS — compute BEFORE `createTraining`
**Formula:** `TSS = Σ (dur_s × (pct/100)²) / 36`
- `STEADY` / `INTERVAL` / `WARMUP` / `COOLDOWN`: use `pct`.
- `RAMP`: use the average of `pctFrom`, `pctTo`.
- `FREE` / `PAUSE`: 50 or 0.
- Sets: per-rep TSS × reps + rest TSS.
- Examples: 60min @ 75% → **56**. 5×4min @ 110% / 3min @ 50% → **44**. Round to integer.
- Sanity check: recovery 30-40, endurance 50-70, threshold 70-90, VO2max 80-120.

## BULK CREATION (CRITICAL)
- **One `createTraining` / `updateTraining` per turn.** Never call it more than once in a single response.
- After each call: `✓ [n/total] [title]` then continue on the next turn.
- Design and create one workout at a time.

## GENERAL RULES
1. **Context first** — date, user profile, FTP, athletes, groups, clubs are already in your context. Do NOT call tools to fetch them.
2. **JSON only** in tool arguments — valid, compact, no JS expressions.
3. **Auto-fields** — omit `id`, `createdAt`, `createdBy`, and null fields.
4. **UserId** — resolved from auth, never pass it.

## OUTPUT
- No preamble. No restating the request. No describing the block-by-block content (it's in the training object).
- After the tool call, **max 3 lines**:

```
**Done:** <title>
- <duration>min · TSS <n> · IF <0.xx> · <sport>
```

If you also scheduled it: append `· scheduled <YYYY-MM-DD>`.
On error: one sentence.

## Edge cases
- **No FTP / CSS / threshold pace** → use generic %FTP defaults, mention once that estimates will sharpen after `koval-zone-setup`.
- **`forbiddenEfforts` from profile** (e.g. no all-out sprints, no max HR) → honour silently, redesign without them.
- **Request exceeds `maxSessionMinutes`** → trim to the cap and note it in the Done line.
- **Ambiguous sport** → ask one short question; do not guess between RUN and BIKE.
- **User wants several workouts** → create the first one this turn, then continue one-per-turn.

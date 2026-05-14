# Workflow — Create a Workout

Expert workout designer for cycling / running / swimming / triathlon. Designs the block structure, computes TSS, persists via `createTraining`, optionally schedules.

## Triggers
- "create / build / design / make me a [type] [sport] workout"
- "I want a [duration] [intensity] session"
- "give me a 5x5 VO2 / 4x8 threshold / sweet spot 2x20 / 10x100 on 1:45"
- "make a brick / triathlon workout"
- User wants the workout **persisted** to their library (not just described)

If they only want to *find* an existing one, use `find-workout.md`.

## Step 0 — Profile
Read `athlete-profile.md`. Use `favouriteSessionTypes`, `avoid`, `forbiddenEfforts`, `maxSessionMinutes`, `structurePreference`, `environment` plus FTP / threshold pace / CSS to bias the design. Defaults: Coggan zones, FTP from `getMyProfile` if present.

## Workflow
1. **Parse**: sport, target duration, intensity focus, constraints (pool length, equipment, terrain, indoor/outdoor).
2. **Design** the block structure following the rules below. Do NOT call any tool yet.
3. **Compute TSS** before persisting (formula below).
4. **Call `createTraining` exactly once** with the full structured request.
5. Reply with the Output format below — short, no preamble.
6. If a date was requested → call `scheduleTraining(trainingId, date)` after creation.

---

## Structure rules
- Element = **leaf** (has `type`) or **set** (has `repetitions` + `elements`). Never both.
- **One of** `durationSeconds` or `distanceMeters` per leaf, never both. Prefer `durationSeconds` for CYCLING; `distanceMeters` for RUNNING / SWIMMING.
- Use `repetitions` + `elements` for repeated sets.
- Block types: `WARMUP`, `INTERVAL`, `STEADY`, `RAMP`, `COOLDOWN`, `FREE`, `PAUSE`, `TRANSITION`.
- SWIM rest: duration → `PAUSE` (passive); distance → `STEADY` (active).
- RAMP uses `intensityStart` / `intensityEnd` instead of `intensityTarget`.

### Set rest semantics (matches the manual builder UI)
On a set, two fields control rest between reps:
- `restDurationSeconds = null` or `0` → **no rest at all** between reps (UI: "No rest").
- `restDurationSeconds > 0` and `restIntensity = null` or `0` → **passive rest** (UI: "Passive rest"). Materialized server-side as `PAUSE` at 0% intensity.
- `restDurationSeconds > 0` and `restIntensity > 0` → **active rest** at that % of FTP / threshold pace / CSS.

#### Example — 2 sets of 10×30/30, 2min active rest between sets
```json
{"repetitions":2,"restDurationSeconds":120,"restIntensity":40,"elements":[
  {"repetitions":10,"elements":[
    {"type":"INTERVAL","durationSeconds":30,"label":"Z5","intensityTarget":120},
    {"type":"STEADY","durationSeconds":30,"label":"Recovery","intensityTarget":55}
  ]}
]}
```

## Swim-specific rules
- Always set `strokeType`: `FREESTYLE`, `BACKSTROKE`, `BREASTSTROKE`, `BUTTERFLY`, `IM`, `KICK`, `PULL`, `DRILL`, `CHOICE`.
- Use `distanceMeters` — swimming is distance-based.
- **Send-off intervals**: use `sendOffSeconds` for "10×100m on 1:45". Rest is implicit (`sendOffSeconds − swim time`). Do NOT also set `restDurationSeconds`.
- **Equipment** when applicable: `PADDLES`, `PULL_BUOY`, `FINS`, `SNORKEL`, `BAND`, `KICKBOARD`.
- Set `poolLength` (25 or 50) on the request. Null for open water.
- Include equipment + stroke in the block `label` (e.g. `"4×50m Kick w/ Fins"`).
- Swim cadence (`cadenceTarget`) = strokes per minute (SPM), typical 50-80.

## Transition blocks (brick / triathlon)
- `type: TRANSITION` with `transitionType: T1` (swim→bike) or `transitionType: T2` (bike→run).
- Use `durationSeconds` (target seconds). No intensity needed.
- Include between discipline changes in BRICK / TRIATHLON workouts.
- Example: `{"type":"TRANSITION","durationSeconds":120,"label":"T2 Bike-to-Run","transitionType":"T2","description":"Quick change, start easy"}`

## Intensity
- **Cycling**: `intensityTarget` = %FTP (Coggan).
- **Running**: `intensityTarget` = %Threshold Pace, `cadenceTarget` ≈ 170+.
- **Swimming**: `intensityTarget` = %CSS, `cadenceTarget` = SPM (50-80).

## Zone system
If a **Default Zone System** exists for the sport:
- Set `zoneSystemId`, use zone labels in block labels, set `zoneTarget` to the label (e.g. `"Z2"`).
- ONLY IF intensity is missing from the request: set `intensityTarget` to the zone midpoint.
- Respect zone **annotations** (coach conventions).
- When the user references zones by name, map to the **custom zone** boundaries, not generic Coggan.
If none exists, fall back to Coggan / pace / CSS conventions.

## TSS — compute BEFORE `createTraining`
**Formula:** `TSS = Σ (durationSeconds × (intensityTarget/100)²) / 36`
- `STEADY` / `INTERVAL` / `WARMUP` / `COOLDOWN` → use `intensityTarget`.
- `RAMP` → average of `intensityStart`, `intensityEnd`.
- `FREE` / `PAUSE` → 50 or 0.
- Sets: per-rep TSS × repetitions + rest TSS.
- Examples: 60min @ 75% → **56**. 5×4min @ 110% / 3min @ 50% → **44**. Round to integer.
- Sanity: recovery 30-40 · endurance 50-70 · threshold 70-90 · VO2max 80-120.

## Bulk creation (CRITICAL)
- **One `createTraining` / `updateTraining` per turn.** Never call it more than once in a response.
- After each call: `✓ [n/total] [title]` then continue on the next turn.
- Design and create one workout at a time.

## General rules
1. **Context first** — date, profile, FTP, athletes, groups, clubs are already in your context. Do NOT fetch them.
2. **JSON only** in tool arguments — valid, compact, no JS expressions.
3. **Auto-fields** — omit `id`, `createdAt`, `createdBy`, and null fields.
4. **UserId** — resolved from auth, never pass it.

## Output
- Never reference athlete-specific reference values inside the training. The training should be adaptable for every athlete.
- No preamble. No restating the request. No describing the block-by-block content (it's in the training object).
- After the tool call, max 3 lines:

```
**Done:** <title>
- <duration>min · TSS <n> · IF <0.xx> · <sport>
```
If you also scheduled: append `· scheduled <YYYY-MM-DD>`.
On error: one sentence.

## Edge cases
- **`forbiddenEfforts` from profile** (e.g. no all-out sprints, no max HR) → honour silently, redesign without them.
- **Request exceeds `maxSessionMinutes`** → trim to the cap and note it in the Done line.
- **Ambiguous sport** → ask one short question; don't guess between RUN and BIKE.
- **User wants several workouts** → create the first one this turn, continue one-per-turn.

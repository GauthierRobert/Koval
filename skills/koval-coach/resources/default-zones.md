# Reference — Default Zones (Coggan-style)

Canonical fallback zone definitions used when the coach has **no `defaultZoneSystem`** in their profile and `getAthleteContext` → `zoneSystems` has no default for that sport.

Used by `create-workout.md`, `build-plan.md`, `assign-workout.md`, and any other workflow that needs to label a block, pick a `zoneTarget`, or derive an `intensityTarget` midpoint when neither the coach nor the request specified one.

## How to use this file

1. Look up the sport table below.
2. Map the request's intent (or zone label the coach referenced) to a row.
3. Use `Label` for `zoneTarget` and block `label`.
4. **Only if `intensityTarget` was not given in the request** → use the `Midpoint` column as `intensityTarget`.
5. Do NOT set `zoneSystemId` — there isn't one. Just use the label.

Trainings stay generic — these are % of FTP / threshold pace / CSS, so the same template applies on every athlete at assign time.

If the coach has a custom `defaultZoneSystem` (from `coach-profile.md` or `getAthleteContext` → `zoneSystems`), use it instead and ignore this file.

---

## Cycling (% FTP)

| Zone | Range    | Midpoint | Label              | Typical intent                         |
|------|----------|----------|--------------------|----------------------------------------|
| Z1   | 0-55     | 45       | Active recovery    | Recovery spin, cooldown                |
| Z2   | 56-75    | 65       | Endurance          | Long aerobic, base                     |
| Z3   | 76-90    | 83       | Tempo              | Sweet spot lower end, sustained effort |
| Z4   | 91-105   | 98       | Threshold          | FTP intervals, 2x20, 4x10              |
| Z5   | 106-120  | 113      | VO2max             | 5x5, 4x4, 3x3                          |
| Z6   | 121-150  | 135      | Anaerobic capacity | 30/30s, 1-min repeats                  |
| Z7   | 151-300  | 200      | Neuromuscular      | All-out sprints (≤15s)                 |

Sweet spot (88-94% FTP) lives at the Z3/Z4 border — use `intensityTarget: 91` and label `"Sweet Spot"` when the request asks for it.

## Running (% Threshold Pace — higher % = faster)

| Zone | Range    | Midpoint | Label     | Typical intent                  |
|------|----------|----------|-----------|---------------------------------|
| Z1   | 0-75     | 60       | Recovery  | Easy jog, walk-run              |
| Z2   | 76-87    | 82       | Endurance | Conversational long run         |
| Z3   | 88-94    | 91       | Tempo     | Marathon pace, steady state     |
| Z4   | 95-102   | 98       | Threshold | Half-marathon to 10k pace       |
| Z5   | 103-110  | 107      | VO2max    | 5k-3k pace intervals            |
| Z6   | 111-130  | 120      | Anaerobic | 1500m-800m pace, hill repeats   |

Set `cadenceTarget` ≈ 170-180 spm for Z3+ unless the request says otherwise.

## Swimming (% CSS speed)

| Zone | Range    | Midpoint | Label        | Typical intent                          |
|------|----------|----------|--------------|-----------------------------------------|
| Z1   | 80-87    | 84       | Recovery     | Easy swim, technique, drills            |
| Z2   | 88-93    | 90       | Endurance    | Long aerobic sets (400m+ reps)          |
| Z3   | 94-100   | 97       | Tempo        | Sub-CSS sustained, race-pace endurance  |
| Z4   | 101-105  | 103      | Threshold    | CSS sets (100s, 200s)                   |
| Z5   | 106-145  | 125      | VO2 / Sprint | Short fast reps (≤50m), race finishes   |

Swim cadence (`cadenceTarget`) = strokes per minute, typical 50-80.

---

## Intent → zone mapping (when the request is vague)

When the coach asks for a "type" but no zone or %:

| Coach says…                            | Cycling | Running | Swimming |
|----------------------------------------|---------|---------|----------|
| recovery / easy spin / shake-out       | Z1      | Z1      | Z1       |
| endurance / aerobic / base / Z2 ride   | Z2      | Z2      | Z2       |
| tempo / steady                         | Z3      | Z3      | Z3       |
| sweet spot                             | Z3 high (91%) | —  | —        |
| threshold / FTP / sustained effort     | Z4      | Z4      | Z4       |
| VO2max / VO2                           | Z5      | Z5      | Z5       |
| anaerobic / hard intervals             | Z6      | Z6      | Z5       |
| sprint / neuromuscular / all-out       | Z7      | Z6      | Z5       |
| warmup / cooldown                      | Z1→Z2   | Z1→Z2   | Z1       |

## Cross-cutting rules

- Ranges are **inclusive lower, inclusive upper**. Border cases: use the upper zone (e.g. 75% → Z2, 76% → Z3).
- Block-by-block `intensityTarget` overrides the midpoint if the coach gave a specific value.
- For `RAMP` blocks, pick `intensityStart` from the lower zone and `intensityEnd` from the upper zone (e.g. WU ramp Z1→Z3: start 50, end 80).
- These defaults are the project-wide baseline. If the coach's signature templates in `coach-profile.md` override any of these, prefer the coach profile.

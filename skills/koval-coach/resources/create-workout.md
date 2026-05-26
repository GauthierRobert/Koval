# Workflow — Create a Workout (Coach)

Design and persist a structured workout **template** that can be reused across athletes / groups / clubs. Stays generic — no athlete-specific watts/paces, only % of FTP / threshold pace / CSS.

## Triggers
- "create a 5x5 VO2 for my Tuesday group"
- "build me a sweet spot 2x20 to send to the club"
- "design a brick for the team"
- "save my signature drill as a training"

If the coach also wants to **assign / schedule** it after creation, chain into `assign-workout.md`.

## Step 0 — Profile
Read `coach-profile.md`. Use:
- `Training method` — if set to anything other than `none`, **also read `training-methods/<slug>.md`** before designing. Its "How this skill applies it" section overrides the generic guidance below when they conflict (intensity caps, hallmark session shapes, periodization context).
- `defaultZoneSystem` and `prescriptionStyle` for targets. If not set, fall back to the sport's default in `getAthleteContext` → `zoneSystems`; if still nothing, use the Coggan-style tables in `default-zones.md`.
- `defaultWarmup` / `defaultCooldown` / signature templates (`threshold` / `VO2max` / `sweet spot` / `endurance`) — start from these blocks, adapt the parameters from the request.
- `titleFormat`, `descriptionStyle`, `language`, `signatureCues` — apply to the title and block descriptions.
- `neverInclude` — refuse silently and redesign if the request hits it.

**Athlete self-context (if the workout is being designed for one specific athlete):** the athlete's preferences (formats they enjoy, indoor vs outdoor, equipment, voice) are a **recommendation only** — lean on them for tone and block flavour when the coach hasn't specified, but **always defer to the coach's explicit instruction**. Templates that target a group or club should ignore athlete self-context entirely; the template must work for every athlete it gets assigned to.

If the coach's method does not suit the requested sport (e.g. `daniels` is run-specific and the request is cycling), fall back to generic rules for this turn and note the mismatch on the Done line.

## Workflow
1. **Parse** the request: sport, target duration, intensity focus, constraints (pool length, equipment, terrain, indoor/outdoor), audience hint (one athlete / group / club).
2. **Design** the block structure from the coach's templates. Do NOT call any tool yet.
3. **Compute TSS** before persisting (formula below).
4. **Call `createTraining` exactly once** with the full structured request. The `title` follows `coach-profile.titleFormat`; block `label`s use the coach's `signatureCues` where applicable.
5. Reply with the Output format below — short, no preamble.
6. If the coach asked to assign it → hand off to `assign-workout.md` with the new `trainingId`.

---

## Structure rules
- Element = **leaf** (has `type`) or **set** (has `repetitions` + `elements`). Never both.
- **One of** `durationSeconds` or `distanceMeters` per leaf. Prefer `durationSeconds` for CYCLING; `distanceMeters` for RUNNING / SWIMMING.
- Block types: `WARMUP`, `INTERVAL`, `STEADY`, `RAMP`, `COOLDOWN`, `FREE`, `PAUSE`, `TRANSITION`.
- SWIM rest: duration → `PAUSE`; distance → `STEADY`.
- RAMP uses `intensityStart` / `intensityEnd`.

### Set rest semantics
- `restDurationSeconds = null` or `0` → **no rest** between reps.
- `restDurationSeconds > 0` and `restIntensity = null/0` → **passive rest** of that length.
- `restDurationSeconds > 0` and `restIntensity > 0` → **active rest** at that % of FTP / threshold pace / CSS.

## Swim-specific
- Always set `strokeType`: `FREESTYLE`, `BACKSTROKE`, `BREASTSTROKE`, `BUTTERFLY`, `IM`, `KICK`, `PULL`, `DRILL`, `CHOICE`.
- Use `distanceMeters`.
- **Send-off intervals**: `sendOffSeconds` for "10×100m on 1:45". Rest is implicit. Do NOT also set `restDurationSeconds`.
- Equipment: `PADDLES`, `PULL_BUOY`, `FINS`, `SNORKEL`, `BAND`, `KICKBOARD`.
- Set `poolLength` (25 or 50). Null for open water.
- Include stroke + equipment in the block `label`.

## Transition blocks (brick / triathlon)
`type: TRANSITION`, `transitionType: T1 | T2`, `durationSeconds`, no intensity. Insert between discipline changes.

## Intensity
- **Cycling**: `intensityTarget` = %FTP.
- **Running**: `intensityTarget` = %Threshold Pace, `cadenceTarget` ≈ 170+.
- **Swimming**: `intensityTarget` = %CSS, `cadenceTarget` = SPM (50-80).

## Zone system
Resolve the zone system in this order: per-request override > `coach-profile.defaultZoneSystem` > the sport's default in `getAthleteContext` → `zoneSystems` > `default-zones.md`. Then branch:

**A. A custom zone system applies (coach profile or a default in `getAthleteContext` → `zoneSystems`)**
- Set `zoneSystemId`, use zone labels in block labels, set `zoneTarget` to the label (e.g. `"Z4"`).
- ONLY IF intensity not in the request: set `intensityTarget` to the **custom zone midpoint**.
- Map coach references like "Z4" to the **custom zone** boundaries — not the project defaults.

**B. No custom zone system → read `default-zones.md`**
- Do NOT set `zoneSystemId`.
- Use the labels and zone names from `default-zones.md` for `zoneTarget` and block `label`.
- ONLY IF intensity not in the request: set `intensityTarget` to the **midpoint column** from `default-zones.md` (use the Intent → zone mapping table when the request only names a session "type" like "endurance" / "sweet spot" / "VO2").
- If the request explicitly gave an intensity (e.g. "85%", "@ threshold"), keep that value and only use `default-zones.md` to pick the matching zone label.

In either case, `default-zones.md` is the source of truth for the **Coggan-style fallback** — never inline numbers from memory.

## TSS — compute BEFORE `createTraining`
**Formula:** `TSS = Σ (durationSeconds × (intensityTarget/100)²) / 36`
Sanity: recovery 30-40 · endurance 50-70 · threshold 70-90 · VO2max 80-120.

## Bulk creation (CRITICAL)
**One `createTraining` per turn.** For multi-workout requests, create one this turn, continue one-per-turn (`✓ [n/total] <title>`).

## Coach-template constraints
1. **Never reference athlete-specific reference values** inside the training. Templates must apply to any athlete on assign.
2. **Visibility** defaults to `coach-profile.defaultVisibility`. If the request mentions a club, set the training's `clubIds` to include it. If a group, set `groupIds` accordingly.
3. **Auto-fields** — omit `id`, `createdAt`, `createdBy` (`coachId` resolved server-side).
4. **JSON only** in arguments; valid, compact.

## Output
- No preamble. No restating the request. No block-by-block prose (it's in the training object).
- Max 3 lines after the call:

```
**Done:** <title>
- <duration>min · TSS <n> · IF <0.xx> · <sport> · <visibility>
```

If you also assigned it: append `· assigned to <athlete/group/club> on <date>`.
On error: one sentence.

## Edge cases
- **`neverInclude` from coach profile** → refuse, redesign without; flag once if the coach insisted.
- **Request exceeds the coach's `maxSessionLength`** → ask before trimming — could be intentional for a long-event block.
- **Ambiguous sport** → ask one short question; don't guess between RUN and BIKE.
- **Coach asked to create + assign in one go** → create this turn, then continue with `assign-workout.md` next turn.

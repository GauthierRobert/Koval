# Workflow — Build a Multi-Week Plan for an Athlete

Construct a `TrainingPlan` (weeks → days → trainings), activate it on an athlete's schedule, and render the first 2 weeks for confirmation.

## Triggers
- "build Alice a 6-week base block"
- "make a training plan for Bob heading into his September race"
- "set up a 4-week recovery block for Carla"
- "clone my classic 8-week build for <athlete>"

## Step 0 — Profile + athlete
- Read `coach-profile.md` for periodization, weekly hours, max hard days, intensity distribution, signature templates, deload cadence, voice/language, neverInclude.
- If `Training method` on `coach-profile.md` is set to anything other than `none`, **also read `training-methods/<slug>.md`** — its periodization style overrides the generic structure in Step 1 below.
- Resolve the target athlete (see `assign-workout.md` step 2 for resolution rules). Refuse if not on the roster.
- `getAthleteProfile(athleteId)` → FTP/threshold/CSS, current CTL/ATL/TSB, weight.
- `listGoals` filtered to the athlete (if MCP exposes athlete goals, otherwise ask).

## Step 1 — Decide structure
From the request + profile + athlete state:
- **Number of weeks** — from the request, default 6 for base, 4 for a peaking block, 3 for taper (defer to `koval-athlete:prep-race.md` workflow rules if it's a taper).
- **Periodization** — if `coach-profile.trainingMethod` is set, use the method's periodization style from `training-methods/<slug>.md`. Otherwise use `coach-profile.periodization`. Common shapes:
  - **Linear** (default): gradual volume → intensity build, deload every 4 weeks.
  - **Block** (method `block-periodization`): 1 HIT-block week + 3 maintenance weeks, repeated. Block weeks own intensity (5 HIT sessions / 7 days); maintenance keeps one HIT day + easy.
  - **Polarized 80-20** (method `polarized`): high volume Z1-Z2 + 1-2 truly hard sessions / week, no grey zone.
  - **Pyramidal** (method `pyramidal`): 1 tempo + 1 VO2 day / week with the rest easy; consider switching to polarized in the final 4 weeks before peak.
  - **Sweet spot** (method `sweet-spot`): 2-3 sweet spot sessions per week (88-94% FTP), lower volume; FasCat-style Base → Build → Specialty.
  - **Norwegian** (method `norwegian`): two double-threshold days per week throughout base + build; race-specific sharpening only in the final 2-3 weeks.
  - **Maffetone** (method `maffetone`): 3-6 month aerobic-only base, anaerobic introduced only after MAF-test plateau. Refuse a peaking block if the athlete hasn't completed the base.
  - **Lydiard** (method `lydiard`): phased — Marathon Conditioning → Hill Resistance → Anaerobic Development → Coordination. Map each phase to a block of weeks in the plan; don't skip the base.
  - **Daniels** (method `daniels`): phased I → II → III → IV (Foundation / R-focus / I-focus / T-focus). Seed paces from the athlete's most recent race; re-check VDOT every 4-6 weeks.
- **Deload** — every `coach-profile.deloadCadence` weeks at `coach-profile.deloadVolume`% of normal volume. Block periodization replaces deload weeks with maintenance weeks.
- **Intensity distribution** — match the method's distribution (e.g. polarized 80/0/20, pyramidal 75/20/5, sweet-spot 65/25/10) or `coach-profile.intensityDistribution` if `method = none`. Apply on a weekly basis, not session basis.
- **Hard / easy days** — apply `coach-profile.maxHardDaysPerWeek` and minimum easy days between hard.

## Step 2 — Clone or create plan shell
Either:
- **Clone a known plan** — `clonePlan(planId, newTitle)`. The plan keeps the structure; reassign the target athlete next.
- **From scratch** — `createPlan(title, sport, targetFtp, durationWeeks, startDate)`.

Title format: `<athlete first name> — <focus> <duration>w` (or whatever `coach-profile.titleFormat` dictates).

## Step 3 — Fill the plan
For each `(weekNumber, dayOfWeek)` slot in the structure:
1. `searchTrainings(query, sport, minDurationMin, maxDurationMin)` to find a matching template in the coach's library (prefer signature templates from `coach-profile`).
2. If nothing fits → hand off to `create-workout.md` to design one (one per turn). Re-enter this loop on the next turn with the new `trainingId`.
3. `addDayToPlan(planId, weekNumber, dayOfWeek, trainingId)`.

To remove a slot later: `removeDayFromPlan(planId, weekNumber, dayOfWeek)`.

## Step 4 — Activate
`activatePlan(planId)` to materialise scheduled workouts on the athlete's calendar from `startDate`.

Pause anytime later with `pausePlan(planId)`.

## Step 5 — Preview
- `getPlanProgress(planId)` for status numbers.
- `getPlanAnalytics(planId)` for projected weekly TSS / hours per week.
- `renderWeekSchedule(weekStart=monday)` for the next 2 weeks; paste verbatim.

## Output format

```
## Plan for <athlete> — <focus>, <N> weeks

<one-sentence strategy>

### Projected load
- Week 1: <hours>h · ~<TSS> TSS
- Week 2: <hours>h · ~<TSS> TSS
- … (compact list)

### This week
<renderWeekSchedule>

### Next week
<renderWeekSchedule>

Plan ID: <planId> — say "swap [day]" or "make [day] easier" to adjust.
```

## Edge cases
- **Athlete is fatigued (TSB < `overreachTsb`)** → start the plan with a recovery week regardless of the request. Mention the choice.
- **Athlete has no FTP** → refuse to set absolute `targetFtp`; ask for it or hand to `koval-athlete:zone-setup.md` (athlete-side).
- **Periodization not set and `trainingMethod = none`** → default to **polarized 80-20**.
- **Coach's `trainingMethod` doesn't suit this athlete's sport** (e.g. `daniels` for a cycling-only athlete) → fall back to generic `polarized` for the plan and mention the mismatch in the output. Don't silently force a misfit.
- **Plan would overlap an existing active plan** → flag, ask whether to pause the existing plan first.
- **Request mentions a race** → after activation, suggest `koval-athlete:prep-race.md` (run by the athlete) or simply add the taper weeks at the tail of this plan.
- **Coach asks "the same plan for every athlete in the group"** → loop one athlete per turn: clone the plan, retarget, activate. Log `✓ [n/total] <athlete>`.

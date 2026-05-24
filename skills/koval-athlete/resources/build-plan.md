# Workflow — Build a Multi-Week Plan for Yourself

Construct a `TrainingPlan` (weeks → days → trainings) on the athlete's own schedule, activate it, and render the first 1–2 weeks for confirmation. Multi-week version of `plan-my-week.md` and the non-race counterpart of `prep-race.md`.

## Triggers
- "build me a 6-week base block"
- "make me a 4-week sweet spot plan"
- "design a 12-week marathon build"
- "I want a training plan for the next 2 months"
- "create a base block for me before my race in 10 weeks"

If the request is **race-driven and within ~6 weeks**, hand off to `prep-race.md` instead — it's tuned for tapering. If the request is **one week only**, use `plan-my-week.md`.

## Step 0 — Profile
Read `athlete-profile.md`. Use `availableDays`, `restDays`, `longSessionDay`, `maxSessionMinutes` (weekday/weekend), `weeklyHours`, `maxHardDaysPerWeek`, `favouriteSessionTypes`, `avoid`, `forbiddenEfforts`, `neverInclude`, `sleepBaseline`, `prescriptionUnit`, `defaultZoneSystem` and the `voice` block. If missing, mention once that running onboarding will personalise the plan, then proceed with defaults.

If `Training method` on the profile is set to anything other than `none`, **also read `training-methods/<slug>.md`** before Step 1 — its periodization style dictates the plan structure. See the matrix in Step 1.

## Step 1 — Decide structure
From the request + profile:
- **Number of weeks** — from the request; defaults: **6** for a base block, **4** for a peaking block, **2–3** for a taper (defer to `prep-race.md` if it's race-driven).
- **Start date** — Monday of the target week; default = next Monday unless the athlete specified.
- **Sport** — from the request. If ambiguous and the athlete trains multiple sports, ask one short question.
- **Periodization** — driven by `Training method` from the profile if set, otherwise default to **polarized 80-20**:
  - **`polarized`**: high volume Z1-Z2 + 1-2 truly hard sessions / week, no grey zone.
  - **`pyramidal`**: 1 tempo + 1 short VO2 day / week, rest easy. Optionally switch to polarized in the final 4 weeks before peak.
  - **`sweet-spot`**: 2-3 sweet-spot sessions per week (88-94% FTP), lower total volume; FasCat-style Base → Build → Specialty.
  - **`norwegian`**: two double-threshold days per week throughout base + build; race-specific sharpening only in the final 2-3 weeks.
  - **`maffetone`**: 3-6 month aerobic-only base, anaerobic introduced only after MAF-test plateau. Refuse a peaking block if the base isn't complete.
  - **`lydiard`**: phased — Marathon Conditioning → Hill Resistance → Anaerobic Development → Coordination. Map each phase to a block of weeks; don't skip the base.
  - **`daniels`**: phased I → II → III → IV (Foundation / R-focus / I-focus / T-focus). Seed paces from the athlete's most recent race; re-check VDOT every 4-6 weeks.
  - **`block-periodization`**: 1 HIT-block week + 3 maintenance weeks, repeated. Block weeks own intensity (5 HIT sessions / 7 days); maintenance keeps one HIT day + easy.
- **Deload** — every 3-4 weeks at ~60% normal volume. Block periodization replaces deload weeks with maintenance weeks.
- **Intensity distribution** — match the method's distribution (polarized 80/0/20, pyramidal 75/20/5, sweet-spot 65/25/10, etc.) on a weekly basis, not session basis. If `method = none`, default to polarized 80-20.
- **Hard / easy days** — honour `maxHardDaysPerWeek` and keep at least one easy day between hard sessions.

## Step 2 — Create the plan shell
`createPlan(title, sport, targetFtp, durationWeeks, startDate)`.

Title format: `<focus> — <duration>w` (e.g. `"Base — 6w"`, `"Sweet Spot Build — 4w"`, `"Marathon Build — 12w"`).

`targetFtp` defaults to the athlete's current FTP from `getMyProfile`. If they're aiming for a specific FTP at plan end, pass that value.

## Step 3 — Fill the plan
For each `(weekNumber, dayOfWeek)` slot in the structure:
1. **`searchTrainings(query, sport, minDurationMin, maxDurationMin)`** to find a matching template in the athlete's own library — prefer existing workouts so the plan reuses their preferred structures.
2. If nothing fits → hand off to `create-workout.md` to design one (one per turn). Re-enter this loop on the next turn with the new `trainingId`.
3. `addDayToPlan(planId, weekNumber, dayOfWeek, trainingId)`.

To remove a slot later: `removeDayFromPlan(planId, weekNumber, dayOfWeek)`.

## Step 4 — Activate
`setPlanStatus(planId, status='ACTIVE', startDate=<plan start Monday>)` so scheduled workouts materialise on the athlete's calendar from `startDate`.

Pause anytime later with `setPlanStatus(planId, status='PAUSED')`; resume with `setPlanStatus(planId, status='ACTIVE')` (no startDate = resume in place).

## Step 5 — Preview
- `getPlanProgress(planId)` for status numbers.
- `getPlanAnalytics(planId)` for projected weekly TSS / hours per week.
- `getMySchedule(from=monday, to=sunday)` for the next 1-2 weeks — build a Mon-Sun grid for each.

## Output format

```
## <focus> plan — <N> weeks · <sport>

<one-sentence strategy, named method if any>

### Projected load
- Week 1: <hours>h · ~<TSS> TSS
- Week 2: <hours>h · ~<TSS> TSS
- … (compact list to the deload)

### This week
<Mon-Sun week grid you build from getMySchedule>

### Next week
<Mon-Sun week grid you build from getMySchedule>

Plan ID: <planId> — say "swap [day]" or "make [day] easier" to adjust.
```

## Edge cases
- **Fatigued (TSB < -25)** → start the plan with a recovery week regardless of the request. Mention the choice.
- **No FTP / threshold pace / CSS for the sport** → hand to `zone-setup.md` first, then resume.
- **`Training method = maffetone` + request for a peaking / VO2 block** → refuse and explain the method's base-first rule; offer to design an aerobic block instead.
- **Method doesn't suit the requested sport** (e.g. `daniels` + cycling-only plan) → fall back to polarized for this plan and note the mismatch on the first line of the output.
- **Plan would overlap an existing active plan** → flag, ask whether to pause the existing plan first.
- **Request mentions a race within the plan window** → add taper weeks at the tail of this plan, or chain into `prep-race.md` if the taper is the whole point.
- **Athlete requests several plans at once** → build the first this turn; continue one-per-turn (`✓ [n/total] <title>`).

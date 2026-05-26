# Workflow — Coach Onboarding

Produce or update `coach-profile.md` so every other koval-coach workflow generates training that matches the coach's voice and rules.

## Triggers
- "set up my coaching profile"
- "configure my coach skills"
- "I want Claude to coach the way I do"
- "save my training philosophy"
- "update my coaching rules" / "update Group N"
- First-time coach in the app

## Step 0 — Role gate
Already enforced by the parent skill. If somehow reached as an ATHLETE, bounce to `koval-athlete`.

## Step 1 — Context (parallel)
- `getAthleteContext` → confirm COACH role (`subject.role`), capture name, default sport, FTP/threshold, your existing `zoneSystems` (zones already in use, with the default per sport), and any previously stored coaching philosophy (`coachPhilosophy` — pre-fill the interview from it)
- `listAthletes` → roster size (informs volume defaults)
- `listPlans(limit=5)` → spot any plans the coach already authored, infer style hints from titles/structure

## Step 2 — Existing profile?
- If `coach-profile.md` exists: ask *"You already have a coaching profile from <date>. Review & update section by section, start over, or just show it?"*
- If updating, jump straight to the section the coach names and skip the rest.
- If `coach-profile.draft.md` exists, offer to resume the previous interview.

## Step 3 — Interview
Ask in **grouped batches**, not one-by-one. Wait for the answer before the next group. Offer "skip / use defaults" per group.

**Question format — prefer multiple choice.** Whenever a question has a known, enumerable set of answers, present it as a **multiple-choice question** (single- or multi-select as appropriate) rather than open prose — it's faster for the coach and keeps answers parseable. Most questions below already list their options (sports, athlete level, training method, periodization model, units, voice/tone, default visibility, etc.) → render those as choices, and always include a "skip / use defaults" choice. Only fall back to **free text** when the answer is genuinely open-ended (e.g. signature drills, favourite session formats, custom never-do rules, coaching cues) — ask those directly. In clients that support structured questions, batch a group's choices together; otherwise list them clearly and let the coach reply with the labels.

### Group 1 — Identity & athletes
- What sports do you primarily coach? (cycling / running / swimming / triathlon / multisport)
- Typical level of your athletes? (beginner / intermediate / competitive / elite / mixed)
- How many athletes do you actively coach right now?
- Do you coach 1:1, in groups (clubs), or both?

### Group 2 — Coaching philosophy
- **Training method?** Read out the menu below and ask the coach to pick one — or **None** for eclectic. See `training-methods.md` for the full menu and `training-methods/<slug>.md` for the per-method playbook that workflows will apply for every athlete you coach.
    1. **Norwegian** — double-threshold days, lactate-guided.
    2. **Polarized 80/20** — strictly easy + truly hard, nothing in between.
    3. **Pyramidal** — lots of easy + tempo-rich + a touch of VO2.
    4. **Sweet Spot / FasCat** — high-density 88–94% FTP, time-crunched cyclists.
    5. **Maffetone (MAF)** — heart-rate-capped aerobic, build the engine.
    6. **Lydiard** — phased base → hills → anaerobic → race.
    7. **Daniels' VDOT** — pace-based E/M/T/I/R from a race time.
    8. **Block Periodization** — concentrated HIIT blocks + maintenance.
    9. **None — eclectic** (default if the coach mixes methods per athlete).
  Persist the chosen slug (or `none`) under `Training method` in the profile. If a method is chosen, **pre-fill the rest of Group 2** from the method's playbook (`training-methods/<slug>.md`) and only ask for overrides.
- Which periodization model do you favour? (linear / block / polarized 80-20 / pyramidal / sweet-spot focused / reverse / undulating) — auto-suggested from the method above when one is chosen.
- Default intensity distribution across a normal week? (e.g. "80% Z1-Z2, 10% Z3, 10% Z4+")
- Do you build in deload weeks? Every how many weeks and at what % of normal volume?
- Do you prefer **time-based** or **TSS-based** prescription?

### Group 3 — Volume & week shape
- Typical weekly hours for your average athlete? (range OK: e.g. 6-10h)
- Max single-session length you'll prescribe to most athletes?
- How many hard days per week max? Minimum easy days between hard sessions?
- Mandatory rest day(s)? Which day usually?
- Long ride/long run day preference? (Sat / Sun / flexible)

### Group 4 — Workout structure preferences
- Default warmup duration and style? (e.g. "15min progressive Z1→Z2 with 3×30s openers")
- Default cooldown?
- Favourite **threshold** session format? (e.g. "2×20 @ 95-100% FTP, 5min recovery")
- Favourite **VO2max** session format? (e.g. "5×4min @ 115%, 4min recovery")
- Favourite **sweet spot** format?
- Favourite **endurance / aerobic** format?
- Any signature drill or block you use a lot? (describe — we'll save it as a template)

### Group 5 — Targets & zones
- Do you prescribe by **% FTP / watts / RPE / HR zones**, or a mix?
- Default zone system? (Coggan 7-zone / polarized 3-zone / custom — paste bounds if custom)
- Cadence targets — do you specify them, and when?
- Runners: pace zones from threshold pace, or HR? Swimmers: CSS-based?

### Group 6 — Recovery, monitoring & alerts
- What TSB threshold do you consider **overreached**? (default -25)
- What TSB do you consider **detrained / too fresh**? (default +25)
- Alerts when an athlete misses N consecutive sessions? If so, N = ?
- Do you require athletes to log RPE / notes / sleep / HRV after each session?

### Group 7 — Voice & communication
- Session **title** format? (e.g. "VO2 — 5×4", "Threshold 2×20", or descriptive prose)
- Session **description** style? (terse cue list / detailed paragraph / motivational / data-only)
- Preferred language for athlete-facing instructions? (English / French / Spanish / …)
- Coaching cues you always use? ("smooth power", "ride the gear", "controlled aggression"…)
- Anything you **never** want in a workout? ("no all-out sprints for masters athletes", "never prescribe fasted rides")

### Group 8 — Club & group context (skip if not in a club)
- Which clubs do you coach in? (auto-pull from profile)
- Any athlete groups you've defined? (auto-pull from groups listing)
- Default visibility for new trainings — **private / shared with athletes only / club-wide / public template**?

## Step 4 — Confirm zone systems
For each sport from Group 1, check `getAthleteContext` → `zoneSystems` for a default. If missing and the coach gave custom bounds in Group 5, `createZoneSystem(...)` to materialise them. Otherwise hand to `koval-athlete:zone-setup.md` for that sport.

## Step 5 — Persist + save
- **Persist your philosophy to the backend → `updateMyContext(sections)`.** As a coach, this writes your coaching philosophy (one document per coach), surfaced as `coachPhilosophy` in every `getAthleteContext` call so the AI coaches the way you do. Pass a map of section title → markdown using the template headings (`Coaching philosophy`, `Volume & week shape`, `Workout structure`, `Targets & zones`, `Recovery & monitoring`, `Voice & communication`).
- Then open `resources/coach-profile.template.md`, copy headings verbatim, fill every placeholder, and save as `coach-profile.md` in this skill's folder as a local working copy. Use `_(using defaults)_` for any skipped group so the file is always complete. The backend is the source of truth; keep the `.md` in sync when you re-run a group.

> Per-athlete context: to record private notes about how you'll coach a *specific* athlete, use `setAthleteCoachingContext(athleteId, sections)` (see `athlete-deepdive.md`). That context is visible only to you and is folded into `getAthleteContext(athleteId)`.

## Step 6 — Show + confirm
Render the resulting profile as a markdown card. Ask: *"Want to tweak anything? Tell me a section name (e.g. 'change Group 4') or say 'looks good' to lock it in."*

## Edge cases
- **Coach refuses or skips a whole group** → write `_(using defaults)_` and pick sensible Coggan / polarized defaults.
- **Coach pastes a long philosophy doc** → parse into the eight groups, fill what you can, ask only the missing pieces.
- **Profile exists, says "update Group 4"** → load file, replay only Group 4, rewrite that section, bump `Last updated`.
- **Multi-sport coach** → ask Groups 4 + 5 once per sport, emit per-sport sub-blocks under each section.
- **Mid-interview drop-off** → save as `coach-profile.draft.md` for "continue my coach setup".

## Follow-ups
- *"Want me to plan this week's sessions for one of your athletes using your new rules?"* → `build-plan.md` or `assign-workout.md`
- *"Want me to review your squad now?"* → `weekly-review.md`
- *"Want me to convert your signature drill into a reusable training template right now?"* → `create-workout.md`

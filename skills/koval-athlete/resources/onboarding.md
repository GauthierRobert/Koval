# Workflow — Athlete Onboarding

Produce or update `athlete-profile.md` so every other koval-athlete workflow has ground truth about availability, goals, recovery rules and voice. Without this file, downstream workflows fall back to generic Coggan / polarized defaults.

## Triggers
- "set up my athlete profile", "onboard me", "I'm new here"
- "tell Claude about my training", "configure my preferences"
- First connect via the Koval MCP connector
- Auto-detected when `getAthleteContext` returns null FTP / threshold pace / CSS for the user's primary sport
- "update my training preferences" (re-run a single section)

## Step 0 — Role gate
Already enforced by the parent skill. If somehow reached as a COACH, bounce to `koval-coach`.

## Step 1 — Context (one call)
- `getAthleteContext` → name, FTP, weight, threshold pace, CSS, CTL/ATL/TSB (`subject` + `trainingLoad`), upcoming `goals`, recent sessions, and any previously stored `athleteContext` (your self-context from a prior onboarding — pre-fill the interview from it). One call replaces the old profile/goals/schedule chain.
- `listZoneSystems` → detect whether zones are configured

## Step 2 — Existing profile?
- If `athlete-profile.md` exists: ask *"You already have a profile from <date>. Review & update section by section, start over, or just show it?"*
- If `athlete-profile.draft.md` exists: offer to **resume** from where the previous interview stopped.
- On "update Group N": load the file, replay only that group, rewrite that section, bump `Last updated`.

## Step 3 — Threshold backstop
For the athlete's primary sport, if the relevant threshold (FTP / threshold pace / CSS) is null, hand to **`zone-setup.md`** first, then resume here. Same trigger the web `OnboardingComponent` uses.

## Step 4 — Interview
Ask in **grouped batches**, not one-by-one. Wait for the answer before the next group. Offer "skip / use defaults" per group. If the athlete pastes a long bio instead, parse what you can into the groups and ask only for the missing pieces.

**Question format — prefer multiple choice.** Whenever a question has a known, enumerable set of answers, present it as a **multiple-choice question** (single- or multi-select as appropriate) rather than open prose — it's faster for the athlete and keeps answers parseable. Most questions below already list their options (sport, level, training method, voice/tone, units, etc.) → render those as choices, and always include a "skip / use defaults" choice. Only fall back to **free text** when the answer is genuinely open-ended (e.g. injuries, "what does a good week look like", custom never-do rules, race names) — ask those directly. In clients that support structured questions, batch a group's choices together; otherwise list them clearly and let the athlete reply with the labels.

### Group 1 — About you
- Primary sport(s)? (cycling / running / swimming / triathlon / multisport)
- Self-described level? (beginner / intermediate / competitive / elite)
- Age or masters category? (optional)
- Working with a human coach in the app, or self-coached?

### Group 2 — Goals & motivation
- Main goal for the next 3 months? (general fitness / weight loss / first race / PR / podium / just enjoy)
- Any A-priority race? (offer `searchRaces` + `createGoal` if yes)
- What does "a good week of training" look like for you?

### Group 3 — Weekly availability
- Realistic training hours per week?
- Available days? (Mon-Sun checklist)
- Days protected for long sessions?
- Mandatory rest day(s)?
- Max single-session length on weekday vs weekend?
- Morning / lunch / evening preference?

### Group 4 — Workout style
- Indoor trainer / outdoor / mix? (cycling)
- Treadmill / road / trail? (running)
- Pool / open water? (swimming)
- Structured intervals or free?
- Favourite session type? (sweet spot / VO2 / endurance / sprints / hills / LSD)
- Type to actively avoid?
- **Training method?** Read out the menu below (one-line each) and ask the athlete to pick one — or **None** for adaptive. See `training-methods.md` for the full menu and `training-methods/<slug>.md` for the per-method playbook.
    1. **Norwegian** — double-threshold days, lactate-guided.
    2. **Polarized 80/20** — strictly easy + truly hard, nothing in between.
    3. **Pyramidal** — lots of easy + tempo-rich + a touch of VO2.
    4. **Sweet Spot / FasCat** — high-density 88–94% FTP, time-crunched cyclists.
    5. **Maffetone (MAF)** — heart-rate-capped aerobic, build the engine.
    6. **Lydiard** — phased base → hills → anaerobic → race.
    7. **Daniels' VDOT** — pace-based E/M/T/I/R from a race time.
    8. **Block Periodization** — concentrated HIIT blocks + maintenance.
    9. **None — adaptive** (default if unsure).
  Persist the chosen slug (or `none`) under `Training method` in the profile.

### Group 5 — Body & recovery
- Injuries / limitations?
- Efforts to avoid? (all-out sprints, max HR, fasted, etc.)
- Sleep quality lately? (good / variable / poor) — biases TSB interpretation
- Do you log RPE / sleep / HRV / notes after sessions?

### Group 6 — Targets & data
- Prescriptions in **% FTP / watts / HR / RPE / pace**?
- Comfortable with TSS, or prefer hours / km?
- Default zone system to use? (auto-pick from `getDefaultZoneSystem(sport)`)

### Group 7 — Voice & communication
- How should session descriptions be written? (terse cue list / detailed / motivational / data-only)
- Preferred language? (English / French / Spanish / …)
- Coaching tone? (firm / encouraging / data-driven / playful)
- Anything Claude should **never** do? ("no 5am sessions", "never Sundays", "no fasted rides")

## Step 5 — Persist to backend
Write everything back so it survives across sessions and clients:
- **Free-form preferences → `updateMyContext(sections)`.** Pass a map of section title → markdown using the template headings (`Identity`, `Goals`, `Weekly availability`, `Workout style`, `Body, recovery & constraints`, `Targets & data`, `Voice & communication`). This is the backend source of truth for your self-context, surfaced to your coach and folded into every future `getAthleteContext` call. This replaces the old "lives only in the .md" limitation — the backend now stores athlete context.
- FTP / weight / threshold pace / CSS → one `updateProfile(ftp?, weightKg?, thresholdPaceSecPerKm?, swimCssSecPer100m?)` call with the fields you captured.
- A-priority goal with date → `createGoal(title, sport, priority, raceDate, raceId?, notes?)`. If found via `searchRaces`, also `linkRaceToGoal`.
- No default zone system for the primary sport → `createZoneSystem(...)` with Coggan defaults, or hand to `zone-setup.md`.

## Step 6 — Compile + save
Open `resources/athlete-profile.template.md`, copy the headings verbatim, fill every placeholder, and save as `athlete-profile.md` in the skills folder this skill lives in. Use `_(using defaults)_` for any group the athlete skipped so the file is always complete. The backend (`updateMyContext` from Step 5) is the source of truth; this `.md` is a local working copy for fast reference — keep the two in sync when you re-run a group.

## Step 7 — Show + confirm
Render the resulting profile as a markdown card. Ask: *"Want to tweak anything? Tell me a section name (e.g. 'change Group 3') or say 'looks good' to lock it in."*

## Edge cases
- **Athlete refuses / skips a group** → `_(using defaults)_` for that section.
- **Long bio pasted** → parse into the 7 groups, only ask the gaps.
- **Profile exists, asks to update Group 3** → load, replay only that group, rewrite, bump timestamp.
- **Multi-sport athlete** → ask Groups 4 + 6 once per sport, emit per-sport sub-blocks.
- **Mid-interview drop-off** → save as `athlete-profile.draft.md` so they can resume with "continue my onboarding".

## Follow-ups
- *"Want me to plan your week now using these rules?"* → `plan-my-week.md`
- *"Want me to check your current form?"* → `form-check.md`
- *"Have a race coming up? I can build you a taper."* → `prep-race.md`
- *"Want to import a workout that fits your style?"* → `find-workout.md`

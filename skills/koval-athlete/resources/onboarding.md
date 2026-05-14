# Workflow — Athlete Onboarding

Produce or update `athlete-profile.md` so every other koval-athlete workflow has ground truth about availability, goals, recovery rules and voice. Without this file, downstream workflows fall back to generic Coggan / polarized defaults.

## Triggers
- "set up my athlete profile", "onboard me", "I'm new here"
- "tell Claude about my training", "configure my preferences"
- First connect via the Koval MCP connector
- Auto-detected when `getMyProfile` returns null FTP / threshold pace / CSS for the user's primary sport
- "update my training preferences" (re-run a single section)

## Step 0 — Role gate
Already enforced by the parent skill. If somehow reached as a COACH, bounce to `koval-coach`.

## Step 1 — Context (parallel)
- `getMyProfile` → name, FTP, weight, threshold pace, CSS, CTL/ATL/TSB
- `listGoals` → pre-fill any goals already set
- `listZoneSystems` → detect whether zones are configured

## Step 2 — Existing profile?
- If `athlete-profile.md` exists: ask *"You already have a profile from <date>. Review & update section by section, start over, or just show it?"*
- If `athlete-profile.draft.md` exists: offer to **resume** from where the previous interview stopped.
- On "update Group N": load the file, replay only that group, rewrite that section, bump `Last updated`.

## Step 3 — Threshold backstop
For the athlete's primary sport, if the relevant threshold (FTP / threshold pace / CSS) is null, hand to **`zone-setup.md`** first, then resume here. Same trigger the web `OnboardingComponent` uses.

## Step 4 — Interview
Ask in **grouped batches**, not one-by-one. Wait for the answer before the next group. Offer "skip / use defaults" per group. If the athlete pastes a long bio instead, parse what you can into the groups and ask only for the missing pieces.

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
Where a field maps to a real model field, write it back:
- FTP / weight / threshold pace / CSS → `updateFtp`, `updateWeight`, `updateThresholdPace`, `updateSwimCss`
- A-priority goal with date → `createGoal(title, sport, priority, raceDate, raceId?, notes?)`. If found via `searchRaces`, also `linkRaceToGoal`.
- No default zone system for the primary sport → `createZoneSystem(...)` with Coggan defaults, or hand to `zone-setup.md`.

Everything else (available days, voice, never-include, sleep baseline…) lives **only** in `athlete-profile.md` — the MCP `User` model has no free-form preferences field.

## Step 6 — Compile + save
Open `resources/athlete-profile.template.md`, copy the headings verbatim, fill every placeholder, and save as `athlete-profile.md` in the skills folder this skill lives in. Use `_(using defaults)_` for any group the athlete skipped so the file is always complete.

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

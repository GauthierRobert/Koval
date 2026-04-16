---
name: koval-athlete-onboarding
description: Use the first time a user connects to Koval, when their FTP / threshold pace / CSS is unset, or when they ask to "set up my profile", "onboard me", "tell Claude about my training", "configure my athlete preferences", "I'm new here". Conducts a structured interview about goals, weekly availability, preferred workout style, recovery rules and communication preferences, then writes athlete-profile.md that every other Koval athlete skill (koval-plan-my-week, koval-find-workout, koval-prep-race, koval-analyze-last-ride, koval-form-check) reads as ground truth so generated plans match the athlete's real life instead of generic Coggan defaults.
---

# Athlete Onboarding

## When to use
- "set up my athlete profile"
- "onboard me", "I'm new here"
- "tell Claude about my training"
- First connect via the Koval MCP connector
- Detected automatically when `getMyProfile` returns null FTP / threshold / CSS for the user's primary sport
- "update my training preferences" (re-run a single section)

**Role gate:** Run only if `getMyProfile().role == "ATHLETE"`. If COACH, reply: *"Looks like you're a coach — run `koval-coach-onboarding` instead to capture your coaching philosophy."*

## Goal

Produce (or update) an `athlete-profile.md` file that captures **how this specific athlete actually trains and lives** — available days, max session length, goals, preferred workout style, recovery rules, voice — so every other Koval athlete skill can read it and generate plans that fit the athlete's real schedule and preferences instead of falling back to defaults.

The canonical schema for the output file ships with this skill at `resources/athlete-profile.template.md`. Treat it as the source of truth for section names and ordering.

## Workflow

1. **Gate + context** (parallel reads):
   - `getMyProfile` → role (must be ATHLETE), name, FTP, weight, threshold pace, CSS, CTL/ATL/TSB
   - `listGoals` → pre-fill any goals already set
   - `listZoneSystems` → detect whether zones are configured

2. **Check for an existing profile**:
   - If `athlete-profile.md` already exists, ask: *"You already have a profile from <date>. Do you want to **review and update** it section by section, **start over**, or **just show it**?"*
   - If `athlete-profile.draft.md` exists, offer to **resume** from where the previous interview left off.
   - On "update", jump straight to the section the athlete names and skip the rest.

3. **Threshold backstop** — for the athlete's primary sport, if FTP (cycling) / threshold pace (running) / CSS (swimming) is null, hand off to `koval-zone-setup` first, then resume here. Same trigger the existing Angular `OnboardingComponent` uses for web users.

4. **Interview** — ask the questions in **grouped batches** (not one-by-one). Wait for the athlete's answer before moving on. Always offer "skip / use defaults" per group.

   ### Group 1 — About you
   - Primary sport(s)? (cycling / running / swimming / triathlon / multisport)
   - Self-described level? (beginner / intermediate / competitive / elite)
   - Age or masters category? (optional)
   - Working with a human coach in the app, or self-coached?

   ### Group 2 — Goals & motivation
   - Main goal for the next 3 months? (general fitness / weight loss / first race / PR a known event / podium / just enjoy riding)
   - Any A-priority race or event date? (offer `searchRaces` and `createGoal` if yes)
   - What does "a good week of training" look like for you?

   ### Group 3 — Weekly availability
   - Realistic training hours per week?
   - Which days are usually available? (Mon-Sun checklist)
   - Which day(s) are protected for long sessions?
   - Mandatory rest day(s)?
   - Max single-session length on a weekday vs weekend?
   - Morning / lunch / evening preference?

   ### Group 4 — Workout style & preferences
   - Indoor trainer / outdoor / mix? (cycling)
   - Treadmill / road / trail? (running)
   - Pool / open water? (swimming)
   - Do you enjoy structured intervals or prefer free riding/running?
   - Favourite session type? (sweet spot / VO2 / endurance / sprints / hills / long slow distance)
   - Any session type you actively dislike or want to avoid?

   ### Group 5 — Body, recovery & constraints
   - Any current injuries or limitations?
   - Effort types you must avoid? (e.g. all-out sprints, max HR work, fasted rides)
   - Sleep quality lately? (good / variable / poor) — used to bias TSB interpretation
   - Do you log RPE / sleep / HRV / notes after sessions?

   ### Group 6 — Targets & data
   - Prescriptions in **% FTP**, **watts**, **HR zones**, **RPE**, or **pace**?
   - Comfortable with TSS as a load metric, or prefer hours / km?
   - Default zone system to use? (auto-pick from `getDefaultZoneSystem(sport)`)

   ### Group 7 — Voice & communication
   - How should session **descriptions** be written? (terse cue list / detailed paragraph / motivational / data-only)
   - Preferred language? (English / French / Spanish / …)
   - Coaching tone? (firm / encouraging / data-driven / playful)
   - Anything Claude should **never** do in your plans? (e.g. "no 5am sessions", "never schedule Sundays", "no fasted rides")

5. **Persist anything we can to the backend**:
   - If FTP / weight / threshold pace / CSS were captured and differ from profile → call `updateFtp(ftp)`, `updateWeight(weightKg)`, `updateThresholdPace(secondsPerKm)`, `updateSwimCss(secondsPer100m)`.
   - If a goal with a date was named → `createGoal(title, sport, priority, raceDate, raceId, notes)`. If a race was found via `searchRaces`, `linkRaceToGoal(raceId, goalId)`.
   - If no default zone system exists for the athlete's primary sport → `createZoneSystem(...)` with Coggan defaults, or hand to `koval-zone-setup`.
   - Everything else (available days, voice, never-include, sleep quality…) lives **only** in `athlete-profile.md` — the MCP `User` model has no free-form preferences field.

6. **Compile + save** the profile as `athlete-profile.md` in the same skills folder this skill lives in. Use `resources/athlete-profile.template.md` as the structural template — copy the headings verbatim, fill in every placeholder, and write `_(using defaults)_` in any group the athlete chose to skip.

7. **Show the result** as a markdown card and ask: *"Want to tweak anything? Tell me a section name (e.g. 'change Group 3') or say 'looks good' to lock it in."*

## Output format — `athlete-profile.md`

The output file follows the exact schema in `resources/athlete-profile.template.md`. Open the template, copy its structure verbatim, then fill in each placeholder.

## Edge cases
- **Role is COACH** → bounce to `koval-coach-onboarding`.
- **Athlete refuses or skips a whole group** → write `_(using defaults)_` in that section so the file is still complete.
- **Athlete pastes a long bio instead of answering** → parse it into the 7 groups, fill in what you can, then ask only the missing pieces.
- **Profile exists and athlete says "update Group 3"** → load the file, replay only Group 3, rewrite that section, bump `Last updated`.
- **Multi-sport athlete** → ask Groups 4 + 6 once per sport (cycling / running / swimming) and emit per-sport sub-blocks.
- **Mid-interview drop-off** → save what you have so far as `athlete-profile.draft.md` so the athlete can resume later with "continue my onboarding".

## Follow-ups to suggest after saving
- *"Want me to plan your week now using these rules?"* → `koval-plan-my-week`
- *"Want me to check your current form?"* → `koval-form-check`
- *"Have a race coming up? I can build you a taper."* → `koval-prep-race`
- *"Want to import a workout that fits your style?"* → `koval-find-workout`

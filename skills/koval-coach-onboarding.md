---
name: koval-coach-onboarding
description: Use ONLY when the user is a COACH and asks to set up, configure, customize or update their personal coaching style — phrases like "set up my coaching profile", "teach me your coaching rules", "configure my coach skill", "onboard me as a coach", "I want you to coach like me", "save my coaching philosophy". Conducts a structured interview about periodization, volume, intensity distribution, workout structure, recovery rules and communication style, then writes a personalised coach profile that every other Koval coach skill (training creation, plan building, athlete review, club sessions) reads as ground truth.
---

# Coach Onboarding

## When to use
- "set up my coaching profile"
- "configure my coach skills"
- "I want Claude to coach the way I do"
- "save my training philosophy"
- "update my coaching rules" (re-run a section)
- First-time coach in the app

**Role gate:** Only run this skill if `getMyProfile().role == "COACH"`. Otherwise reply: *"This setup is for coaches — your account is set as ATHLETE. Switch roles in your profile if you also coach."*

## Goal

Produce (or update) a `coach-profile` document that captures **how this specific coach prescribes training**, so every other coach skill (`koval-plan-my-week`, `koval-find-workout`, `koval-coach-weekly-review`, training creation flows, club session creation) can read it and generate trainings that match the coach's voice and rules — instead of falling back to generic Coggan defaults.

## Workflow

1. **Gate + context** (parallel):
   - `getMyProfile` → confirm COACH role, capture name, default sport, FTP/threshold, club membership
   - `listAthletes` → roster size (informs volume defaults)
   - `listZoneSystems` → existing zone systems already in use
   - `listPlans` (limit 5) → spot any plans the coach already authored, infer style hints from titles/structure

2. **Check for an existing profile**:
   - If a `coach-profile.md` already exists for this coach, ask: *"You already have a coaching profile from <date>. Do you want to **review and update** it section by section, **start over**, or **just show it**?"*
   - If updating, jump straight to the section the coach names and skip the rest.

3. **Interview** — ask the questions in **grouped batches** (not one at a time). Wait for the coach's answer before moving to the next group. Always offer a "skip / use defaults" option per group.

   ### Group 1 — Identity & athletes
   - What sports do you primarily coach? (cycling / running / swimming / triathlon / multisport)
   - What's the typical level of your athletes? (beginner / intermediate / competitive / elite / mixed)
   - How many athletes do you actively coach right now?
   - Do you coach 1:1, in groups (clubs), or both?

   ### Group 2 — Coaching philosophy
   - Which periodization model do you favour? (linear / block / polarized 80-20 / pyramidal / sweet-spot focused / reverse / undulating)
   - What's your default intensity distribution across a normal week? (e.g. "80% Z1-Z2, 10% Z3, 10% Z4+")
   - Do you build in deload weeks? If so, every how many weeks and at what % of normal volume?
   - Do you prefer **time-based** or **TSS-based** prescription?

   ### Group 3 — Volume & week shape
   - Typical weekly hours for your average athlete? (range is fine: e.g. 6-10h)
   - Max single-session length you'll prescribe to most athletes?
   - How many hard days per week max? Minimum easy days between hard sessions?
   - Mandatory rest day(s)? Which day usually?
   - Long ride/long run day preference? (Sat / Sun / flexible)

   ### Group 4 — Workout structure preferences
   - Default warmup duration and style? (e.g. "15min progressive Z1→Z2 with 3×30s openers")
   - Default cooldown? (e.g. "10min easy spin")
   - Favourite **threshold** session format? (e.g. "2×20 @ 95-100% FTP, 5min recovery")
   - Favourite **VO2max** session format? (e.g. "5×4min @ 115%, 4min recovery")
   - Favourite **sweet spot** format?
   - Favourite **endurance / aerobic** format?
   - Any signature drill or block you use a lot? (describe it — we'll save it as a template)

   ### Group 5 — Targets & zones
   - Do you prescribe by **% FTP**, **watts**, **RPE**, **HR zones**, or a mix?
   - Which zone system do you want as default? (Coggan 7-zone / polarized 3-zone / custom — and if custom, paste the bounds)
   - Cadence targets — do you specify them, and if so when?
   - For runners: pace zones based on threshold pace, or HR? For swimmers: CSS-based?

   ### Group 6 — Recovery, monitoring & alerts
   - What TSB threshold do you consider **overreached**? (default -25)
   - What TSB do you consider **detrained / too fresh**? (default +25)
   - Do you want alerts when an athlete misses N consecutive sessions? If so, N = ?
   - Do you require athletes to log RPE / notes / sleep / HRV after each session?

   ### Group 7 — Voice & communication
   - How do you want session **titles** formatted? (e.g. "VO2 — 5×4", "Threshold 2×20", or descriptive prose)
   - How do you want session **descriptions** written? (terse cue list / detailed paragraph / motivational / data-only)
   - Preferred language for athlete-facing instructions? (English / French / Spanish / …)
   - Any phrases or coaching cues you always use? ("smooth power", "ride the gear", "controlled aggression"…)
   - Anything you **never** want in a workout? (e.g. "no all-out sprints for masters athletes", "never prescribe fasted rides")

   ### Group 8 — Club & group context (skip if not in a club)
   - Which clubs do you coach in? (auto-pull from profile)
   - Any athlete groups you've defined? (auto-pull from `listGroups`)
   - Default visibility for new trainings — **private**, **shared with athletes only**, **club-wide**, **public template**?

4. **Confirm zone systems exist**:
   - For each sport the coach picked in Group 1, check `getDefaultZoneSystem(sport)`.
   - If any are missing and the coach gave custom bounds in Group 5, call `createZoneSystem(...)` to materialise them.
   - Otherwise hand off to `koval-zone-setup` for that sport before continuing.

5. **Compile the profile** into the structure below and save it as `coach-profile.md` in the user's skills folder (same directory this skill lives in). Also store a one-line digest in conversation memory so subsequent skills can detect it.

6. **Show the result** to the coach as a markdown card and ask: *"Want to tweak anything? Tell me a section name (e.g. 'change Group 4') or say 'looks good' to lock it in."*

## Output format — `coach-profile.md`

```markdown
---
name: koval-coach-profile
description: Personalised coaching rules for <Coach Name>. Loaded automatically by every Koval coach skill (training creation, plan building, athlete review, club session creation) so generated workouts match this coach's philosophy, volume targets, intensity distribution, recovery rules and voice. Re-run koval-coach-onboarding to update.
---

# Coach Profile — <Coach Name>

_Last updated: <YYYY-MM-DD>_

## Identity
- **Sports:** <…>
- **Athlete level:** <…>
- **Roster size:** <N>
- **Coaching mode:** <1:1 / club / both>

## Philosophy
- **Periodization:** <model>
- **Intensity distribution:** <%Z1-2 / %Z3 / %Z4+>
- **Deload cadence:** every <N> weeks at <X>% volume
- **Prescription unit:** <time | TSS>

## Volume & week shape
- **Weekly hours:** <range>
- **Max session length:** <hh:mm>
- **Hard days / week:** max <N>, min <N> easy days between
- **Rest day:** <day>
- **Long session day:** <day>

## Default workout templates
- **Warmup:** <…>
- **Cooldown:** <…>
- **Threshold:** <…>
- **VO2max:** <…>
- **Sweet spot:** <…>
- **Endurance:** <…>
- **Signature drill:** <name + structure>

## Targets & zones
- **Prescription style:** <% FTP / watts / RPE / HR / mix>
- **Default zone system:** <name> (id: <…>)
- **Cadence targets:** <when / how>
- **Sport-specific:** <pace / HR / CSS notes>

## Recovery & monitoring
- **Overreach TSB threshold:** <value>
- **Detrained TSB threshold:** <value>
- **Missed-session alert:** after <N> consecutive
- **Required logs:** <RPE / notes / sleep / HRV>

## Voice & communication
- **Title format:** <pattern>
- **Description style:** <terse / detailed / motivational / data>
- **Language:** <…>
- **Signature cues:** <list>
- **Never include:** <list>

## Club & group context
- **Clubs:** <names>
- **Groups:** <names>
- **Default visibility:** <private / athletes / club / public>

## How other skills should use this
Any Koval coach skill that creates a `Training`, `ScheduledWorkout`, `ClubSession` or `Plan` MUST:
1. Read this file first.
2. Use the templates above as building blocks (warmup, cooldown, signature drills).
3. Respect the volume, hard-day, intensity-distribution and rest rules.
4. Apply the title format, description style, language and signature cues.
5. Honour the "never include" list.
6. Use the default zone system unless the coach explicitly overrides per request.
```

## Edge cases
- **User isn't a coach** → role gate message, do not run.
- **Coach refuses or skips a whole group** → write `_(using defaults)_` in that section and pick sensible Coggan/polarized defaults so the file is still complete.
- **Coach pastes a long philosophy doc instead of answering** → parse it into the eight groups, fill in what you can, then ask only the missing pieces.
- **Profile already exists and the coach says "update Group 4"** → load the existing file, replay only Group 4, rewrite that section, bump `Last updated`.
- **Multi-sport coach** → ask Groups 4 and 5 once per sport (cycling / running / swimming) and emit per-sport sub-blocks under each section.
- **Mid-interview drop-off** → save what you have so far as a draft (`coach-profile.draft.md`) so the coach can resume later with "continue my coach setup".

## Follow-ups to suggest after saving
- *"Want me to plan this week's sessions for one of your athletes using your new rules?"* → `koval-plan-my-week`
- *"Want me to review your squad now?"* → `koval-coach-weekly-review`
- *"Want me to convert your signature drill into a reusable training template right now?"* → call `createTraining` with the drill structure.

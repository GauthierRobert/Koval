---
name: koval-coach-profile
description: Personalised coaching rules for <Coach Name>. Loaded automatically by every workflow under the koval-coach skill (onboarding, weekly-review, athlete-deepdive, create-workout, assign-workout, build-plan, club-sessions, publish-club-gazette) so generated workouts and plans match this coach's philosophy, volume targets, intensity distribution, recovery rules and voice. Re-run onboarding to update.
---

# Coach Profile — <Coach Name>

_Last updated: <YYYY-MM-DD>_

## Identity
- **Sports:** <…>
- **Athlete level:** <…>
- **Roster size:** <N>
- **Coaching mode:** <1:1 / club / both>

## Philosophy
- **Periodization:** <linear / block / polarized 80-20 / pyramidal / sweet-spot / reverse / undulating>
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
- **Overreach TSB threshold:** <value, default -25>
- **Detrained TSB threshold:** <value, default +25>
- **Missed-session alert:** after <N> consecutive (default 7 days)
- **Required logs:** <RPE / notes / sleep / HRV>

## Voice & communication
- **Title format:** <pattern, e.g. "VO2 — 5×4">
- **Description style:** <terse cue list / detailed paragraph / motivational / data>
- **Language:** <English / French / …>
- **Signature cues:** <list of phrases the coach always uses>
- **Never include:** <list>

## Club & group context
- **Clubs:** <names>
- **Groups:** <names>
- **Default visibility:** <private / athletes / club / public>

## How other workflows should use this
Any koval-coach workflow that creates a `Training`, `ScheduledWorkout`, `ClubSession` or `Plan` MUST:
1. Read this file first.
2. Use the templates above as building blocks (warmup, cooldown, signature drills).
3. Respect the volume, hard-day, intensity-distribution and rest rules.
4. Apply the title format, description style, language and signature cues.
5. Honour the "never include" list.
6. Use the default zone system unless the coach explicitly overrides per request.
7. For per-athlete writes, also cross-check the athlete's `forbiddenEfforts` (if their athlete-profile is reachable) — coach rules and athlete rules combine, athlete-side wins on safety.

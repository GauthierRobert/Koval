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

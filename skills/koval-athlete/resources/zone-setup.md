# Workflow — Zone Setup

Capture the athlete's threshold value and create a personalised zone system for the relevant sport.

## Triggers
- "set up my zones"
- "I just did an FTP / ramp test, my new FTP is …"
- "create my running pace zones"
- "set my CSS"
- "reset my zones"
- Triggered as a sub-step of onboarding when threshold is null

## Workflow

1. **Current state** — `getAthleteContext` → check `subject.ftp`, `subject.functionalThresholdPace`, `subject.criticalSwimSpeed`.
2. **Determine sport** from the request:
   - Power → CYCLING (FTP in watts)
   - Pace → RUNNING (threshold pace, sec/km)
   - Swim → SWIMMING (CSS, sec/100m)
3. **Capture or confirm the threshold**:
   - If provided ("my FTP is 280"), use it.
   - Otherwise ask: *"What's your FTP / threshold pace / CSS? Or share a recent ramp/test result and I'll estimate."*
   - Ramp test peak power → FTP ≈ `0.75 × peakPower`.
   - 20-min test best power → FTP ≈ `0.95 × P20`.
   - Running threshold ≈ 5k race pace − 5 sec/km (rough), or 10k race pace.
4. **Persist** — one `updateProfile` call, passing only the field for the relevant sport (leave the others null):
   - Cycling: `updateProfile(ftp=<watts>)`
   - Running: `updateProfile(thresholdPaceSecPerKm=<sec>)` — e.g. 4:10/km = 250
   - Swimming: `updateProfile(swimCssSecPer100m=<sec>)` — e.g. 1:35/100m = 95
5. **Existing default zone system?** — `getDefaultZoneSystem(sportType)`.
   - If one exists: *"You already have a default zone system. Replace or keep?"*
   - If none, proceed.
6. **Create the zone system** — `createZoneSystem(name, sportType, referenceType, referenceName, referenceUnit, zones)`.

### Default zone bounds (Coggan-style)

Use the canonical tables in **`default-zones.md`** as the starting point for `createZoneSystem` — pass each row's range and label straight through unless the user requested custom bounds. The same tables are the project-wide fallback when no Default Zone System exists, so editing them in one place keeps zone-setup and workout creation aligned.

7. **Confirm** with `listZoneSystems` and render a one-line summary.
8. **Suggest follow-up** — *"Want me to capture the rest of your training preferences now (available days, goals, voice)?"* → `onboarding.md`.

## Output format

```
Done. <Sport> zones set.

**Threshold:** <value> <unit>
**Zone system:** <name> — <N> zones

| Zone | Range | Label |
|------|-------|-------|
| Z1   | 0-55% | Active recovery |
| Z2   | 56-75%| Endurance |
| ...

Future workouts will use these for targets and TSS.
```

## Edge cases
- **No threshold value and can't estimate** → suggest a 20-minute test (cycling) or 30-min time trial (running) and offer to schedule via `find-workout.md`.
- **Custom zone bounds requested** → pass the user's values straight to `createZoneSystem`.
- **User wants to delete an old zone system** → confirm, then `deleteZoneSystem(systemId)`.
- **Profile already complete** → after persisting threshold, exit cleanly. No need to re-run onboarding.

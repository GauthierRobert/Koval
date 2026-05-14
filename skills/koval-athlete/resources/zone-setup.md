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

1. **Current state** — `getMyProfile` → check `ftp`, `functionalThresholdPace`, `criticalSwimSpeed`.
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
4. **Persist**:
   - Cycling: `updateFtp(ftp)`
   - Running: `updateThresholdPace(secondsPerKm)` — e.g. 4:10/km = 250
   - Swimming: `updateSwimCss(secondsPer100m)` — e.g. 1:35/100m = 95
5. **Existing default zone system?** — `getDefaultZoneSystem(sportType)`.
   - If one exists: *"You already have a default zone system. Replace or keep?"*
   - If none, proceed.
6. **Create the zone system** — `createZoneSystem(name, sportType, referenceType, referenceName, referenceUnit, zones)`.

### Default zone bounds (Coggan-style)

**Cycling (% FTP):**

| Zone | Range  | Label              |
|------|--------|--------------------|
| Z1   | 0-55   | Active recovery    |
| Z2   | 56-75  | Endurance          |
| Z3   | 76-90  | Tempo              |
| Z4   | 91-105 | Threshold          |
| Z5   | 106-120| VO2max             |
| Z6   | 121-150| Anaerobic capacity |
| Z7   | 151-300| Neuromuscular      |

**Running (% threshold pace, slower → smaller %):**

| Zone | Range   | Label       |
|------|---------|-------------|
| Z1   | 0-75    | Recovery    |
| Z2   | 76-87   | Endurance   |
| Z3   | 88-94   | Tempo       |
| Z4   | 95-102  | Threshold   |
| Z5   | 103-110 | VO2max      |
| Z6   | 111-130 | Anaerobic   |

**Swimming (% CSS speed):**

| Zone | Range   | Label       |
|------|---------|-------------|
| Z1   | 80-87   | Recovery    |
| Z2   | 88-93   | Endurance   |
| Z3   | 94-100  | Tempo       |
| Z4   | 101-105 | Threshold   |
| Z5   | 106-145 | VO2 / Sprint|

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

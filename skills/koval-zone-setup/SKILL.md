---
name: koval-zone-setup
description: Use during onboarding or when the user asks to set up, configure, or reset their training zones, FTP, threshold pace or CSS — phrases like "set up my zones", "configure my FTP", "I just did a ramp test", "create my power zones". Captures the threshold value via Koval MCP profile updates and creates a personalised zone system for the relevant sport.
---

# Zone Setup

## When to use
- "set up my zones"
- "I just did an FTP test, my new FTP is 280W"
- "create my running pace zones"
- New-user onboarding flow
- "reset my zones"

## Workflow

1. **Check current state** → call `getMyProfile` to see what's already set (FTP, functionalThresholdPace, criticalSwimSpeed).
2. **Determine sport** from the user's request:
   - Power → CYCLING (FTP in watts)
   - Pace → RUNNING (threshold pace, sec/km)
   - Swim → SWIMMING (CSS, sec/100m)
3. **Capture or confirm the threshold value**:
   - If the user provided one ("my FTP is 280"), use it.
   - Otherwise ask: "What's your FTP / threshold pace / CSS? (Or tell me your last ramp test result and I'll estimate.)"
   - If they give a ramp test peak power, FTP ≈ `0.75 × peakPower`.
4. **Persist the threshold**:
   - Cycling: `updateFtp(ftp)`
   - Running: `updateThresholdPace(secondsPerKm)` (e.g. 4:10/km = 250)
   - Swimming: `updateSwimCss(secondsPer100m)` (e.g. 1:35/100m = 95)
5. **Check for an existing default zone system** for that sport via `getDefaultZoneSystem(sportType)`.
   - If one exists, ask: "You already have a default zone system. Replace it or keep it?"
   - If none, proceed to create.
6. **Create a Coggan-style zone system** via `createZoneSystem(name, sportType, referenceType, referenceName, referenceUnit, zones)` with:
   - Cycling (FTP): Z1 0-55, Z2 56-75, Z3 76-90, Z4 91-105, Z5 106-120, Z6 121-150, Z7 151-300
   - Running (threshold pace): equivalent 7-zone system based on % of threshold pace
   - Swimming (% CSS speed): Z1 80-87 Recovery, Z2 88-93 Endurance, Z3 94-100 Tempo, Z4 101-105 Threshold, Z5 106-145 VO2max / Sprint
7. **Confirm** with `listZoneSystems` and a one-line summary.
8. **Suggest follow-up:** *"Want me to capture the rest of your training preferences now (available days, goals, voice)?"* → hand off to `koval-athlete-onboarding` so the new athlete ends up with a full profile, not just zones.

## Output format

```
✅ <Sport> zones set up.

**Threshold:** <value> <unit>
**Zone system:** <name> — <N> zones

| Zone | Range | Label |
|---|---|---|
| Z1 | 0-55% | Active recovery |
| Z2 | 56-75% | Endurance |
| ... |

Your future workouts will use these to compute targets and TSS.
```

## Edge cases
- **No threshold value provided and user can't estimate** → suggest a 20-minute test (cycling) or 30-min time trial (running) and offer to schedule it via `koval-find-workout`.
- **User wants custom zone bounds** → accept their values and pass them straight to `createZoneSystem`.
- **User asks to delete an old zone system** → use `deleteZoneSystem(systemId)` after confirming.

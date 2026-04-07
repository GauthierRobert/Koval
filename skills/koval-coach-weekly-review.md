---
name: koval-coach-weekly-review
description: Use ONLY when the user is a COACH and asks to review their athletes — phrases like "review my athletes", "how are my athletes doing", "weekly check-in on my squad", "any athletes flagged". Pulls each athlete's profile, PMC and recent sessions via Koval MCP and renders a coach dashboard with alerts for overreach or inactivity.
---

# Coach Weekly Review

## When to use
- "review my athletes"
- "weekly check-in on my squad"
- "is anyone overreaching"
- "who hasn't trained this week"

**Role gate:** Only run this skill if `getMyProfile().role == "COACH"`. Otherwise reply: "This skill is for coaches — your account is set as ATHLETE."

## Workflow

**Step 0 — Load coach profile.** Read `coach-profile.md` if present. Pull the coach's configured `overreachTsb` (default `-25`), `detrainedTsb` (default `+25`) and `missedSessionAlertN` (default `7` days) — use these instead of the hardcoded values below. Also honour the configured `language` and `voice` when writing the action items. If missing, suggest running `koval-coach-onboarding` once and proceed with defaults.

1. Call `getMyProfile` — confirm the user is a COACH. Abort otherwise.
2. Call `listAthletes` to get the coach's athlete roster.
3. For each athlete (in parallel batches of 3-5 to keep latency reasonable):
   - `getAthleteProfile(athleteId)` → name, FTP, current CTL/ATL/TSB
   - `getAthletePmc(athleteId, from=today-14d, to=today)` → for last-week TSS sum and form trend
   - `getAthleteRecentSessions(athleteId, limit=3)` → most recent 3 sessions
4. Compute per-athlete flags (thresholds from `coach-profile.md`, defaults shown):
   - **🔴 Overreached**: TSB < `overreachTsb` (default -25)
   - **🟡 Inactive**: no sessions in the last `missedSessionAlertN` days (default 7)
   - **🟡 Detraining**: TSB > `detrainedTsb` (default +25)
   - **🟢 OK**: everything else
5. Render a dashboard table sorted by severity (red → yellow → green).

## Output format
[koval-plan-my-week.md](koval-plan-my-week.md)
```
## Squad review — week of <monday>

**<N> athletes** · <X red> · <Y yellow> · <Z green>

| Athlete | Form (TSB) | 7d TSS | Last session | Flag |
|---|---|---|---|---|
| Alice | -12 | 420 | Mon · VO2 4×4 | 🟢 |
| Bob   | -32 | 580 | Sun · 4h endurance | 🔴 Overreached |
| Carla |  +8 |  90 | (8d ago) | 🟡 Inactive |

### Action items
- **Bob** — TSB -32, prescribe 2-3 easy days
- **Carla** — no activity in 8 days, send a check-in message
```

Keep prose minimal — the table + action items are the value.

## Edge cases
- **No athletes assigned** → "You don't have any athletes yet. Athletes can connect via your coach code."
- **Athlete has no training history** → render TSB as `—`, flag as `🟡 New athlete (no data)`.
- **Coach asks about one athlete by name** → skip the listAthletes step, jump straight to that athlete and render a single-athlete card with `getAthletePowerCurve` added in.

# Workflow — Weekly Squad Review

Athlete dashboard: TSB, 7d TSS, last session, alerts on overreach / inactivity / detraining.

## Triggers
- "review my athletes"
- "weekly check-in on my squad"
- "is anyone overreaching"
- "who hasn't trained this week"

## Step 0 — Profile
Read `coach-profile.md`. Pull configured `overreachTsb` (default -25), `detrainedTsb` (default +25), `missedSessionAlertN` (default 7 days). Honour `language` and `voice` when writing action items.

## Workflow

1. `getMyProfile` → confirm COACH (already gated, double-check).
2. `listAthletes` → coach's roster.
3. For each athlete (parallel batches of 3-5 to keep latency reasonable):
   - `getAthleteProfile(athleteId)` → name, FTP, current CTL/ATL/TSB
   - `getAthletePmc(athleteId, from=today-14d, to=today)` → last-week TSS sum and form trend
   - `getAthleteRecentSessions(athleteId, limit=3)` → most recent 3 sessions
4. Compute per-athlete flag (thresholds from profile):
   - **🔴 Overreached** — TSB < `overreachTsb`
   - **🟡 Inactive** — no sessions in the last `missedSessionAlertN` days
   - **🟡 Detraining** — TSB > `detrainedTsb`
   - **🟡 New athlete** — no PMC data yet
   - **🟢 OK** — everything else
5. Render the dashboard sorted by severity (red → yellow → green).

## Output format

```
## Squad review — week of <monday>

**<N> athletes** · <X red> · <Y yellow> · <Z green>

| Athlete | Form (TSB) | 7d TSS | Last session     | Flag |
|---------|-----------:|-------:|------------------|------|
| Alice   | -12        | 420    | Mon · VO2 4×4    | 🟢 |
| Bob     | -32        | 580    | Sun · 4h endurance| 🔴 Overreached |
| Carla   | +8         | 90     | (8d ago)         | 🟡 Inactive |

### Action items
- **Bob** — TSB -32, prescribe 2-3 easy days
- **Carla** — no activity in 8 days, send a check-in message
```

Keep prose minimal — the table + action items are the value. Action items in the coach profile's `language`.

## Follow-ups
- *"Want me to deep-dive on <athlete>?"* → `athlete-deepdive.md`
- *"Want me to build a recovery week for <Bob>?"* → `build-plan.md` (preset: recovery)
- *"Send a message to <Carla>?"* — note that messaging is not yet exposed via MCP; suggest the coach use the in-app chat.

## Edge cases
- **No athletes assigned** → *"You don't have any athletes yet. Athletes can connect via your coach code."*
- **Athlete has no training history** → render TSB as `—`, flag `🟡 New athlete`.
- **Coach asks about one athlete by name** → skip `listAthletes`, jump straight to `athlete-deepdive.md` for that athlete.
- **Profile has a non-default `missedSessionAlertN = 14`** → use it; do not hardcode 7 days.

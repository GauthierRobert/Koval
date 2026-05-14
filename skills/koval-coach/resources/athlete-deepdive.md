# Workflow — Single-Athlete Deep Dive

Full picture of one athlete: profile, form trend, power curve, last sessions, next scheduled sessions, with concrete next-step suggestions for the coach.

## Triggers
- "deep dive on Alice"
- "how's Bob doing"
- "look at Carla's last 4 weeks"
- "show me everything on <athlete>"

Also invoked automatically from `weekly-review.md` when the coach drills into a flagged athlete.

## Step 0 — Profile + resolve athlete
- Read `coach-profile.md` for thresholds and voice.
- Resolve the athlete: pass the name to `listAthletes` and match. If ambiguous, ask one short question with the candidates. If the athlete is not on the coach's roster, refuse: *"That athlete isn't on your roster."*

## Workflow

In parallel:
- `getAthleteProfile(athleteId)` — name, sport focus, FTP/threshold/CSS, weight, current CTL/ATL/TSB, role
- `getAthletePmc(athleteId, from=today-90d, to=today)` — 90-day form trend
- `getAthleteRecentSessions(athleteId, limit=10)` — last 10 sessions
- `getAthletePowerCurve(athleteId, from=today-90d, to=today)` — recent power curve (cycling)
- `getAthleteSchedule(athleteId, from=today, to=today+14d)` — what's already booked
- `getBestPowerCurve(athleteId)` — all-time PRs (cycling)

Render with the markdown helpers:
- `renderPmcReport` for the form trend
- `renderPowerCurveReport` for the curve (cycling only)
- `renderSessionSummary(sessionId)` for the **most recent** session — paste verbatim

## Output format

```
## <Athlete name> — deep dive

**Sport:** <…>  ·  **FTP:** <…>  ·  **Weight:** <…>
**Current form:** CTL <…> · ATL <…> · TSB <…>  → <flag emoji + label>

### Form trend (90d)
<renderPmcReport>

### Last session
<renderSessionSummary>

### Power curve (last 90d, cycling)
<renderPowerCurveReport>

### Next 14 days on the calendar
| Date     | Workout           | Status   |
|----------|-------------------|----------|
| Tue 14   | VO2 5×4           | PENDING  |
| Thu 16   | Tempo 3×15        | PENDING  |
| Sun 19   | Long Z2 3h        | PENDING  |

### Coach actions to consider
- <bullet 1, derived from TSB / missed sessions / curve trend>
- <bullet 2>
- <bullet 3>
```

The action bullets should reference the coach's profile thresholds. Examples:
- TSB < `overreachTsb` → "Cut Thursday's tempo, replace with a recovery spin."
- No sessions in `missedSessionAlertN` days → "Reach out — eight days quiet."
- 30d power curve > 5% below all-time at the athlete's race duration → "Race-specific sharpening is overdue."

## Follow-ups
- *"Want me to build him a recovery week?"* → `build-plan.md`
- *"Want me to swap Thursday for an easy spin?"* → `assign-workout.md` (with `rescheduleWorkout` / `unassignWorkout` + new `assignTraining`)
- *"Want me to create a sharpening session for his race in 3 weeks?"* → `create-workout.md`

## Edge cases
- **Athlete has no recent sessions** → drop the "last session" and "power curve" sections, just show the profile + scheduled work.
- **Cycling tools called on a runner** → fall back to volume/pace charts (`renderVolumeReport`); skip power curve.
- **Athlete profile is private to athlete (free-form prefs)** → you cannot read `athlete-profile.md` — work from MCP data only.
- **Coach explicitly asks for a longer window** → adjust `from` to `today-180d` or whatever.

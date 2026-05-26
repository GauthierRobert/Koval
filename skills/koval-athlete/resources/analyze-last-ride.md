# Workflow — Analyze Last Ride / Session

Pull the most recent completed session and render a markdown summary card with overview, blocks, power curve and PR flags.

## Triggers
- "analyse my last ride / run / session"
- "how was my workout this morning"
- "recap yesterday's training"
- "did I PR anything"
- Automatic when the user just uploaded a session

## Workflow

1. `getSessions(mode='recent', limit=1)` → most recent `CompletedSession`. Capture `sessionId` and `sport`.
2. Build the session card yourself: `getSessionDetail(sessionId)` for the overview (duration, avg power/HR, TSS, IF, RPE, distance), `getSessionBlocks(sessionId)` for the per-block table, and — for cycling — `getSessionPowerCurve(sessionId)` for a power-curve bar row. Lay it out as a compact overview table + a blocks table.
3. **PR check** (cycling only) — call `getPersonalRecords` and compare against the session power curve from step 2. If any duration's best from this session ties or beats the all-time PR, prepend a `**🏆 New PR:**` line right after the verdict.
4. **Planned vs actual** — if the session has a `scheduledWorkoutId`, call `getScheduledWorkoutDetail(scheduledWorkoutId)` and append a short delta line (planned TSS vs actual TSS, planned duration vs actual). If `getSessionDetail` returns an `alignmentScore` (a % where 100 = on plan), surface it too — e.g. *"Plan alignment: 104%"*. This rating is set by you (in the app's session view) or by your coach; it's a judgement of how well you matched the prescription, not just a number — mention the athlete can rate it in the app if it's unset.

## Output format

Lead with **one** verdict sentence in the profile's `voice` and `language` (e.g. "Solid endurance ride.", "Hard session — that VO2 block hit hard."), then the session card you built from the data tools. Optional PR line goes between the verdict and the card. Keep prose minimal — the card has the numbers.

```
<verdict sentence>
**🏆 New PR:** <duration> · <value> (was <previous>)   ← only if a PR fired

<session card: overview table + blocks table + (cycling) power-curve row>

Planned vs actual: TSS <planned>→<actual> · Duration <planned>→<actual> min   ← only if linked
```

## Edge cases
- **No recent sessions** → *"I don't see any completed sessions yet. Upload a FIT file or sync from Strava and try again."*
- **Session has no FIT data** → `getSessionPowerCurve` returns empty; just omit the power-curve row.
- **No FTP set** → TSS / IF will be missing. Suggest running `zone-setup.md`.
- **User asks for a specific session, not the last** → use `getSessionDetail(sessionId)` if you already have an ID, otherwise `getSessions(mode='recent', limit=10)` and let the user pick.
- **Running / swimming session** → skip the cycling-only PR check; describe the effort using pace / CSS instead.

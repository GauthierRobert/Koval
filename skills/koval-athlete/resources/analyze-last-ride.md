# Workflow — Analyze Last Ride / Session

Pull the most recent completed session and render a markdown summary card with overview, blocks, power curve and PR flags.

## Triggers
- "analyse my last ride / run / session"
- "how was my workout this morning"
- "recap yesterday's training"
- "did I PR anything"
- Automatic when the user just uploaded a session

## Workflow

1. `getRecentSessions` with `limit=1` → most recent `CompletedSession`. Capture `sessionId` and `sport`.
2. `renderSessionSummary(sessionId)` → ready-to-paste markdown card with overview table, per-block breakdown, and (for cycling sessions with FIT data) a power curve bar chart. Paste it verbatim.
3. **PR check** (cycling only) — call `getPersonalRecords` and compare against the session's power curve from step 2. If any duration's best from this session ties or beats the all-time PR, prepend a `**🏆 New PR:**` line right after the verdict.
4. **Planned vs actual** — if the session has a `scheduledWorkoutId`, call `getScheduledWorkoutDetail(scheduledWorkoutId)` and append a short delta line (planned TSS vs actual TSS, planned duration vs actual).

## Output format

Lead with **one** verdict sentence in the profile's `voice` and `language` (e.g. "Solid endurance ride.", "Hard session — that VO2 block hit hard."), then paste `renderSessionSummary` verbatim. Optional PR line goes between the verdict and the card. Keep prose minimal — the card has the numbers.

```
<verdict sentence>
**🏆 New PR:** <duration> · <value> (was <previous>)   ← only if a PR fired

<renderSessionSummary output verbatim>

Planned vs actual: TSS <planned>→<actual> · Duration <planned>→<actual> min   ← only if linked
```

## Edge cases
- **No recent sessions** → *"I don't see any completed sessions yet. Upload a FIT file or sync from Strava and try again."*
- **Session has no FIT data** → `renderSessionSummary` skips the power curve section automatically — fine.
- **No FTP set** → TSS / IF will be missing. Suggest running `zone-setup.md`.
- **User asks for a specific session, not the last** → use `getSessionDetail(sessionId)` if you already have an ID, otherwise `getRecentSessions(limit=10)` and let the user pick.
- **Running / swimming session** → skip the cycling-only PR check; describe the effort using pace / CSS instead.

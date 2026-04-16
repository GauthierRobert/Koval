---
name: koval-analyze-last-ride
description: Use when the user asks to analyze, summarize, review or recap their most recent training session, ride, run or swim. Triggers include "how was my last ride", "analyze my last workout", "what did I do yesterday", "recap my session". Pulls the most recent CompletedSession from the Koval MCP server, renders a full markdown summary card (sport, duration, avg power/HR, TSS/IF, RPE, blocks, power curve) and highlights any new personal records.
---

# Analyze Last Ride

## When to use
- "analyze my last ride / run / session"
- "how was my workout this morning"
- "recap yesterday's training"
- After the user completes and uploads a session

## Workflow

1. Call `getRecentSessions` with `limit=1` to find the most recent `CompletedSession`. Capture its `sessionId`.
2. Call `renderSessionSummary(sessionId)` — returns a ready-to-paste markdown card with overview table, per-block breakdown and (for cycling sessions with a FIT file) a power curve bar chart.
3. **Optional PR check** — if the session is cycling, call `getPersonalRecords` and compare against the session's power curve. If any duration's best from this session ties or beats the user's all-time PR, mention it explicitly.
4. **Optional context** — if the session is part of a scheduled workout, call `getScheduledWorkoutDetail` to surface the planned target vs the actual.

## Output format

Lead with a one-sentence verdict ("Solid endurance ride", "Hard session — that VO2 block hit hard"), then drop the markdown card from `renderSessionSummary` verbatim. If you found a PR, add a `**🏆 New PR:**` line right after the verdict. Keep prose minimal — the card already has all the numbers.

## Edge cases
- **No recent sessions** → reply: "I don't see any completed sessions yet. Upload a FIT file or sync from Strava and try again."
- **Session has no FIT data** → `renderSessionSummary` will skip the power curve section automatically; that's fine.
- **No FTP set** → TSS/IF will be missing from the card. Suggest the user run the `koval-zone-setup` skill or set FTP via `updateFtp`.

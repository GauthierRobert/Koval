---
name: koval-form-check
description: Use when the user asks about their current form, fitness, fatigue, freshness or readiness — phrases like "what's my form", "am I fresh", "should I race this weekend", "how's my fitness", "am I overtraining". Pulls the user's PMC (Performance Management Chart) from the last ~90 days via the Koval MCP server, renders a CTL/ATL/TSB sparkline + interpretation, and gives a one-paragraph human read of where they stand.
---

# Form Check

## When to use
- "what's my form"
- "am I fresh enough to race"
- "how's my fitness trending"
- "should I take a rest day"
- "am I overreaching"

## Workflow

1. Call `getMyProfile` to confirm the user has training history (CTL/ATL/TSB present). If all three are 0/null, reply: "I don't have enough training history to compute form yet — log a few sessions and try again."
2. Compute a 90-day window: `to = today`, `from = today - 90 days`.
3. Call `renderPmcReport(from, to)` — returns markdown with the CTL/ATL/TSB sparkline + a key-value table including current values and a baked-in TSB band interpretation.
4. Drop the rendered markdown into the reply.

## Output format

Open with one short sentence describing the user's form ("You're fresh and race-ready", "Solid build — fatigue is productive right now", "You're deep in fatigue, prioritise recovery"). Then paste the `renderPmcReport` output verbatim. Optionally close with a single actionable suggestion based on TSB:

- **TSB > +25** — "Consider adding intensity, you're starting to detrain."
- **+5 to +25** — "Good window to race or hit a key session."
- **-10 to +5** — "Neutral — train as planned."
- **-30 to -10** — "Productive overload, keep an eye on sleep and HRV."
- **TSB < -30** — "High fatigue — consider an easy day or full rest."

## Edge cases
- **New user (no PMC data)** → tell them to log sessions first.
- **User asks about a specific date** → adjust the `from`/`to` window accordingly (e.g. "form last month" = previous calendar month).

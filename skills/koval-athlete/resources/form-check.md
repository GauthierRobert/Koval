# Workflow — Form Check (PMC)

Pull the user's Performance Management Chart for the last ~90 days, render the CTL/ATL/TSB sparkline and give a one-paragraph read.

## Triggers
- "what's my form"
- "am I fresh enough to race"
- "how's my fitness trending"
- "should I take a rest day"
- "am I overreaching"

## Workflow

1. `getMyProfile` → confirm training history (CTL/ATL/TSB present). If all three are 0/null: *"I don't have enough training history to compute form yet — log a few sessions and try again."*
2. Compute window: `to = today`, `from = today - 90 days`. Honour a user-specified window if given ("form last month" → previous calendar month).
3. `getPmcData(from, to)` → array of daily `{date, ctl, atl, tsb}` points. Build the report yourself: a unicode sparkline of CTL across the window plus a small table of the latest CTL (fitness) / ATL (fatigue) / TSB (form).
4. Add **one** prose verdict in the profile's voice/language plus a single TSB-band suggestion (see below), keyed off the most recent TSB value.

## Output format

```
<one-sentence verdict — "fresh and race-ready" / "productive build" / "deep in fatigue, recover">

CTL  ▁▂▃▄▅▆▇█   (fitness over the window)

| Metric | Value |
|--------|-------|
| CTL (fitness) | <latest> |
| ATL (fatigue) | <latest> |
| TSB (form)    | <latest> |

<one actionable suggestion based on TSB>
```

### TSB-band suggestions

| TSB band     | Suggestion |
|--------------|------------|
| > +25        | "Consider adding intensity, you're starting to detrain." |
| +5 to +25    | "Good window to race or hit a key session." |
| -10 to +5    | "Neutral — train as planned." |
| -30 to -10   | "Productive overload, watch sleep and HRV." |
| < -30        | "High fatigue — consider an easy day or full rest." |

## Edge cases
- **New user, no PMC** → tell them to log sessions first.
- **Custom window** → adjust `from` / `to` (e.g. "form last month" → calendar month range, "this year" → Jan 1 → today).
- **Athlete profile present and `sleepBaseline = poor`** → soften any "go hard" suggestion to "train as planned, prioritise recovery".

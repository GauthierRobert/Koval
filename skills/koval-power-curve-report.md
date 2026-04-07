---
name: koval-power-curve-report
description: Use when the user asks about their power curve, peak power, mean-maximal power or personal records — phrases like "show my power curve", "what are my best efforts", "where are my PRs", "how's my sprint power". Pulls all-time PRs plus 30-day and 90-day power curves from the Koval MCP server and renders a side-by-side markdown comparison.
---

# Power Curve Report

## When to use
- "show my power curve"
- "what are my best 5-minute / 20-minute efforts"
- "where am I peaking right now"
- "compare my recent power to my all-time best"

## Workflow

1. Call `getPersonalRecords` — returns all-time best mean-maximal watts at each standard duration (5s, 15s, 30s, 1m, 2m, 5m, 10m, 20m, 30m, 1h, 1.5h, 2h).
2. Compute two date windows: `to = today`, `from30 = today - 30d`, `from90 = today - 90d`.
3. In parallel:
   - `renderPowerCurveReport(from30, to)` — last 30 days
   - `renderPowerCurveReport(from90, to)` — last 90 days
4. Build a comparison markdown table with columns `Duration | All-time | 90d | 30d | Δ vs all-time` so the user can spot where they're sharp or detrained.

## Output format

```
## Your power curve

**All-time PRs vs recent windows**

| Duration | All-time | 90d | 30d | Δ |
|---|---|---|---|---|
| 5s | <w> W | <w> W | <w> W | <pct> |
| ... |

### Last 30 days
<rendered bar chart>

### Last 90 days
<rendered bar chart>

**Sharpest right now:** <durations where 30d is within 2% of all-time>
**Furthest off form:** <durations where 30d is 10%+ below all-time>
```

## Edge cases
- **No cycling FIT data** → reply: "I don't see any cycling sessions with power data — upload some FIT files to build a power curve."
- **Only running/swimming user** → mention that power curves are cycling-only at the moment, suggest `koval-form-check` instead.
- **User asks for a custom window** → respect it (e.g. "last year" → from = today - 365d).

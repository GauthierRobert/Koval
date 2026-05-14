# Workflow — Power Curve Report

Pull all-time PRs plus 30-day and 90-day power curves and render a side-by-side markdown comparison.

## Triggers
- "show my power curve"
- "what are my best 5-min / 20-min efforts"
- "where am I peaking right now"
- "compare my recent power to my all-time best"

## Workflow

1. `getPersonalRecords` → all-time best mean-maximal watts at each standard duration (5s, 15s, 30s, 1m, 2m, 5m, 10m, 20m, 30m, 1h, 1.5h, 2h).
2. Compute windows: `to = today`, `from30 = today - 30d`, `from90 = today - 90d` (or the user's custom window).
3. In parallel:
   - `renderPowerCurveReport(from30, to)` — last 30 days
   - `renderPowerCurveReport(from90, to)` — last 90 days
4. Build the comparison table: `Duration | All-time | 90d | 30d | Δ vs all-time` (Δ as a signed percentage).

## Output format

```
## Your power curve

**All-time PRs vs recent windows**

| Duration | All-time | 90d   | 30d   | Δ     |
|----------|----------|-------|-------|-------|
| 5s       | <w> W    | <w> W | <w> W | <pct> |
| 1m       | …        | …     | …     | …     |
| 5m       | …        | …     | …     | …     |
| 20m      | …        | …     | …     | …     |

### Last 30 days
<renderPowerCurveReport(30d) verbatim>

### Last 90 days
<renderPowerCurveReport(90d) verbatim>

**Sharpest right now:** <durations where 30d is within 2% of all-time>
**Furthest off form:** <durations where 30d is 10%+ below all-time>
```

## Edge cases
- **No cycling FIT data** → *"I don't see any cycling sessions with power data — upload some FIT files to build a power curve."*
- **Only running / swimming user** → mention power curves are cycling-only; suggest `form-check.md` or a pace-based equivalent.
- **Custom window** → respect it ("last year" → `from = today - 365d`).
- **Profile has `defaultZoneSystem` cycling** → annotate each duration row with the zone label (`Z5 — VO2max`, `Z4 — Threshold`, …) using the system's bounds.

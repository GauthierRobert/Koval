# Workflow — Score a Session's Plan Alignment

Judge how well an athlete's *completed* session matched the workout that was *scheduled* for it, and record it as a single percentage on the session: **100 = on plan, above 100 = they exceeded the prescribed work, below 100 = they fell short.** The score shows up as a colored badge in the athlete's history, on the session detail, and on the **Alignment** tab of your athlete deep-dive (evolution over time — green inside 90–110%, red outside).

This is a *coaching judgement*, not a formula. The app can pre-compute a rough deterministic estimate (see "The app's estimate" below), but the authoritative score is the one you set — weigh the dimensions with a coach's eye.

## Triggers
- "score Alice's last session against the plan"
- "how well did Bob hit Tuesday's intervals?"
- "rate alignment for <athlete>'s <session>"

Also invoked naturally from `weekly-review.md` / `athlete-deepdive.md` when a completed session is clearly off its prescription and worth flagging.

## Step 0 — Profile + resolve athlete
- Read `coach-profile.md` for voice and for what *this* coach cares about (a Norwegian-double-threshold coach weighs zone discipline heavily; a sweet-spot coach weighs TSS/duration).
- Resolve the athlete via `listAthletes`; refuse if not on the roster.

## Step 1 — Gather planned vs actual (session must be linked)
A session can only be scored against a plan when it is **linked to a scheduled workout**. Confirm the link, then pull both sides:

- `getSessions(athleteId, mode='recent')` → find the session, note its `id` and that it has a `scheduledWorkoutId`. (`alignmentScore` on the summary is the current effective rating, if any.)
- `getSessionDetail(sessionId, athleteId)` → actual TSS, IF, duration, avg/normalized power.
- `getSessionBlocks(sessionId, athleteId)` → per-block **target vs actual power** — the backbone of interval execution quality.
- `getScheduledWorkoutDetail(scheduledWorkoutId)` → the prescription (planned blocks, target intensities, estimated TSS/IF/duration).

If the session is **not** linked, say so — there's nothing to score against — and offer to link it first.

## Step 2 — Weigh the six dimensions
Compare actual against planned across these, then form one overall percentage. Suggested emphasis (adapt to the coach profile):

| Dimension | What to compare | Reads from |
|---|---|---|
| **Power** | avg/NP vs prescribed target % | `getSessionDetail` vs `getScheduledWorkoutDetail` |
| **Duration** | actual vs planned seconds | both |
| **TSS** | actual vs estimated TSS | both |
| **IF** | actual vs estimated IF | both |
| **Zone repartition** | time-in-zone shape vs intended distribution | session zones vs planned block intensities |
| **Block comparison** | per-interval actual vs target power — were the hard bits hit? | `getSessionBlocks` |

Judgement notes:
- A long Z2 ride run 20 min short but bang-on intensity is ~90%, not 50% — duration and intensity both matter, neither alone.
- For interval sessions, **block comparison dominates**: nailing 5×5 @ 110% but cutting the warm-up is still a near-100% session.
- Going *harder* than prescribed is **above 100%**, but flag it — over-cooking an easy day is a problem, not a win. Say so in the note.
- Keep the number in **0–300%**; in practice almost everything lands 70–130%.

## Step 3 — Record it
Set the score **and** the reasoning in one call — the note body is the "why":

```
appendCoachNote(
  athleteId,
  body: "Hit all 5 VO2 reps at target (305–312 W vs 300 W planned); cooldown skipped. Strong execution.",
  sessionId,
  alignmentScore: 104
)
```

- `alignmentScore` requires `sessionId`. It sets the **coach/AI** rating (badge source = AI), which takes precedence over the athlete's self-rating on the badge and chart.
- Omit `alignmentScore` for a plain note. Omit `sessionId` for a general (non-session) note.
- One `appendCoachNote` per turn (SKILL.md rule 3). For several sessions, iterate one per turn (`✓ [n/total] Tue 14 — 104%`).

## The app's estimate
When neither athlete nor coach has rated a linked session, the app offers a **deterministic estimate** (a weighted actual-vs-plan ratio over the dimensions above) as a starting suggestion in its rating modal. Treat it as a sanity check, not the answer — your score is the authoritative one. The athlete may also have set their own self-rating; you can validate it (adopt the same number) or override it.

## Output format
```
## <Athlete> — <Session title> · alignment <score>%  <🟢/🔴>

| Dimension | Planned | Actual | |
|---|---|---|---|
| Power     | 300 W   | 308 W  | ↑ |
| Duration  | 60 min  | 54 min | ↓ |
| TSS       | 95      | 92     | ≈ |
| Intervals | 5×5 @110% | 5×5 hit | ✓ |

<one-line verdict in the coach's voice>
→ Recorded as <score>% with a coach note.
```

## Edge cases
- **Session not linked to a plan** → no baseline; offer to link, don't invent a score.
- **Unstructured ride (free/Z2) with no blocks** → score on duration + TSS + IF only; skip block comparison.
- **Athlete already self-rated** → show their number, then your judgement; setting a coach score validates or overrides it (it wins on the badge).
- **Athlete not on roster** → refuse; never read another coach's athlete.

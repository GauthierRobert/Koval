# Koval Skills

End-user skills for the **Koval Training Planner AI** MCP connector. Drop these into Claude Desktop or Claude.ai alongside the connector and Claude will route every training-related request to the right internal workflow — analyse a session, plan a week, prep a race, design a workout, review your athletes, publish the club gazette, and more.

## What is this?

The Koval backend exposes **~70 MCP tools** covering trainings, schedules, plans, sessions, analytics, coaching, clubs and zones. A skill is a markdown playbook bundle that tells Claude *which tools to chain* for each workflow — so you can say "analyze my last ride" instead of walking it through the tool graph yourself.

There are exactly **two skills**, scoped by role:

| Skill            | Audience | Workflows in `resources/` |
|------------------|----------|---------------------------|
| `koval-athlete`  | Athletes | onboarding · zone-setup · analyze-last-ride · form-check · power-curve-report · find-workout · create-workout · plan-my-week · prep-race |
| `koval-coach`    | Coaches  | onboarding · weekly-review · athlete-deepdive · create-workout · assign-workout · build-plan · club-sessions · publish-club-gazette |

Each skill's `SKILL.md` is the **router** — it carries the broad description that triggers Claude to load the skill, gates by role (`ATHLETE` vs `COACH`), and points to the per-workflow playbook in `resources/`. All output is **markdown only** (unicode bar charts, sparklines, tables) — no image rendering required.

## Skill structure

```
koval-athlete/
├── SKILL.md                              ← router + cross-cutting rules
└── resources/
    ├── athlete-profile.template.md       ← canonical schema for athlete-profile.md
    ├── onboarding.md
    ├── zone-setup.md
    ├── analyze-last-ride.md
    ├── form-check.md
    ├── power-curve-report.md
    ├── find-workout.md
    ├── create-workout.md
    ├── plan-my-week.md
    └── prep-race.md

koval-coach/
├── SKILL.md
└── resources/
    ├── coach-profile.template.md         ← canonical schema for coach-profile.md
    ├── onboarding.md
    ├── weekly-review.md
    ├── athlete-deepdive.md
    ├── create-workout.md
    ├── assign-workout.md
    ├── build-plan.md
    ├── club-sessions.md
    └── publish-club-gazette.md
```

This is the format Claude Desktop and Claude.ai's Skill upload UI expect. Each skill is distributed as a `.zip` archive containing the skill directory at its root — the build script below produces them.

## Building the ZIPs

```bash
node skills/package-skills.mjs
```

Zero-dependency script — walks every immediate subdirectory of `skills/` that contains a `SKILL.md`, zips it, and writes the artifact to `skills/dist/<skill-name>.zip`. Re-run any time you edit a workflow file. `dist/` is git-ignored.

Output:
```
skills/dist/koval-athlete.zip
skills/dist/koval-coach.zip
```

## Installing

1. Install the **Koval** MCP connector in Claude Desktop or Claude.ai (see project README for connector setup + JWT).
2. Build the ZIPs once with `node skills/package-skills.mjs`.
3. Install each skill matching your role:
   - **Claude.ai**: open a Project → **Skills** → **Upload skill** → pick `koval-athlete.zip` (athletes) or `koval-coach.zip` (coaches).
   - **Claude Desktop**: extract the ZIP into your Claude Desktop skills folder (path varies by OS — see Claude Desktop docs), or drop the skill *directory* in directly. Restart the app.
4. Start a new conversation with the connector enabled and try one of the trigger phrases below.

Athletes only need `koval-athlete`. Coaches can install both if they also train themselves; otherwise `koval-coach` is enough.

## Trigger phrases

### `koval-athlete`
- "set up my athlete profile", "onboard me", "I'm new here"
- "set my FTP to 280", "I just did a ramp test", "create my pace zones"
- "analyse my last ride", "how was my workout", "did I PR"
- "what's my form", "am I fresh", "should I race", "TSB"
- "show my power curve", "best 5-min effort"
- "find me a sweet spot workout", "do I have a 90min Z2 ride"
- "create a 5x5 VO2", "design a 90min Z2 ride", "make a brick workout"
- "plan my week", "what should I do this week"
- "build me a taper", "I have a race in N weeks"

### `koval-coach`
- "set up my coaching profile", "save my coaching philosophy"
- "review my athletes", "weekly squad check-in", "is anyone overreaching"
- "deep dive on Alice", "how's Bob doing"
- "create a 5x5 VO2 for my Tuesday group", "design a sweet spot for the club"
- "assign this to Alice for Tuesday", "schedule X for the whole group"
- "build Alice a 6-week base block"
- "create a club session for Sunday", "make this a recurring session"
- "publish the gazette", "compile this week's newsletter"

## Profiles & onboarding

Each skill produces a persistent profile file that the other workflows read as ground truth:

- **`koval-athlete`** writes `athlete-profile.md` — available days, goals, max session length, never-include rules, voice. Schema at `koval-athlete/resources/athlete-profile.template.md`. Read by every other athlete workflow.
- **`koval-coach`** writes `coach-profile.md` — periodization, volume, signature workouts, alert thresholds, tone. Schema at `koval-coach/resources/coach-profile.template.md`. Read by every other coach workflow.

Run onboarding once when you first connect — re-run any time your training reality changes. Without these files, downstream workflows fall back to generic Coggan / polarized defaults.

## Notes

- Skills work best when your **profile** is complete (FTP, weight, threshold pace, CSS) — otherwise TSS/IF estimates fall back to defaults.
- The `render*` tools (`renderPowerCurveReport`, `renderPmcReport`, `renderVolumeReport`, `renderWeekSchedule`, `renderSessionSummary`, `renderFriReport`) return ready-to-paste markdown — your client doesn't need any special rendering.
- Coach workflows check the `COACH` role and require an existing coach-athlete relationship in the app. Athlete workflows refuse if your account is `COACH` (run `koval-coach` instead).

# Koval Skills

End-user skills for the **Koval Training Planner AI** MCP connector. Drop these into Claude Desktop or Claude.ai alongside the connector and Claude will automatically pick the right workflow when you ask it to analyse a ride, plan a week, prep a race, etc.

## What is this?

The Koval backend exposes **~70 MCP tools** covering trainings, schedules, plans, sessions, analytics, coaching, clubs and zones. A skill is a short markdown playbook that tells Claude *which tools to chain* for a common workflow — so you can say "analyze my last ride" instead of manually walking it through the tool graph.

All output is **markdown only** (unicode bar charts, sparklines, tables) — no images required. It renders natively in any Claude client.

## Installing

1. Install the **Koval** MCP connector in Claude Desktop or Claude.ai (see project README for connector setup + JWT).
2. Copy the `.md` files in this directory into your Claude skills folder:
   - **Claude Desktop**: drop into your skills directory (see Claude Desktop docs for the path on your OS).
   - **Claude.ai**: upload via the Skills UI on a Project.
3. Start a new conversation with the connector enabled and try one of the trigger phrases below.

## The skills

| Skill | Trigger phrase | What it does |
|---|---|---|
| [koval-analyze-last-ride](koval-analyze-last-ride.md) | "analyze my last ride" | Pulls your most recent session, renders a markdown summary card with blocks + power curve, flags new PRs |
| [koval-form-check](koval-form-check.md) | "what's my form", "am I fresh" | PMC sparkline of CTL/ATL/TSB + interpretation of your current form band |
| [koval-plan-my-week](koval-plan-my-week.md) | "plan my training week" | Looks at your form, goals and recent load, proposes 5-7 sessions, schedules them, renders a week grid |
| [koval-prep-race](koval-prep-race.md) | "build me a taper", "I have a race in N weeks" | Builds a taper plan from a goal/race, schedules it, renders the next two weeks |
| [koval-power-curve-report](koval-power-curve-report.md) | "show my power curve" | All-time PRs + 30d and 90d power curves side-by-side |
| [koval-coach-weekly-review](koval-coach-weekly-review.md) | "review my athletes" (COACH only) | Athlete dashboard: TSB, 7d TSS, last session, alerts on overreach / inactivity |
| [koval-find-workout](koval-find-workout.md) | "find me a sweet spot workout" | Searches your library by sport / duration / title and offers to schedule one |
| [koval-zone-setup](koval-zone-setup.md) | "set up my zones" | Onboarding: capture FTP, create personalised zone systems |

## Notes

- Skills work best when your **profile** is complete (FTP, weight, threshold pace, CSS) — otherwise TSS/IF estimates fall back to defaults.
- The `render*` tools (`renderPowerCurveReport`, `renderPmcReport`, `renderVolumeReport`, `renderWeekSchedule`, `renderSessionSummary`) return ready-to-paste markdown — your client doesn't need any special rendering.
- Coach skills check the `COACH` role and require an existing coach-athlete relationship in the app.

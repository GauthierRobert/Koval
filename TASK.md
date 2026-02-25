# Task Backlog

---

## Calendar & Scheduling

- **Overdue workouts inline with week panel** — Overdue scheduled workouts should appear on the same row/line as the current week panel header, not below it as a separate section.

- **Reschedule workouts via drag & drop** — In the calendar view, allow dragging a scheduled workout card to a different day to reschedule it. Should update the `scheduledDate` on drop and reflect the change immediately in the UI.

- **Month view for calendar** — Add a month display mode alongside the existing week view. Toggle between Week / Month with a selector. Month view should show workout cards compactly (label + TSS or sport icon).

- **Differentiate sports in workout load** — Calendar workout load bars/indicators must use distinct colors per sport (cycling, running, swimming, etc.). Add a label showing the workout load value inside the indicator; fall back to a tooltip when the element is too narrow to display text.

- **Link executed sessions to scheduled workouts in calendar** — Completed workout sessions should appear in the calendar. When a session date matches a scheduled workout, link them automatically. If the match is ambiguous, expose a manual picker so the user can associate a session with a scheduled workout.

- **Disable "complete" action for future workouts** — The button/action to mark a workout as completed must be disabled (greyed out, not hidden) when the scheduled date is in the future.

---

## History Panel

- **Highlight selected history item** — The currently selected history workout entry must receive a specific CSS class (e.g. `selected`) so it can be styled distinctly from the rest of the list.

- **Sync toggle state with displayed content** — When the user switches the history/workout toggle, the main panel must also switch: history mode shows the last selected history entry; workout mode shows the last selected workout plan. The two states must not bleed into each other.

- **Sort workouts and history by most recent** — Both the workout list and the history list must be sorted descending by date (most recent first) at all times.

- **Compact history workout cards** — History workout boxes should match the visual density of workout plan cards. Reduce padding, font sizes, and spacing so more items are visible without scrolling.

- **Remove Power Progression from history panel** — Delete the "Power Progression" block/chart from the history detail panel. It is not needed there.

- **Scheduled training card: remove top border** — In the week / overdue panel, the top border of scheduled training cards should be hidden to give them a cleaner, flush appearance.

---

## Analysis Component

- **RPE fallback when TSS is zero or absent** — If a session has a TSS value of zero (or no TSS at all — e.g. unstructured outdoor ride), check for an RPE value and display it instead. If RPE is also absent, render an inline input that lets the user set the RPE directly inside the analysis component and save it to the session.

- **TSS from RPE for unstructured workouts** — Implement a TSS estimation based on RPE + duration for sessions that have no power data (external / non-structured workouts). The computed TSS should be stored on the session and feed into all downstream calculations (PMC, weekly load, etc.).

- **Remove close button from analysis panel** — The explicit close/dismiss button inside the analysis component must be removed. Closing should be handled by the parent context (e.g. clicking outside, navigating away).

- **Inline title and metrics; de-emphasise metrics** — Place the workout title and the key metrics (TSS, duration, NP, etc.) on the same line. Metrics should look secondary to the title: remove their background chip, reduce font size, and lower contrast.

---

## PMC (Performance Management Chart)

- **Include completed workouts in PMC history** — The PMC calculation (`MetricsService.getPmc`) currently only pulls from history sessions. Ensure that workouts marked as completed (with a recorded TSS or RPE-derived TSS) are also included in the daily TSS aggregation used to compute CTL / ATL / TSB.

---

## Strava Integration

- **Retrieve FIT files from Strava** — Implement an endpoint + service to fetch FIT activity files from Strava for the authenticated user. Requires the user to have granted all metric scopes (`activity:read_all`). Parse the FIT data and ingest relevant metrics (power, HR, cadence, TSS) into the session/history store.

---

## UX / Settings

- **Auto-close user settings modal on save** — When the user saves their reference values (FTP, weight, etc.) in the settings modal, automatically close the modal after a successful save. No manual dismiss required.
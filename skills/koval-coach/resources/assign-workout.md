# Workflow — Assign / Schedule a Workout

Take an existing training (template) and put it on an athlete's, group's, or club's calendar.

## Triggers
- "assign this to Alice for Tuesday"
- "schedule the 5×5 VO2 for my Tuesday group"
- "send this workout to my club for Sunday"
- "reschedule Bob's Thursday session to Friday"
- "remove Carla's Saturday workout"

## Step 0 — Profile
Read `coach-profile.md` for `defaultVisibility`, `restDay`, `longSessionDay`. Use them to sanity-check the date the coach picked (warn if assigning a hard session on the configured rest day).

## Workflow

1. **Resolve the training**:
   - If the coach said "this", use the most recently created/discussed `trainingId` in conversation.
   - Otherwise `searchTrainings(query, sport, minDurationMin, maxDurationMin)` and confirm with the coach if multiple matches.
2. **Resolve the target**:
   - **One athlete** — match name via `listAthletes`. If ambiguous, ask one short question with the candidate list. If not on the roster, refuse.
   - **A group** — list the coach's groups; map "Tuesday group" → group id. Iterate athletes in that group.
   - **A club session** — use `createClubSession` instead of `assignTraining` (see `club-sessions.md`).
3. **Resolve the date(s)**:
   - "Tuesday" → next Tuesday from today.
   - "next week's Sat" → Saturday of next ISO week.
   - "every Tuesday for 4 weeks" → 4 separate `assignTraining` calls, one per turn.
4. **Sanity check the date**:
   - Against `coach-profile.restDay` (warn if hard session lands there).
   - Against the athlete's existing schedule via `getAthleteSchedule(athleteId, from, to)` — flag conflicts but don't block.
5. **Assign**:
   - One athlete: `assignTraining(trainingId, athleteId, date, status='PENDING')`.
   - Whole group: iterate athletes, **one `assignTraining` per turn**, log `✓ [n/total] <athlete> — <date>`.
6. **Confirm** with the affected athletes' `renderWeekSchedule` for that week — paste verbatim.

## Reschedule / unassign

- Reschedule: `rescheduleWorkout(scheduledWorkoutId, newDate)`.
- Drop: `unassignWorkout(scheduledWorkoutId)`.
Both take the `scheduledWorkoutId` (not the trainingId). Look it up via `getAthleteSchedule` first if you don't have it in context.

## Output format

Single athlete:
```
Assigned **<title>** to <athlete> for <weekday DD MMM>.

<renderWeekSchedule for athlete, that week>
```

Whole group (per turn):
```
✓ [3/8] Alice — Tue 14 May
```

Final summary after the last athlete:
```
Assigned **<title>** to <N> athletes in <group> for <weekday DD MMM>.
```

Reschedule / unassign:
```
Moved <athlete>'s **<title>** from <old> to <new>.
Dropped <athlete>'s **<title>** on <date>.
```

## Edge cases
- **Athlete not on roster** → refuse: *"<name> isn't on your roster — they need to connect via your coach code first."*
- **Training visibility is `private`** and the coach is trying to assign to athletes → ask whether to update visibility to `athletes` first.
- **Date in the past** → refuse and ask for a forward date.
- **Conflict on the chosen day** (athlete already has a workout) → flag, offer stack / swap / pick another day.
- **Coach asks to "assign to everyone for the next 4 Tuesdays"** → 4 weeks × N athletes = many writes. Do one assignment per turn, log progress, ask the coach to confirm at the start if it's a long run.
- **Group has 0 athletes** → refuse: *"That group has no athletes — assign anyway as a template?"*

# Workflow — Club / Group Sessions

Create one-off and recurring club sessions, announce them, attach a training, and track who's coming.

## Triggers
- "create a club session for Sunday"
- "set up our Tuesday night group ride"
- "make this a recurring session every Tuesday at 7pm"
- "post an announcement about Saturday's ride"
- "what's on this week for <club>"
- "create our monthly FTP test for the club"
- "see who joined Tuesday's session"

## Step 0 — Profile + club
- Read `coach-profile.md` for `clubs`, `groups`, default visibility, language, signature cues.
- Resolve the club: `listClubMembers` or `getClub(clubId)` to confirm the coach is owner/admin/coach there (`createClubSession` rejects regular members).

## One-off session

`createClubSession(...)` with:
- `clubId`
- `title` — `coach-profile.titleFormat` applied
- `description` — in `coach-profile.language` and `descriptionStyle`
- `startDate`, `startTime`, `durationMinutes` (or `endDate` for multi-day events)
- `trainingId` (optional) — attach an existing training as the session's prescription
- `clubGroupIds` (optional) — restrict to a sub-group
- `maxParticipants` (optional)
- `locationName`, `locationLat`, `locationLng` (optional)

If the coach wants a brand-new workout attached but it doesn't exist yet, design it via `create-workout.md` first (this turn), then `createClubSession` next turn with the new `trainingId`.

## Recurring session

`createRecurringSession(...)` with:
- the one-off fields above
- `recurrence` — e.g. `{ "frequency": "WEEKLY", "interval": 1, "byDayOfWeek": ["TUE"], "until": "<YYYY-MM-DD>" }`

After creation, future occurrences are materialised automatically by the backend. Edits to a single occurrence are out of scope here — refer the coach to the web UI.

## Manage attendance

Members self-RSVP via `joinClubSession` / `leaveClubSession`. The coach can:
- View the roster via `listClubSessions(clubId, from, to)` and the session detail (returned by `getClubFeed` or web).
- **Cancel** a session: `cancelSession(sessionId, reason)`.
- **Delete** completely: `deleteSession(sessionId)`.

## Announcements

`postClubAnnouncement(clubId, title, body)` for plain text club-wide announcements. The body should be markdown in the coach's `language` and `descriptionStyle`. Goes to the club feed and notifies members.

## Club tests

Coaches running periodic testing for their club:
- `listClubTests(clubId)` — what's already defined.
- `createClubTestFromPreset(clubId, presetType, scheduleDate)` — common presets (FTP 20-min, ramp test, 30-min running TT, CSS test).
- `startTestIteration(testId, scheduledDate)` — open an iteration for athletes to log results into.
- `recordTestResult(iterationId, athleteId, value)` — store an athlete's result.
- `applyTestReferences(iterationId)` — push the iteration's results as the athletes' new threshold reference (FTP / threshold pace / CSS).

## Insights

- `getClubFeed(clubId, limit)` — recent club activity (announcements, sessions, gazette events).
- `getEngagementInsights(clubId, from, to)` — member activity / engagement summary.
- `listClubGazetteEditions(clubId)` — past and current gazette editions.

## Output format

For a new session:
```
Created **<title>** for <weekday DD MMM HH:mm> · <duration>min.
- Attached training: <training title> · TSS <n>
- Visibility: <club / group / members>
- RSVP link: in-app club feed
```

For a recurring series:
```
Set up **<title>** every <weekday> at <HH:mm> until <until-date>.
```

For an announcement:
```
Posted to <club name>.
```

## Edge cases
- **Coach isn't owner/admin/coach of the club** → backend will refuse; surface the error cleanly.
- **`maxParticipants` already reached for an existing session the coach wants to extend** → ask whether to raise the cap.
- **Trying to attach a private training to a club session** → ask whether to raise visibility to `club`.
- **Multiple clubs in scope** → ask which club explicitly before any write.
- **Session in the past** → refuse to create; offer to clone with a future date.

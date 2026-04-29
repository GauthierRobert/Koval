---
name: koval-publish-club-gazette
description: Use when an owner / admin / coach asks Claude to compile and publish their club's weekly gazette ("publish the gazette", "compile this week's gazette", "send out edition #14", "release the club newsletter"). Discovers the current draft, lets the user choose what to include, generates a magazine-style PDF, and publishes atomically.
---

# Publish Club Gazette

## When to use

- "publish the gazette for {club}"
- "compile this week's club newsletter"
- "release edition #N"
- "let's put together the gazette"

This skill is **only** for owners, admins or coaches of a club — the `publishGazetteWithPdf` MCP tool will reject requests from regular members.

## Workflow

### Step 1 — Find the draft to publish

Call `listOpenGazetteDrafts(clubId)`. Typically there is one DRAFT covering the current week; sometimes two if the previous week's draft is still open.

Ask the user which one they want to publish if more than one exists. Display:
- edition number
- period covered (`periodStart` → `periodEnd`)
- creation date

### Step 2 — Get the full payload

Call `getGazettePayload(editionId)`. The response contains:
- `posts[]` — every member contribution in the draft, with author, type, content, photo URLs (signed, valid 24h), and any linked session / race goal already resolved
- `statsPreview`, `leaderboardPreview`, `topSessionsPreview`, `mostActiveMembersPreview`, `milestonesPreview` — live previews of what each auto-curated section *would* contain at publish time

### Step 3 — Curate with the user

Walk the user through the available material. Ask three things:

1. **Which posts to include** — by default include all non-empty, on-topic posts. Surface the list grouped by type (`SESSION_RECAP`, `RACE_RESULT`, `PERSONAL_WIN`, `SHOUTOUT`, `REFLECTION`). The user names the ones to drop.
2. **Which auto sections to include** — the five flags `STATS`, `LEADERBOARD`, `TOP_SESSIONS`, `MILESTONES`, `MOST_ACTIVE_MEMBERS`. Default to all five. Skip a section only if the preview is empty or the user prefers a leaner edition.
3. **Period bounds** — confirm the default period (Sunday-to-Sunday week) is what they want. If they want a different range (e.g. publishing biweekly), call `previewGazetteAutoSections(clubId, newStart, newEnd)` to see fresh stats for the proposed bounds.

### Step 4 — Generate the PDF

Compose a magazine-style PDF locally with the curated content. Suggested layout:

```
Page 1 — cover         : edition number, period, club name, hero stats line
Page 2 — overview      : stats grid (swim/bike/run + total hours + TSS)
Page 3 — leaderboard   : top 5–10 by TSS
Page 4 — top sessions  : 1–3 cards with title, date, participants
Page 5 — most active   : top 5 members
Page 6 — milestones    : race finishes, club anniversaries
Pages 7+ — member posts: one card per included post, photos rendered inline
```

Photos: download each `originalUrl` (or `variants.large.url` to save bandwidth) from the payload and embed.

The PDF must be ≤ 10 MB.

### Step 5 — Publish

Call `publishGazetteWithPdf` with:
- `editionId` — the draft chosen in step 1
- `pdfBase64` — your generated PDF, base64-encoded
- `filename` — `gazette-{number}.pdf` is fine
- `includedPostIds` — exactly the post IDs the user chose, **in display order**
- `includedSections` — the auto-section flags they confirmed (`["STATS", "LEADERBOARD", ...]`)
- `periodStart` / `periodEnd` — only if the user changed them in step 3

The server will:
1. Validate the PDF (size, magic header)
2. Verify all `includedPostIds` belong to this edition
3. Freeze snapshots for the chosen sections only
4. Mark non-included posts as `excluded=true` (still kept in the database, just hidden from the published view)
5. Attach the PDF and transition to `PUBLISHED`
6. Notify all active club members
7. Emit a `GAZETTE_PUBLISHED` event in the live club feed

### Step 6 — Confirm

Tell the user the edition has been published, mention how many posts and which sections were included, and link to the PDF download (`/api/clubs/{clubId}/gazette/editions/{id}/pdf`).

## Things to avoid

- **Don't publish without showing a curation summary.** Always echo the chosen post titles and section flags back to the user before calling `publishGazetteWithPdf`.
- **Don't silently rewrite a member's post.** You can choose to exclude one, but never edit content.
- **Don't publish a DRAFT that has zero posts AND zero sections.** Refuse and ask for at least one section.
- **Don't reuse the same period bounds across two consecutive editions** — each edition should cover a unique period.
- **Don't call `discardGazetteDraft`** unless the user explicitly asks to throw away a draft.

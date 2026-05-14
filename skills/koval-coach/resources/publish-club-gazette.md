# Workflow — Publish Club Gazette

Compile and publish a club's periodic gazette as a magazine-style PDF. Owner / admin / coach role required.

## Triggers
- "publish the gazette for <club>"
- "compile this week's club newsletter"
- "release edition #N"
- "let's put together the gazette"

`publishGazetteWithPdf` rejects regular members — surface that error cleanly if the coach somehow loses the role mid-flow.

## Step 1 — Find the draft

`listOpenGazetteDrafts(clubId)`. Typically one DRAFT covers the current period; sometimes two if a previous draft is still open.

If more than one, ask which to publish. Show:
- edition number
- period covered (`periodStart` → `periodEnd`)
- creation date

## Step 2 — Get the full payload

`getGazettePayload(editionId)`. The response contains:
- `posts[]` — every member contribution (author, type, content, signed photo URLs valid 24h, linked session / race goal already resolved)
- `statsPreview`, `leaderboardPreview`, `topSessionsPreview`, `mostActiveMembersPreview`, `milestonesPreview` — live previews of what each auto-curated section would contain at publish time

## Step 3 — Curate with the user

Ask three things:

1. **Which posts to include** — default = all non-empty, on-topic posts. Surface grouped by type (`SESSION_RECAP`, `RACE_RESULT`, `PERSONAL_WIN`, `SHOUTOUT`, `REFLECTION`). The coach names the ones to drop.
2. **Which auto sections to include** — `STATS`, `LEADERBOARD`, `TOP_SESSIONS`, `MILESTONES`, `MOST_ACTIVE_MEMBERS`. Default all five. Drop empty previews or whatever the coach prefers.
3. **Period bounds** — confirm the default Sunday-to-Sunday window. For a biweekly cadence, call `previewGazetteAutoSections(clubId, newStart, newEnd)` to refresh the stats for the new bounds.

## Step 4 — Generate the PDF

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

Photos: download each `originalUrl` (or `variants.large.url` to save bandwidth) and embed.

**PDF size: ≤ 10 MB.**

Apply `coach-profile.language` for any natural-language captions.

## Step 5 — Publish

`publishGazetteWithPdf` with:
- `editionId` — the draft chosen in step 1
- `pdfBase64` — your generated PDF, base64-encoded
- `filename` — `gazette-{number}.pdf` is fine
- `includedPostIds` — exactly the post IDs the coach chose, **in display order**
- `includedSections` — the auto-section flags they confirmed (e.g. `["STATS","LEADERBOARD","TOP_SESSIONS"]`)
- `periodStart` / `periodEnd` — only if the user changed them in step 3

The server will:
1. Validate the PDF (size, magic header)
2. Verify all `includedPostIds` belong to this edition
3. Freeze snapshots for the chosen sections only
4. Mark non-included posts as `excluded=true` (kept in DB, hidden from the published view)
5. Attach the PDF and transition to `PUBLISHED`
6. Notify all active club members
7. Emit a `GAZETTE_PUBLISHED` event in the live club feed

## Step 6 — Confirm

Tell the coach how many posts and which sections were included; link to the PDF download (`/api/clubs/{clubId}/gazette/editions/{id}/pdf`).

## Things to avoid

- **Never publish without a curation summary.** Always echo chosen post titles and section flags before calling `publishGazetteWithPdf`.
- **Never silently rewrite a member's post.** You may exclude a post, never edit content.
- **Refuse to publish a DRAFT with zero posts AND zero sections.** Ask for at least one section first.
- **Don't reuse the same period bounds across consecutive editions.** Each edition should cover a unique period.
- **Don't call `discardGazetteDraft`** unless the coach explicitly asks to throw away the draft.

## Edge cases
- **No open drafts** → the previous edition was published. *"No draft is open — a new one will roll over at the next period boundary. Discard isn't needed."*
- **Coach asks for a custom period bigger than the draft window** → call `previewGazetteAutoSections` for the new bounds and confirm the auto-section deltas before publishing.
- **PDF exceeds 10 MB** → re-encode photos to `variants.large` or `variants.medium`; warn the coach.
- **Coach changes their mind mid-flow** — never auto-publish. Always wait for an explicit *"yes, publish"*.

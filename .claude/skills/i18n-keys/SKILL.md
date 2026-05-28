---
name: i18n-keys
description: Use when adding or modifying user-facing strings in Angular templates/components, or when editing `frontend/public/i18n/en.json` or `frontend/public/i18n/fr.json`. Enforces the `@ngx-translate` pattern, keeps `en` and `fr` keysets in sync, and prevents JSON corruption (a recurring failure mode in this repo).
---

# i18n Keys

## Overview

User-facing text in the frontend goes through `@ngx-translate`. Two locale files must stay structurally identical:

- `frontend/public/i18n/en.json`
- `frontend/public/i18n/fr.json`

Hardcoded English strings in templates are a bug — they bypass French users entirely.

## Adding a string

1. **Choose the namespace** that matches the feature: `DASHBOARD.`, `CALENDAR_WEEK.`, `SETTINGS.`, `TRAININGS.`, `COACH.`, `CLUBS.`, `PLANS.`, `RACES.`, `GOALS.`, `PMC.`, `ANALYTICS.`, `PACING.`, `ONBOARDING.`, `ERRORS.`, `COMMON.`. Reuse an existing namespace before inventing a new one.
2. **Key style**: `SCREAMING_SNAKE_CASE`, semantic (not duplicate-text-as-key): `DASHBOARD.WEEKLY_LOAD_TITLE`, not `DASHBOARD.WEEKLY_LOAD`.
3. **Add to `en.json` first**, then **add the same key to `fr.json`** in the same change. Never add only one side.
4. In the template:
   ```html
   <h2>{{ 'DASHBOARD.WEEKLY_LOAD_TITLE' | translate }}</h2>
   ```
5. With parameters:
   ```json
   "DASHBOARD.GREETING": "Welcome back, {{name}}"
   ```
   ```html
   {{ 'DASHBOARD.GREETING' | translate: { name: athlete.firstName } }}
   ```

## Modifying a string

- Translation change only (text): update both `en.json` and `fr.json`. No template change needed.
- Key rename: grep every template + TS file for the old key, replace all, then update both JSON files. Stale references silently render the literal key.
- Deletion: grep first. If nothing references it, remove from both files.

## Key parity

After any change to either file, **both files must have the exact same set of keys**. Quick check:

```bash
node -e "
const en = require('./frontend/public/i18n/en.json');
const fr = require('./frontend/public/i18n/fr.json');
const flat = (o, p='') => Object.entries(o).flatMap(([k,v]) =>
  typeof v === 'object' ? flat(v, p+k+'.') : [p+k]);
const e = new Set(flat(en)), f = new Set(flat(fr));
const onlyEn = [...e].filter(k => !f.has(k));
const onlyFr = [...f].filter(k => !e.has(k));
if (onlyEn.length) console.log('Only in en:', onlyEn);
if (onlyFr.length) console.log('Only in fr:', onlyFr);
if (!onlyEn.length && !onlyFr.length) console.log('In sync: ' + e.size + ' keys');
"
```

## JSON safety

Both files are large and have been corrupted by hand-edits before. Required after any edit:

```bash
node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/en.json','utf8')); console.log('en.json: valid')"
node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/fr.json','utf8')); console.log('fr.json: valid')"
```

If either fails, **stop and fix the JSON** before doing anything else — a broken locale file white-screens the app.

## In code (TS), not templates

For dynamic strings (toast messages, error text from a service):

```typescript
import { TranslateService } from '@ngx-translate/core';

constructor(private translate: TranslateService) {}

showError() {
  this.errorToast.show(this.translate.instant('ERRORS.SAVE_FAILED'));
}
```

Use `.instant(...)` only when you know translations are already loaded (after app bootstrap). Otherwise `.get(...).subscribe(...)`.

## French translation guidance

- Keep tone consistent with existing keys in the same namespace (formal `vous`, not `tu`, unless the namespace already uses `tu`).
- Sport vocabulary: `entraînement` (workout), `séance` (session), `course` (race/run — context matters), `vélo` (cycling), `natation` (swimming), `zone` (zone).
- Don't translate proper nouns (Strava, Koval, FTP, PMC).
- If you don't know the right French translation, leave a `TODO_FR:` prefix in the value rather than guessing — easier to grep and fix later than burying bad translations.

## Anti-patterns

| Don't | Do Instead |
|---|---|
| Add a string to `en.json` only | Add to both `en.json` and `fr.json` in the same commit |
| Hardcode `<h1>Welcome</h1>` in a template | Use `{{ 'NAMESPACE.WELCOME' | translate }}` |
| Use the English text as the key (`'Welcome back' \| translate`) | Semantic key: `DASHBOARD.GREETING` |
| Edit JSON without validating | Run the JSON parse check after every edit |
| Translate proper nouns | Keep `Strava`, `Koval`, `FTP`, `PMC` unchanged |
| Duplicate keys across namespaces | One canonical namespace per concept; reuse the key |

# i18n French Translation — Design Spec

**Date**: 2026-03-21
**Status**: Approved
**Library**: @ngx-translate/core + @ngx-translate/http-loader

## Overview

Add full French translation support to the Angular frontend using ngx-translate. All ~750 hardcoded user-facing strings across ~47 HTML templates and ~40 TS files will be extracted into translation JSON files. A language switcher in the top bar allows runtime toggling between English and French.

## Architecture

### Dependencies

```
@ngx-translate/core@^17.0.0
@ngx-translate/http-loader@^17.0.0
```

### Translation Files

```
frontend/public/i18n/en.json   — English (source of truth)
frontend/public/i18n/fr.json   — French
```

Files placed in `public/` so they are served by Angular's build system (the project uses `public/` as asset root in `angular.json`, not `src/assets/`). The `HttpLoaderFactory` prefix is set to `./i18n/`.

Single file per language. Nested JSON structure with component-based namespaces.

### App Configuration

**`app.config.ts`**:

```typescript
import { importProvidersFrom } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './i18n/', '.json');
}

// In providers array:
importProvidersFrom(
  TranslateModule.forRoot({
    loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory, deps: [HttpClient] },
    defaultLanguage: 'en'
  })
)
```

**Language initialization** (in `app.component.ts`):
1. Check `localStorage.getItem('lang')`
2. Fall back to `navigator.language.startsWith('fr') ? 'fr' : 'en'`
3. Set `translate.setDefaultLang('en')`
4. Call `translate.use(detectedLang)`

### Standalone Component Integration

Every standalone component using the `translate` pipe must add `TranslateModule` to its `imports` array.

### TypeScript Translation Strategy

Two patterns depending on reactivity needs:

- **One-shot messages** (error toasts, notifications, confirmations): Use `translate.instant('KEY')`. These are displayed once and don't need to react to language changes.
- **Persistent display strings** (computed labels in TS that stay on screen): Use `translate.stream('KEY')` which returns an Observable, or move the translation to the template with the `translate` pipe. This ensures the value updates when the user switches language.

Methods that compute display strings (e.g., `getMembershipLabel()`, `getOpenToAllLabel()`) should return translation keys instead of English strings, and let the template translate them via the pipe.

## Key Convention

### Namespace Structure

Nested JSON keyed by component namespace in UPPER_SNAKE_CASE:

```json
{
  "TOP_BAR": {
    "DASHBOARD": "Dashboard",
    "ASSISTANT": "Assistant"
  },
  "COMMON": {
    "CANCEL": "Cancel",
    "SAVE": "Save"
  }
}
```

### Namespaces (~42 total)

| Category | Namespaces |
|---|---|
| Layout | `TOP_BAR`, `SIDEBAR`, `SETTINGS`, `TRAINING_HISTORY` |
| Auth | `LOGIN`, `AUTH_CALLBACK`, `ONBOARDING` |
| Dashboard | `DASHBOARD` |
| AI | `AI_CHAT` |
| Calendar | `CALENDAR`, `CALENDAR_WEEK`, `CALENDAR_MONTH` |
| Clubs | `CLUBS_LIST`, `CLUB_DETAIL`, `CLUB_FEED`, `CLUB_SESSIONS`, `CLUB_MEMBERS`, `CLUB_STATS`, `CLUB_LEADERBOARD`, `CLUB_RACE_GOALS` |
| Coaching | `COACH_DASHBOARD`, `GROUP_MANAGEMENT` |
| Live Session | `LIVE_SESSION`, `SESSION_SUMMARY`, `ZOMBIE_GAME` |
| Training | `WORKOUT_SELECTION`, `WORKOUT_HISTORY`, `SESSION_ANALYSIS` |
| Pages | `PACING`, `PMC`, `PHYSIOLOGY`, `GOALS`, `RACES` |
| Zone | `ZONE_MANAGER` |
| Shared | `BLOCK_EDITOR`, `DEVICE_MANAGER`, `INVITE_CODE`, `MEMBERSHIPS`, `SHARE_TRAINING`, `TRAINING_ACTION`, `WORKOUT_DETAIL`, `WORKOUT_VIZ`, `CREATE_WITH_AI` |
| Common | `COMMON` |

### Key Naming

- Keys in UPPER_SNAKE_CASE: `TOP_BAR.NO_CLUBS_YET`
- Shared strings in `COMMON`: Cancel, Save, Delete, Join, Leave, Loading, etc.
- Parameterized: `"IMPORTED": "Imported {{count}} activities"`
- Plurals: separate keys `MEMBER` / `MEMBERS`

## Usage Patterns

### HTML Templates — Translate Pipe

```html
<!-- Static text -->
<span>{{ 'TOP_BAR.DASHBOARD' | translate }}</span>

<!-- Attributes -->
<input [placeholder]="'LOGIN.USER_ID' | translate" />
<button [title]="'COMMON.DELETE' | translate">...</button>

<!-- Parameters -->
{{ 'HISTORY.IMPORTED' | translate: { count: result.newlyImported } }}

<!-- Conditional text -->
{{ (items.length === 1 ? 'CLUB.MEMBER' : 'CLUB.MEMBERS') | translate }}
```

### TypeScript — TranslateService

```typescript
private translate = inject(TranslateService);

// Synchronous (after language loaded)
this.errorMsg = this.translate.instant('SIDEBAR.INVALID_CODE');

// With parameters
this.msg = this.translate.instant('HISTORY.IMPORTED', { count: 5 });
```

## Language Switcher

**Location**: Top bar, right side, before settings gear icon.

**UI**: Text button showing the *other* language (`FR` when in English, `EN` when in French). Click toggles.

**Implementation**: Inline in `top-bar.component.ts`:

```html
<button (click)="toggleLang()" class="lang-toggle">
  {{ currentLang === 'en' ? 'FR' : 'EN' }}
</button>
```

**Behavior**:
- Toggles between `en` ↔ `fr`
- Persists to `localStorage('lang')`
- Calls `translateService.use(lang)` — all pipes update reactively

## Scope — Full Translation

All ~750 strings will be translated. Major areas:

| Area | Est. Strings | Files |
|---|---|---|
| Auth (login, callback, onboarding) | ~30 | 3 HTML, 2 TS |
| Layout (top bar, sidebar, settings) | ~60 | 4 HTML, 2 TS |
| Dashboard | ~40 | 1 HTML, 1 TS |
| Calendar (main, week, month) | ~50 | 3 HTML |
| Clubs (list, detail, 6 tabs) | ~120 | 8 HTML, 3 TS |
| Coaching (dashboard, groups) | ~80 | 2 HTML, 2 TS |
| Live session (dashboard, summary, game) | ~50 | 3 HTML, 1 TS |
| Training (library, history, analysis) | ~40 | 3 HTML, 2 TS |
| Pages (pacing, PMC, physiology, goals, races) | ~150 | 5 HTML, 5 TS |
| Zone manager | ~30 | 1 HTML, 1 TS |
| Shared modals (9 components) | ~100 | 9 HTML, 5 TS |
| Common (reusable) | ~30 | — |
| **Total** | **~780** | **~47 HTML, ~25 TS** |

## Known Limitations

- **Date formatting**: ~24 occurrences of hardcoded `'en-US'` locale in `toLocaleDateString()` / `toLocaleTimeString()` calls across 14 files. These dates will remain in English format regardless of language selection. Should be addressed as a follow-up by replacing hardcoded locale with the active language.
- **Single JSON file size**: ~780 keys per file (~1500 lines of JSON). Manageable but may cause merge conflicts if multiple people edit translations simultaneously. Can split per-namespace later if needed.

## Non-Goals

- Backend translation (API error messages stay in English)
- Additional languages beyond English and French
- Angular built-in i18n (`@angular/localize`)
- Lazy-loading translation files per route
- Translating the brand name "Koval TRAINING"
- Translating sport-specific abbreviations that are universal (FTP, TSS, CTL, ATL, TSB, RPM, BPM, W, W/kg)
- Date/time locale formatting (follow-up task)

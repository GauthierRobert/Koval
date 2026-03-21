# i18n French Translation — Design Spec

**Date**: 2026-03-21
**Status**: Approved
**Library**: @ngx-translate/core + @ngx-translate/http-loader

## Overview

Add full French translation support to the Angular frontend using ngx-translate. All ~750 hardcoded user-facing strings across ~47 HTML templates and ~40 TS files will be extracted into translation JSON files. A language switcher in the top bar allows runtime toggling between English and French.

## Architecture

### Dependencies

```
@ngx-translate/core
@ngx-translate/http-loader
```

### Translation Files

```
frontend/src/assets/i18n/en.json   — English (source of truth)
frontend/src/assets/i18n/fr.json   — French
```

Single file per language. Nested JSON structure with component-based namespaces.

### App Configuration

**`app.config.ts`**: Add `TranslateModule.forRoot()` with `HttpLoaderFactory` via `importProvidersFrom()`.

**Language initialization** (in `app.component.ts`):
1. Check `localStorage.getItem('lang')`
2. Fall back to `navigator.language.startsWith('fr') ? 'fr' : 'en'`
3. Set `translate.setDefaultLang('en')`
4. Call `translate.use(detectedLang)`

### Standalone Component Integration

Every standalone component using the `translate` pipe must add `TranslateModule` to its `imports` array.

Services using translations inject `TranslateService` and use `translate.instant('KEY')`.

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

## Non-Goals

- Backend translation (API error messages stay in English)
- Additional languages beyond English and French
- Angular built-in i18n (`@angular/localize`)
- Lazy-loading translation files per route
- Translating the brand name "Koval TRAINING"
- Translating sport-specific abbreviations that are universal (FTP, TSS, CTL, ATL, TSB, RPM, BPM, W, W/kg)

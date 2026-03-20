# Unified Training Picker & Button Standardization

## Problem

Training creation and linking is scattered across multiple components with inconsistent UX:

1. **`CreateWithAiModalComponent`** (club sessions, workout selection) — has a search+card picker in "Select existing" tab
2. **`ScheduleModalComponent`** (coach dashboard, group management, club members) — uses a basic `<select>` dropdown
3. **Dashboard `LINK TRAINING`** — navigates away to the club page instead of opening a modal
4. **`ClubSessionsTabComponent`** — bubbles events to parent to open modal instead of being self-contained
5. **AI button styles** — three different CSS classes (`ai-btn`, `tab-btn--ai`, `ai-btn-corner`) with inconsistent appearance

## Approach

**Approach B: Picker component + self-contained sessions tab**

- Extract training search/select UI into a shared `TrainingSearchPickerComponent`
- Use it in both `CreateWithAiModalComponent` and `ScheduleModalComponent`
- Move modal ownership into `ClubSessionsTabComponent` (remove event bubbling to parent)
- Dashboard opens `CreateWithAiModalComponent` inline instead of navigating
- Standardize AI button styling via a shared CSS class

## Design

### 1. New Component: `TrainingSearchPickerComponent`

**Location:** `frontend/src/app/components/shared/training-search-picker/`

A standalone Angular component (`ChangeDetectionStrategy.OnPush`) that provides a searchable, card-based training selection UI.

**API:**

```typescript
// Inputs
@Input() selectedId: string | null = null;

// Output — EventEmitter extends Subject<T> extends Observable<T>
@Output() selected = new EventEmitter<Training | null>();

// Internal state
private searchQuery$ = new BehaviorSubject<string>('');

filteredTrainings$ = combineLatest([
  this.trainingService.trainings$,
  this.searchQuery$
]).pipe(
  map(([trainings, q]) => !q.trim() ? trainings
    : trainings.filter(t =>
        t.title?.toLowerCase().includes(q.toLowerCase()) ||
        t.description?.toLowerCase().includes(q.toLowerCase())))
);
```

**Behavior:**
- Calls `TrainingService.loadTrainings()` in `ngOnInit`
- Template binds `filteredTrainings$ | async`
- Search input updates `searchQuery$` via `(input)` binding
- Card click emits `selected` with the training (or `null` to deselect)
- Selection state is controlled externally via `selectedId` input — the component is stateless beyond search query
- Displays training title, sport badge, type badge, and truncated description per card

### 2. Changes to `CreateWithAiModalComponent`

The "Select existing" tab body is replaced by `<app-training-search-picker>`:

```html
<app-training-search-picker
  [selectedId]="selectedTrainingId"
  (selected)="selectedTrainingId = $event?.id ?? null">
</app-training-search-picker>
```

**Removed from component:**
- `availableTrainings: Training[]`
- `searchQuery: string`
- `filteredTrainings` getter
- `selectTraining()` method
- `TrainingService.loadTrainings()` call in `ngOnChanges`

**Kept:**
- `selectedTrainingId` — used by `confirmSelection()` to link training to session
- `confirmSelection()` — calls `clubService.linkTrainingToSession()`
- `resetState()` — still resets `selectedTrainingId`

### 3. Changes to `ScheduleModalComponent`

The "Pick Existing" `<select>` dropdown is replaced by `<app-training-search-picker>`:

```html
@if (!showAiGenerate) {
  <app-training-search-picker
    [selectedId]="selectedTrainingId"
    (selected)="selectedTrainingId = $event?.id ?? null">
  </app-training-search-picker>
}
```

**Removed:**
- `trainings` private property and its subscription in `ngOnInit`
- The `<select>` element and `<option>` loop

**Changed:**
- `selectedTrainingId` type from `string` to `string | null` (initialized to `null` instead of `''`)
- `canSubmit()` guard updated to check `!this.selectedTrainingId` (already handles falsy values)

**Kept:**
- `selectedTrainingId` — used by `submit()` to assign/schedule
- Date field, athletes checkboxes, notes textarea, AI generate section — all unchanged

### 4. `ClubSessionsTabComponent` Becomes Self-Contained

Modal state moves from `ClubDetailPageComponent` into `ClubSessionsTabComponent`.

**Remove from `ClubDetailPageComponent`:**
- Properties: `showAiModal`, `aiContext`, `aiSessionInfo`, `aiSessionDate`
- Methods: `openAiModalForSession()`, `onAiCreated()`
- Template: `<app-create-with-ai-modal>` block
- Template: `(createAiForSession)` event binding on `<app-club-sessions-tab>`

**Add to `ClubSessionsTabComponent`:**
- TypeScript imports: `ActionContext`, `ActionResult` from `ai-action.service`
- Angular imports: `CreateWithAiModalComponent`
- Properties: `showAiModal = false`, `aiContext: ActionContext = {}`, `aiSessionInfo`, `aiSessionDate`
- Method `openAiModalForSession(session)` — builds context from session, sets `showAiModal = true`
- Method `onAiCreated(result)` — closes modal, reuses existing `loadCalendarSessions()` to refresh the week view
- Template: `<app-create-with-ai-modal>` with same bindings that were in `ClubDetailPageComponent`

**Delete from `ClubSessionsTabComponent`:**
- `@Output() createAiForSession` — no longer needed
- `onAiCreateForSession()` method — replaced by `openAiModalForSession()`

### 5. Dashboard `LINK TRAINING` Button

Replace navigation (`router.navigate`) with inline modal.

**Add to `DashboardComponent`:**

```typescript
linkTrainingSession: any = null;

openLinkModal(session: any): void {
  this.linkTrainingSession = session;
}

onLinkCreated(_result: ActionResult): void {
  this.linkTrainingSession = null;
  // Refresh reminders
}

onLinkModalClosed(): void {
  this.linkTrainingSession = null;
}
```

**Template:**

```html
<button class="reminder-action" (click)="openLinkModal(r)">LINK TRAINING</button>

<app-create-with-ai-modal
  [isOpen]="!!linkTrainingSession"
  actionType="TRAINING_FROM_NOTATION"
  [context]="{ clubId: linkTrainingSession?.clubId, sessionId: linkTrainingSession?.sessionId }"
  [sessionInfo]="{ scheduledAt: linkTrainingSession?.scheduledAt, sport: linkTrainingSession?.sport }"
  label="Link Training"
  (closed)="onLinkModalClosed()"
  (created)="onLinkCreated($event)">
</app-create-with-ai-modal>
```

**Remove:**
- `navigateToLinkTraining()` method
- `Router` import (if no longer used elsewhere)

**Add:**
- `CreateWithAiModalComponent` import

### 6. Button Style Standardization

Define a shared `btn-ai` CSS class in `frontend/src/styles.css` (global styles):

```css
.btn-ai {
  /* shared sparkle button appearance */
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.15), rgba(59, 130, 246, 0.15));
  border: 1px solid rgba(168, 85, 247, 0.3);
  color: #c4b5fd;
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-radius: 6px;
  padding: 6px 14px;
  transition: all 0.2s ease;
}

.btn-ai:hover {
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.25), rgba(59, 130, 246, 0.25));
  border-color: rgba(168, 85, 247, 0.5);
}
```

**Replace in each component:**
- `WorkoutSelectionComponent`: `tab-btn tab-btn--ai tab-btn--ai-background` → `btn-ai`
- `ZoneManagerComponent`: `ai-btn` → `btn-ai`
- `ClubSessionsTabComponent`: `ai-btn-corner` → `btn-ai btn-ai--compact` (compact variant for inline use on session cards)

Remove the old per-component CSS rules for the replaced classes.

## Files Changed

| File | Action |
|------|--------|
| `shared/training-search-picker/*` | **New** — picker component (TS, HTML, CSS) |
| `shared/create-with-ai-modal/create-with-ai-modal.component.ts` | Remove search/select state, import picker |
| `shared/create-with-ai-modal/create-with-ai-modal.component.html` | Replace select tab body with picker |
| `shared/schedule-modal/schedule-modal.component.ts` | Remove `trainings` property + subscription, import picker, change `selectedTrainingId` to `string \| null` |
| `shared/schedule-modal/schedule-modal.component.html` | Replace `<select>` with picker |
| `clubs/club-detail-page/club-detail-page.component.ts` | Remove modal state and methods |
| `clubs/club-detail-page/club-detail-page.component.html` | Remove modal and event binding |
| `clubs/club-sessions-tab/club-sessions-tab.component.ts` | Add modal state, methods, import modal |
| `clubs/club-sessions-tab/club-sessions-tab.component.html` | Add modal template |
| `dashboard/dashboard.component.ts` | Replace navigation with modal state |
| `dashboard/dashboard.component.html` | Add modal template, change button handler |
| `workout-selection/workout-selection.component.html` | Update button class to `btn-ai` |
| `zone-manager/zone-manager.component.html` | Update button class to `btn-ai` |
| `club-sessions-tab/club-sessions-tab.component.html` | Update button class to `btn-ai btn-ai--compact` |
| `club-sessions-tab/club-sessions-tab.component.css` | Remove old `ai-btn-corner` styles |
| `src/styles.css` | Remove old `tab-btn--ai` / `tab-btn--ai-background` styles (they live here, not in the component CSS) |
| `zone-manager/zone-manager.component.css` | Remove old `ai-btn` styles |
| `src/styles.css` | Add global `btn-ai` class |

## Out of Scope

These components benefit transitively from the `ScheduleModalComponent` picker upgrade — no direct changes needed:
- `ClubMembersTabComponent` "ASSIGN TRAINING" button
- `GroupManagementComponent` "ASSIGN TRAINING" button
- `CoachDashboardComponent` athlete assignment

Excluded (different UX patterns):
- AI chat page — conversational, no modal needed
- Zone manager modal's `actionType="ZONE_CREATION"` — unrelated to training picking, unchanged
- `ShareTrainingModalComponent` — different purpose (tagging trainings to groups); candidate for future picker adoption

## Notes

- The `btn-ai` class unifies three distinct color schemes (indigo, cyan, purple) into a single purple gradient. This is an intentional visual redesign for consistency, not just a class rename.
- The `TrainingSearchPickerComponent` should use `ChangeDetectionStrategy.OnPush` to match the codebase convention and works naturally with `| async`.
- Verify that `sessionReminders$` objects from the coach service include `clubId`, `sessionId`, `scheduledAt`, and `sport` fields for the dashboard modal context.

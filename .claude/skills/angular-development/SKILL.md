---
name: angular-development
description: Use when writing or modifying Angular components, services, or templates in the frontend. Enforces RxJS-first reactive patterns, global style reuse, file size limits, and single responsibility principle.
---

# Angular Development Standards

## Overview

This project uses Angular 21 with standalone components, OnPush change detection, and RxJS-driven reactive state. Every component and service must follow these rules — no exceptions.

## RxJS-First: Push Over Pull

State flows through Observables. Components subscribe in templates via `| async`. Never pull state into plain properties for template binding.

### Required Pattern

```typescript
// Service: expose state as Observable
private itemsSubject = new BehaviorSubject<Item[]>([]);
items$ = this.itemsSubject.asObservable();

// Component: expose Observables for template
items$ = this.myService.items$;
vm$ = combineLatest({ items: this.items$, user: this.auth.user$ });
```

```html
<!-- Template: always use async pipe -->
@if (vm$ | async; as vm) {
  @for (item of vm.items; track item.id) { ... }
}
```

### Forbidden

- **No manual `.subscribe()` to set plain properties** for template binding. Use `| async` instead.
- **No imperative state** like `this.items = []` that templates read directly — use `BehaviorSubject` + `| async`.
- Exception: simple local UI state (e.g. `isOpen = false`, `selectedTab = 'details'`) that doesn't come from a service.

### Derived State

Use RxJS operators, not methods called from templates:

```typescript
// Good: derived once
filteredItems$ = this.items$.pipe(
  map(items => items.filter(i => !i.archived))
);

// Bad: recomputed every change detection cycle
getFilteredItems() { return this.items.filter(i => !i.archived); }
```

### When to Use `subscribe()`

Only for **side effects** that don't feed templates:
- API calls triggered by user actions (join, leave, save)
- Navigation (`router.navigate`)
- One-time initialization in `ngOnInit` for non-template state

Always clean up with `takeUntilDestroyed(this.destroyRef)` or use `DestroyRef`.

## Reuse Global Styles

`styles.scss` provides a full design system. **Use it instead of rewriting CSS per component.**

### Available Global Classes

| Class | Purpose |
|-------|---------|
| `.glass` | Glass-morphism card (bg + border + radius) |
| `.card` | Standard card (bg + border + radius + padding) |
| `.page` | Page container with padding and scrolling |
| `.page-header` | Flex header with space-between |
| `.page-title` / `.page-subtitle` | Page-level typography |
| `.section-label` | Uppercase small label (e.g. "MY CLUBS") |
| `.empty-state` | Centered empty content message |
| `.stat-value` / `.stat-label` | Big number + small label pair |
| `.skeleton` | Shimmer loading placeholder |
| `.btn-primary` / `.btn-ghost` / `.btn-danger` / `.btn-info` / `.btn-success` | Button variants |
| `.btn-ghost-neutral` | Ghost button without danger hover |
| `.btn-spinner` | Loading spinner inside buttons |
| `.premium-button` | Gradient accent button |
| `.desktop-only` / `.mobile-only` | Responsive visibility |
| `.icon-inline` | Inline SVG vertical alignment |
| `.table-scroll-wrapper` | Horizontal scroll on mobile |

### Available CSS Variables

Use these instead of hardcoding colors, spacing, or sizes:

- **Colors:** `--accent-color`, `--success-color`, `--danger-color`, `--info-color`, `--text-color`, `--text-muted`, `--text-dim`
- **Semantic tints:** `--accent-subtle`, `--accent-border`, `--accent-hover` (same for success/danger/info)
- **Surfaces:** `--bg-color`, `--surface-color`, `--surface-hover`, `--surface-elevated`, `--glass-bg`, `--glass-border`
- **Overlays:** `--overlay-5` through `--overlay-30`, `--overlay-backdrop`
- **Spacing:** `--space-xs` (4px) to `--space-2xl` (48px)
- **Typography:** `--text-xs` (0.625rem) to `--text-2xl` (1.5rem)
- **Radius:** `--radius-sm` (6px), `--radius-md` (10px), `--radius-lg` (14px), `--radius-xl` (20px)
- **Shadows:** `--shadow-sm`, `--shadow-md`, `--shadow-lg`
- **Layout:** `--page-padding`, `--sidebar-width`, `--topbar-height`, `--card-padding`

### Component CSS Rules

- **Never redefine** `.glass`, `.btn-*`, `.card`, `.skeleton` or other global classes in component CSS
- **Never hardcode** colors — always use CSS variables
- Component CSS should only contain **layout-specific styles** (grid, flex, positioning) and component-unique elements
- **SCSS mixins** `btn-colors($prefix)` and `%btn-base` extend are available for custom buttons

### Responsive Breakpoints

Follow the global breakpoint system:
- `768px` — tablets and large phones
- `480px` — small phones
- Desktop-first: use `max-width` queries

## File Size Limit: 400 Lines

**No component or service may exceed 400 lines** (TS + HTML combined for components, TS alone for services).

### When Approaching the Limit

1. **Components > 400 lines:** Extract sub-components into a subdirectory of the parent
2. **Services > 400 lines:** Extract domain-specific logic into focused services
3. **Templates > 250 lines:** Extract template sections into child components

### How to Extract

```
parent-component/
  parent.component.ts          # Orchestration, data loading, routing
  parent.component.html        # Layout shell using sub-components
  sub-feature/
    sub-feature.component.ts   # Focused on one section
    sub-feature.component.html
    sub-feature.component.css
```

Sub-components communicate via `@Input()` / `@Output()`. Parent owns the data; children render and emit events.

## Single Responsibility Principle

### Components

Each component does **one thing**:
- A **page component** loads data and orchestrates layout
- A **card component** displays one entity
- A **form component** manages one form
- A **modal component** wraps one dialog

If a component has multiple unrelated `@Input` groups or multiple independent `ngOnInit` chains, split it.

### Services

Each service owns **one domain**:
- `ClubService` — club CRUD and membership
- `ClubSessionService` — session CRUD and participation
- `AuthService` — authentication and user state

Never add methods to a service that don't belong to its domain. Create a new service instead.

## Component Conventions

### Always Use

- `standalone: true` — no NgModules
- `changeDetection: ChangeDetectionStrategy.OnPush`
- `@if` / `@for` / `@switch` control flow (not `*ngIf` / `*ngFor`)
- `inject()` function (not constructor injection)
- `track` expression in all `@for` loops
- `TranslateModule` + `| translate` pipe for all user-facing text
- `| async` pipe for all Observable bindings

### Never Use

- `*ngIf`, `*ngFor`, `*ngSwitch` (use new control flow)
- `NgModule` declarations
- Manual `subscribe()` for template data
- `Default` change detection strategy
- Hardcoded English strings in templates

## i18n

All user-facing strings go through `@ngx-translate`:
- Keys in `public/i18n/en.json` and `public/i18n/fr.json`
- Use `{{ 'SECTION.KEY' | translate }}` in templates
- Group keys by feature: `DASHBOARD.`, `CALENDAR_WEEK.`, `SETTINGS.`, etc.
- When adding keys to `en.json`, always add the French equivalent to `fr.json`

## OnPush + Change Detection

With `OnPush`, Angular only re-renders when:
1. An `@Input()` reference changes
2. An event handler fires in the template
3. An `| async` pipe receives a new value
4. `ChangeDetectorRef.markForCheck()` is called manually

If data changes outside these paths (e.g. in a `subscribe()` callback), call `this.cdr.markForCheck()`.

## Testing

- Use **Vitest** (not Karma/Jasmine)
- Test files: `*.spec.ts` next to the source file
- Run: `npm test` from `frontend/`
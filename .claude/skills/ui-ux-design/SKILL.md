---
name: ui-ux-design
description: Use when creating or modifying HTML templates and CSS styles for Angular components. Enforces visual hierarchy, spacing rhythm, responsive design, accessibility, interaction patterns, and the project's dark-first glassmorphism design language.
---

# UI/UX Design Standards

## Overview

This project uses a dark-first glassmorphism design language with an orange accent. Every UI element must feel intentional, consistent, and accessible. Design for the smallest screen first in your head, then implement desktop-first with `max-width` breakpoints.

## Visual Hierarchy

Every screen must have a clear reading order. Users scan in an F-pattern: top-left first, then across, then down.

### Hierarchy Levels

| Level | Purpose | Styling |
|-------|---------|---------|
| **1. Page title** | What page am I on? | `--text-xl`, weight 700, `--text-color` |
| **2. Section label** | What group is this? | `--text-xs`, weight 700, uppercase, `--text-dim`, letter-spacing 0.1em |
| **3. Card title** | What is this item? | 13-15px, weight 700, `--text-color` |
| **4. Body text** | Details and descriptions | `--text-sm`, weight 400-500, `--text-color` |
| **5. Meta/secondary** | Supporting info | `--text-xs` to `--text-sm`, `--text-muted` |
| **6. Tertiary** | Timestamps, counts, hints | `--text-xs`, `--text-dim` |

### Rules

- **Max 3 hierarchy levels visible at once** in any card or section
- **One primary action per card** — if a card has multiple actions, one must be visually dominant
- **Section labels always precede their content** — never orphan a label at the bottom of a scroll area
- **Accent color (`--accent-color`) is for primary actions and key data only** — overusing it kills hierarchy

## Spacing Rhythm

Use the spacing scale consistently. Never use arbitrary pixel values.

| Token | Value | Use for |
|-------|-------|---------|
| `--space-xs` | 4px | Inline gaps, tight padding |
| `--space-sm` | 8px | Between related elements, card internal gaps |
| `--space-md` | 16px | Between cards, section padding |
| `--space-lg` | 24px | Between sections |
| `--space-xl` | 32px | Major section breaks |
| `--space-2xl` | 48px | Page-level vertical rhythm |

### Rules

- **Related items: `--space-sm`** (elements within a card)
- **Sibling items: `--space-md`** (cards in a grid)
- **Sections: `--space-lg` to `--space-xl`** (between distinct groups)
- **Consistent internal padding:** cards use `--card-padding` (16px desktop, 12px mobile)
- **Never mix spacing scales** — if one gap is 8px, neighbors should be 8 or 16, not 10 or 14

## Card Design

Cards are the primary content container. Follow the glassmorphism system.

### Anatomy

```
┌─────────────────────────────────┐
│ [pill/badge]          [actions] │  ← top row: type + controls
│                                 │
│ [icon] TITLE                    │  ← identity: what is this
│ meta · meta · meta              │  ← supporting context
│                                 │
│ ┌─metric──┐ ┌─metric──┐        │  ← data: quantitative info
│ │ 45m DUR │ │ 120 TSS │        │
│ └─────────┘ └─────────┘        │
│                                 │
│ ─── footer / action bar ─────  │  ← action: what can I do
└─────────────────────────────────┘
```

### Rules

- **One accent color per card** — use `--card-color` CSS variable pattern for theming (border, pills, actions all derive from one color)
- **Left accent border** (3-4px) to indicate card type/status
- **Glass background** — use `.glass` class or `var(--glass-bg)` + `var(--glass-border)`
- **No heavy borders or shadows on cards** — use subtle `--glass-border` and soft shadows
- **Hover: subtle lift** — `translateY(-1px)` + soft `box-shadow`, never dramatic transforms
- **Status through color, not text** — attending = green border, waiting = orange border, not "Status: Attending"

## Color Usage

### One Color Per Semantic Meaning

| Color | Meaning | Use for |
|-------|---------|---------|
| `--accent-color` (orange) | Primary / brand / scheduled | Planned workouts, primary CTAs, club identity |
| `--success-color` (green) | Positive / done / active | Completed workouts, attending status, confirmations |
| `--danger-color` (red) | Negative / destructive / warning | Errors, cancellations, delete actions, full capacity |
| `--info-color` (blue) | Informational / neutral | Standalone sessions, info badges, links |
| `--text-muted` (gray) | Secondary / disabled | Inactive states, hints, timestamps |

### Rules

- **Never use raw hex values** — always reference CSS variables
- **Tints for backgrounds:** `color-mix(in srgb, var(--color) 8-15%, transparent)` for subtle backgrounds
- **Borders from tints:** `color-mix(in srgb, var(--color) 25%, transparent)` for borders
- **Max 2 colors per card** — the accent color + neutral text. No rainbow cards.
- **White text on dark bg, dark text on light bg** — the theme variables handle this automatically

## Typography

### Rules

- **Font: Inter** — the only typeface. Never add other fonts.
- **Weight scale:** 400 (body), 500 (emphasis), 600 (subheadings), 700 (headings), 800 (display/metrics)
- **Uppercase is for:** section labels, badges, pill tags, status indicators — **never for body text or titles longer than 3 words**
- **Letter-spacing:** 0.5-1px only on uppercase text. Never on regular text.
- **Line-height:** 1.2 for headings, 1.4-1.5 for body text
- **Truncation:** use `text-overflow: ellipsis` + `white-space: nowrap` + `overflow: hidden` for all titles in constrained layouts. Never let text wrap and break card height.

## Responsive Design

### Breakpoints (desktop-first)

```css
/* Tablets & large phones */
@media (max-width: 768px) { ... }

/* Small phones */
@media (max-width: 480px) { ... }
```

### Rules

- **Flex-wrap for card grids** — use `display: flex; flex-wrap: wrap; gap` with `min-width` on items, not CSS Grid with fixed columns
- **Stack on mobile** — multi-column layouts become single column at 768px
- **Touch targets: min 32px** — all buttons and interactive elements (`--touch-target-min`)
- **No horizontal scroll** — ever. If content overflows, truncate or wrap.
- **Hide non-essential actions on mobile** — use `.desktop-only` class. Show only the primary action.
- **Test at 320px width** — this is the minimum viable viewport

### Common Patterns

```css
/* Flex-wrap card grid — cards grow to fill, wrap on small screens */
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
}
.grid-item {
  flex: 1 1 200px;  /* grow, shrink, min 200px before wrapping */
  min-width: 200px;
  max-width: 400px;
}

/* Stack on mobile */
@media (max-width: 600px) {
  .grid-item {
    flex: 1 1 100%;
    max-width: none;
  }
}
```

## Interaction Patterns

### Buttons

- **Primary action:** `.btn-primary` (filled orange) — one per section/card
- **Secondary action:** `.btn-ghost-neutral` (transparent, neutral hover)
- **Destructive action:** `.btn-ghost` (transparent, red hover) or `.btn-danger`
- **Loading state:** add `.btn-spinner` element inside, disable button
- **Never stack more than 2 buttons side by side** — use a dropdown for 3+

### Action Visibility

- **Always visible:** primary action (join, save, submit)
- **Hover-to-reveal:** secondary actions (delete, reschedule, duplicate) — use `opacity: 0` on `.card-actions`, `opacity: 1` on parent `:hover`
- **On mobile:** all actions always visible (no hover state on touch)

```css
@media (hover: hover) {
  .card-actions { opacity: 0; pointer-events: none; }
  .card:hover .card-actions { opacity: 1; pointer-events: auto; }
}
```

### Modals

- **Dark backdrop:** `var(--overlay-backdrop)` with `backdrop-filter: blur(2px)`
- **Click backdrop to close** — always
- **Escape key to close** — always (`@HostListener('document:keydown.escape')`)
- **Focus trap** — use `cdkTrapFocus cdkTrapFocusAutoCapture` from `@angular/cdk/a11y`
- **Max width:** 440-480px for forms, 320px for confirmations
- **`role="dialog"` + `aria-modal="true"` + `aria-labelledby`** — always

### Dropdowns

- **Position: absolute** relative to trigger button
- **z-index: 100+** to escape card `overflow`
- **Click outside to close** — register document listener on open, remove on close
- **Glass background** with shadow: `var(--surface-color)` + `var(--shadow-md)`

## Empty States

Every list or data section must handle the empty case.

```html
@if (items.length === 0) {
  <div class="empty-state">
    <span class="empty-icon"><!-- relevant emoji or SVG --></span>
    <span>{{ 'SECTION.EMPTY_MESSAGE' | translate }}</span>
  </div>
}
```

- **Always include an icon or emoji** — bare text feels broken
- **Use encouraging copy** — "No workouts yet" not "Error: no data"
- **Offer a CTA when possible** — "Add your first workout" button

## Loading States

- **Skeleton screens** for initial data load (use `.skeleton` class)
- **Inline spinner** for button actions (use `.btn-spinner`)
- **Never show a blank screen** — always show structure while loading
- **Disable interactive elements** during async operations

## Accessibility

### Required

- **All images:** `alt` attribute (empty `alt=""` for decorative)
- **All buttons:** text content or `aria-label`
- **All inputs:** associated `<label>` or `aria-label`
- **All modals:** `role="dialog"`, `aria-modal="true"`, `aria-labelledby`
- **Status messages:** `role="alert"` for errors, `role="status"` for confirmations
- **Color is not the only indicator** — always pair color with text, icon, or shape
- **Focus visible:** never remove `:focus-visible` outlines without replacing them

### Keyboard Navigation

- `Tab` moves between interactive elements
- `Enter` / `Space` activates buttons and links
- `Escape` closes modals and dropdowns
- Arrow keys navigate within tab groups or lists (when applicable)

## Anti-Patterns

| Don't | Do Instead |
|-------|-----------|
| Hardcode colors (`#ff9d00`) | Use CSS variables (`var(--accent-color)`) |
| Custom button styles per component | Use `.btn-primary`, `.btn-ghost`, etc. |
| Fixed pixel widths on containers | Use `flex`, `max-width`, percentage |
| `overflow: hidden` on cards with dropdowns | Use `overflow: visible` when cards contain menus |
| Multiple accent colors in one card | One `--card-color` drives all accents |
| Deep nesting (>3 levels of divs) | Flatten structure, use flex/grid |
| Pixel-based font sizes | Use `--text-*` variables or rem |
| Animations longer than 200ms | Keep transitions 100-200ms for snappy feel |
| Large click targets without feedback | Add `:hover` and `:active` states |
| Text that wraps unpredictably | Truncate with ellipsis in constrained layouts |
# Koval Theming System

The app's look is driven entirely by **CSS custom properties** defined in
`_tokens.scss`. Swapping the look (retro, futuristic, modern, …) is a matter
of overriding those tokens — no component CSS needs to change.

## File layout

```
src/styles/
├── _tokens.scss        ← canonical token surface (default = dark)
├── _buttons.scss       ← button system (consumes tokens only)
├── _forms.scss         ← form controls (consumes tokens only)
├── _mixins.scss
└── themes/
    ├── _index.scss     ← forwards every pack
    ├── _retro.scss
    ├── _futuristic.scss
    ├── _modern.scss
    └── _synthwave.scss
```

`src/styles.scss` imports `tokens`, `themes`, `buttons`, `forms`, then layers
global utility classes on top.

## Activation

`ThemeService` writes two attributes on `<html>`:

| Attribute          | Values                                                          |
| ------------------ | --------------------------------------------------------------- |
| `data-theme`       | `dark` \| `light` — color scheme axis (system-resolved)         |
| `data-theme-pack`  | `retro` \| `futuristic` \| `modern` \| `synthwave` \| `custom`  |
| _(no attribute)_   | default pack                                                    |

Packs may declare their own light variant inside their file:

```scss
[data-theme-pack='retro'] {
  // dark variant tokens
  &[data-theme='light'] {
    // light variant tokens
  }
}
```

## Token surface

These are the groups every pack should consider. The leftmost column shows
the prefix; see `_tokens.scss` for the exhaustive list.

| Group          | Examples                                                     |
| -------------- | ------------------------------------------------------------ |
| Color          | `--primary-color`, `--accent-color`, `--accent-glow`         |
| Surface        | `--bg-color`, `--surface-card`, `--glass-bg`                 |
| Border         | `--border-width`, `--border-color`, `--radius-*`             |
| Typography     | `--font-display`, `--weight-display`, `--tracking-display`   |
| Spacing        | `--space-xs..3xl`, `--page-padding`, `--card-padding`        |
| Shadow         | `--shadow-sm..lg`, `--shadow-glass`, `--shadow-accent-glow`  |
| Glass          | `--glass-blur`, `--glass-saturation`, `--glass-filter`       |
| Atmosphere     | `--noise-opacity`, `--orb-opacity`, `--mesh-stop-1..3`       |
| Motion         | `--transition-fast/base/slow`, `--ease-*`                    |
| Component      | `--card-*`, `--btn-*`, `--focus-ring-*`, `--scrollbar-*`     |

The overlay & text alpha scales (`--overlay-3..30`, `--text-30..95`) are
parameterised over `--overlay-channel` / `--text-channel`. To flip the
polarity (e.g. light theme uses dark overlays) just set:

```scss
--overlay-channel: 0, 0, 0;
--text-channel: 0, 0, 0;
```

## Writing a new theme pack

1. Create `themes/_<slug>.scss` and wrap everything in
   `[data-theme-pack='<slug>'] { … }`.
2. Override the tokens you want to change. Anything not overridden inherits
   from `_tokens.scss`.
3. `@use 'newpack';` in `themes/_index.scss`.
4. Register it in `THEME_PACKS` inside `theme.service.ts` (slug, label, preview swatch, supportsLight).
5. Add i18n keys `SETTINGS.THEME_PACK_<SLUG>` to `en.json` / `fr.json`.

## Runtime AI-generated themes

`ThemeService.applyCustomTokens(tokens)` accepts a plain
`Record<string, string>`, injects a `<style id="koval-custom-theme">` into
`<head>`, switches the pack to `custom`, and persists to `localStorage`.
Pass `null` to clear.

```ts
themeService.applyCustomTokens({
  'accent-color': 'oklch(0.7 0.2 30)',
  'bg-color': '#100806',
  'radius-md': '2px',
  'glass-blur': '0px',
  'font-display': 'Courier New, monospace',
});
```

Token names are bare (no leading `--`).

### Prompt for AI theme generation

Paste this into Claude with your free-form vibe brief:

```
You are a designer generating a coherent theme pack for the Koval app.

Output ONLY a JSON object: { "<token>": "<css-value>", ... }
No prose, no markdown fences, no leading "--".

Required guidelines:
- Pick a clear visual identity (mood, era, materiality). Surface, accent,
  borders, radii, typography, atmosphere, and shadows must all reinforce it.
- Use oklch() for colors when possible. Keep WCAG AA contrast between
  text-color and bg-color (≥ 4.5:1 for body, ≥ 3:1 for large text).
- Pair surface + bg colors that read together. If accent is saturated,
  surfaces should be desaturated, and vice versa.
- Match typography to the era — serifs for retro, mono for cyber/futuristic,
  geometric sans for modern, etc.
- Glass blur 0–4px = flat era (retro, brutalist), 16–32px = modern/futuristic.
- Noise opacity 0–0.02 = clean, 0.03–0.05 = subtle texture, 0.06+ = heavy.
- Orb opacity 0 = no atmosphere, 0.05–0.12 = subtle, 0.15+ = neon/dreamy.
- Radii: small (≤4px) = sharp/retro, large (≥14px) = soft/modern.

Available tokens (override as many as needed to express the vibe):

  primary-color, secondary-color, success-color, danger-color, info-color,
  accent-color, accent-color-2, accent-glow, accent-glow-soft, on-primary-color,
  on-accent-color, bg-color, surface-color, surface-card, sidebar-color,
  glass-bg, surface-hover, surface-elevated, surface-raised,
  text-color, text-muted, text-dim,
  overlay-channel, text-channel,
  border-width, border-width-strong, border-style, border-color, border-color-strong,
  radius-sm, radius-md, radius-lg, radius-xl, radius-pill,
  font-display, font-body, font-mono,
  weight-display, tracking-display, display-transform, btn-text-transform, btn-letter-spacing,
  space-md, space-lg, space-xl, page-padding, card-padding,
  shadow-sm, shadow-md, shadow-lg, shadow-glass, shadow-elevated,
  shadow-inner-light, shadow-accent-glow,
  glass-blur, glass-saturation,
  noise-opacity, noise-blend-mode, orb-opacity, orb-blur,
  orb-1-color, orb-2-color, orb-3-color,
  mesh-stop-1, mesh-stop-2, mesh-stop-3,
  gradient-angle, accent-gradient,
  transition-fast, transition-base, transition-slow,
  focus-ring-color, focus-ring-width, focus-ring-offset

VIBE: <describe the theme here, e.g. "cyberpunk noir — rainy neon Tokyo,
       cold cyan accents on near-black, hot magenta highlights, mono UI,
       heavy glass and atmosphere">
```

The returned JSON can be passed directly to `applyCustomTokens()`.

## Migrating component CSS

Many component CSS files still contain hardcoded values from earlier
iterations (e.g. `border-radius: 8px`, `oklch(0.78 0.14 130 / 0.08)`,
`rgba(255, 255, 255, 0.08)`). When you touch a component file, replace
those with token references:

| Hardcoded                              | Token                                  |
| -------------------------------------- | -------------------------------------- |
| `border-radius: 8px`                   | `border-radius: var(--radius-md)`      |
| `border-radius: 6px`                   | `var(--radius-sm)`                     |
| `border-radius: 14px`                  | `var(--radius-lg)`                     |
| `border-radius: 20px` / `24px`         | `var(--radius-xl)`                     |
| `1px solid rgba(255,255,255,0.1)`      | `var(--card-border)`                   |
| `rgba(255,255,255,0.05)`               | `var(--overlay-5)`                     |
| `font-weight: 700` (body) / `800`      | `var(--weight-bold)` / `--weight-extrabold` |
| `letter-spacing: 0.5px` / `1px`        | `var(--tracking-wide)` / `--tracking-widest` |
| `oklch(0.75 0.18 65 / 0.3)`            | `var(--accent-glow)`                   |
| inline `transition: all 0.2s`          | `var(--transition-base)`               |

`grep -E "oklch\(|rgba\(255,|rgba\(0, 0, 0|border-radius: [0-9]"` against
`src/app/components` is a reasonable starting point for an incremental
migration pass.

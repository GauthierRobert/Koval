import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest, map } from 'rxjs';

export type ColorScheme = 'dark' | 'light';
export type ColorSchemeMode = 'dark' | 'light' | 'system';

/**
 * Built-in theme packs. Each is a self-contained SCSS file in
 * `src/styles/themes/`. To add a new built-in, drop a file there
 * and add its slug here. Custom AI-generated packs use the
 * 'custom' slug and inject tokens at runtime — see applyCustomTokens().
 */
export type ThemePack =
  | 'default'
  | 'retro'
  | 'futuristic'
  | 'modern'
  | 'synthwave'
  | 'almanac'
  | 'custom';

export interface ThemePackMeta {
  slug: ThemePack;
  labelKey: string; // i18n key (or fallback label)
  fallbackLabel: string;
  supportsLight: boolean;
  preview: { bg: string; accent: string; surface: string; text: string };
}

export const THEME_PACKS: readonly ThemePackMeta[] = [
  {
    slug: 'default',
    labelKey: 'SETTINGS.THEME_PACK_DEFAULT',
    fallbackLabel: 'Default',
    supportsLight: true,
    preview: { bg: '#0a0a0f', accent: 'oklch(0.75 0.18 65)', surface: '#14141b', text: '#ececf1' },
  },
  {
    slug: 'retro',
    labelKey: 'SETTINGS.THEME_PACK_RETRO',
    fallbackLabel: 'Retro',
    supportsLight: true,
    preview: { bg: '#1a130c', accent: 'oklch(0.7 0.16 55)', surface: '#251a10', text: '#f4e4c8' },
  },
  {
    slug: 'futuristic',
    labelKey: 'SETTINGS.THEME_PACK_FUTURISTIC',
    fallbackLabel: 'Futuristic',
    supportsLight: false,
    preview: { bg: '#04060d', accent: 'oklch(0.78 0.18 200)', surface: '#070b18', text: '#e0f4ff' },
  },
  {
    slug: 'modern',
    labelKey: 'SETTINGS.THEME_PACK_MODERN',
    fallbackLabel: 'Modern',
    supportsLight: true,
    preview: { bg: '#0e0f12', accent: 'oklch(0.55 0.04 250)', surface: '#16181d', text: '#f0f1f4' },
  },
  {
    slug: 'synthwave',
    labelKey: 'SETTINGS.THEME_PACK_SYNTHWAVE',
    fallbackLabel: 'Synthwave',
    supportsLight: false,
    preview: { bg: '#110821', accent: 'oklch(0.72 0.28 340)', surface: '#1a0c2e', text: '#ffe6ff' },
  },
  {
    slug: 'almanac',
    labelKey: 'SETTINGS.THEME_PACK_ALMANAC',
    fallbackLabel: 'Almanac',
    supportsLight: true,
    preview: { bg: '#ece3cf', accent: '#2f7a72', surface: '#f5ecd9', text: '#1c1408' },
  },
];

const SCHEME_STORAGE_KEY = 'theme';
const PACK_STORAGE_KEY = 'theme-pack';
const CUSTOM_TOKENS_STORAGE_KEY = 'theme-custom-tokens';
const CUSTOM_STYLE_ELEMENT_ID = 'koval-custom-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private modeSubject = new BehaviorSubject<ColorSchemeMode>(this.loadMode());
  private packSubject = new BehaviorSubject<ThemePack>(this.loadPack());

  mode$ = this.modeSubject.asObservable();
  pack$ = this.packSubject.asObservable();
  /** Resolved (system → dark/light) color scheme. */
  scheme$ = combineLatest([this.mode$, this.pack$]).pipe(map(() => this.resolved()));
  /** Back-compat alias — same as `scheme$`. */
  theme$ = this.scheme$;

  private mediaQuery: MediaQueryList | null =
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(prefers-color-scheme: light)')
      : null;

  constructor() {
    this.apply();
    this.mediaQuery?.addEventListener('change', () => {
      if (this.modeSubject.value === 'system') {
        this.apply();
        this.modeSubject.next('system'); // re-emit
      }
    });
    // Restore any custom-pack tokens that were saved in a previous session.
    const customTokens = this.loadCustomTokens();
    if (customTokens) this.injectCustomTokens(customTokens);
  }

  // ── Color scheme (dark / light / system) ────────────────────
  setMode(mode: ColorSchemeMode): void {
    this.modeSubject.next(mode);
    localStorage.setItem(SCHEME_STORAGE_KEY, mode);
    this.apply();
  }

  /** Legacy quick-toggle between dark / light. */
  toggle(): void {
    this.setMode(this.resolved() === 'dark' ? 'light' : 'dark');
  }

  // ── Named theme pack ────────────────────────────────────────
  setPack(pack: ThemePack): void {
    this.packSubject.next(pack);
    localStorage.setItem(PACK_STORAGE_KEY, pack);
    this.apply();
  }

  /**
   * Apply a fully custom token map (e.g. AI-generated). Persists to
   * localStorage and switches the pack to 'custom'. Pass `null` to
   * clear and fall back to the previous built-in pack.
   *
   * Token names are bare (without the leading `--`). Values are raw
   * CSS — any valid value for a custom property.
   */
  applyCustomTokens(tokens: Record<string, string> | null): void {
    if (!tokens) {
      localStorage.removeItem(CUSTOM_TOKENS_STORAGE_KEY);
      this.removeCustomStyleElement();
      // If the user was on 'custom', drop back to default.
      if (this.packSubject.value === 'custom') this.setPack('default');
      return;
    }
    localStorage.setItem(CUSTOM_TOKENS_STORAGE_KEY, JSON.stringify(tokens));
    this.injectCustomTokens(tokens);
    this.setPack('custom');
  }

  getCustomTokens(): Record<string, string> | null {
    return this.loadCustomTokens();
  }

  // ── Internals ───────────────────────────────────────────────
  private resolved(): ColorScheme {
    const mode = this.modeSubject.value;
    if (mode === 'system') return this.mediaQuery?.matches ? 'light' : 'dark';
    return mode;
  }

  private apply(): void {
    const root = document.documentElement;
    root.setAttribute('data-theme', this.resolved());
    const pack = this.packSubject.value;
    if (pack === 'default') root.removeAttribute('data-theme-pack');
    else root.setAttribute('data-theme-pack', pack);
  }

  private injectCustomTokens(tokens: Record<string, string>): void {
    const css =
      ':root[data-theme-pack="custom"] {\n' +
      Object.entries(tokens)
        .map(([k, v]) => `  --${k.replace(/^--/, '')}: ${v};`)
        .join('\n') +
      '\n}';
    let el = document.getElementById(CUSTOM_STYLE_ELEMENT_ID) as HTMLStyleElement | null;
    if (!el) {
      el = document.createElement('style');
      el.id = CUSTOM_STYLE_ELEMENT_ID;
      document.head.appendChild(el);
    }
    el.textContent = css;
  }

  private removeCustomStyleElement(): void {
    document.getElementById(CUSTOM_STYLE_ELEMENT_ID)?.remove();
  }

  private loadMode(): ColorSchemeMode {
    const stored = localStorage.getItem(SCHEME_STORAGE_KEY);
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored;
    return 'dark';
  }

  private loadPack(): ThemePack {
    const stored = localStorage.getItem(PACK_STORAGE_KEY);
    if (
      stored === 'default' ||
      stored === 'retro' ||
      stored === 'futuristic' ||
      stored === 'modern' ||
      stored === 'synthwave' ||
      stored === 'almanac' ||
      stored === 'custom'
    ) {
      return stored;
    }
    return 'default';
  }

  private loadCustomTokens(): Record<string, string> | null {
    const raw = localStorage.getItem(CUSTOM_TOKENS_STORAGE_KEY);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw);
      return typeof parsed === 'object' && parsed ? (parsed as Record<string, string>) : null;
    } catch {
      return null;
    }
  }
}

// ── Backwards-compat aliases for old imports ─────────────────────
export type Theme = ColorScheme;
export type ThemeMode = ColorSchemeMode;

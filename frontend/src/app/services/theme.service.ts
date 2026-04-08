import { Injectable } from '@angular/core';
import { BehaviorSubject, map } from 'rxjs';

export type Theme = 'dark' | 'light';
export type ThemeMode = 'dark' | 'light' | 'system';

const STORAGE_KEY = 'theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private modeSubject = new BehaviorSubject<ThemeMode>(this.loadMode());
  mode$ = this.modeSubject.asObservable();
  theme$ = this.modeSubject.pipe(map(() => this.resolved()));

  private mediaQuery: MediaQueryList | null =
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(prefers-color-scheme: light)')
      : null;

  constructor() {
    this.applyResolved();
    this.mediaQuery?.addEventListener('change', () => {
      if (this.modeSubject.value === 'system') {
        this.applyResolved();
        // Re-emit so theme$ subscribers see the new resolved value
        this.modeSubject.next('system');
      }
    });
  }

  setMode(mode: ThemeMode): void {
    this.modeSubject.next(mode);
    localStorage.setItem(STORAGE_KEY, mode);
    this.applyResolved();
  }

  /**
   * Backwards-compatible toggle: flips between explicit dark and light.
   * Used by the top-bar quick toggle. The Settings panel uses setMode() for full 3-way control.
   */
  toggle(): void {
    const next: ThemeMode = this.resolved() === 'dark' ? 'light' : 'dark';
    this.setMode(next);
  }

  private resolved(): Theme {
    const mode = this.modeSubject.value;
    if (mode === 'system') {
      return this.mediaQuery?.matches ? 'light' : 'dark';
    }
    return mode;
  }

  private loadMode(): ThemeMode {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored;
    return 'dark';
  }

  private applyResolved(): void {
    document.documentElement.setAttribute('data-theme', this.resolved());
  }
}

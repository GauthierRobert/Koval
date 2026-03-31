import { Injectable, NgZone, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type Theme = 'dark' | 'light';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private ngZone = inject(NgZone);
  private themeSubject = new BehaviorSubject<Theme>(this.loadTheme());
  theme$ = this.themeSubject.asObservable();

  constructor() {
    this.applyTheme(this.themeSubject.value);
  }

  toggle(): void {
    const next: Theme = this.themeSubject.value === 'dark' ? 'light' : 'dark';
    this.themeSubject.next(next);
    localStorage.setItem('theme', next);
    this.applyTheme(next);
  }

  private loadTheme(): Theme {
    const stored = localStorage.getItem('theme');
    if (stored === 'light' || stored === 'dark') return stored;
    return 'dark';
  }

  private applyTheme(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}

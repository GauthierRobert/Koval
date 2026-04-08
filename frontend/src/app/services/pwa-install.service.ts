import { Injectable, NgZone, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

const DISMISS_KEY = 'koval_pwa_install_dismissed_at';
const DISMISS_TTL_MS = 7 * 24 * 60 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  private ngZone = inject(NgZone);
  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  private installableSubject = new BehaviorSubject<boolean>(false);
  installable$ = this.installableSubject.asObservable();

  constructor() {
    if (typeof window === 'undefined') return;

    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      this.deferredPrompt = event as BeforeInstallPromptEvent;
      this.ngZone.run(() => {
        if (!this.isDismissedRecently()) {
          this.installableSubject.next(true);
        }
      });
    });

    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.ngZone.run(() => this.installableSubject.next(false));
    });
  }

  async prompt(): Promise<void> {
    if (!this.deferredPrompt) return;
    await this.deferredPrompt.prompt();
    const choice = await this.deferredPrompt.userChoice;
    this.deferredPrompt = null;
    this.ngZone.run(() => this.installableSubject.next(false));
    if (choice.outcome === 'dismissed') {
      this.markDismissed();
    }
  }

  dismiss(): void {
    this.markDismissed();
    this.installableSubject.next(false);
  }

  private markDismissed(): void {
    try {
      localStorage.setItem(DISMISS_KEY, String(Date.now()));
    } catch {
      /* ignore */
    }
  }

  private isDismissedRecently(): boolean {
    try {
      const raw = localStorage.getItem(DISMISS_KEY);
      if (!raw) return false;
      const ts = parseInt(raw, 10);
      if (isNaN(ts)) return false;
      return Date.now() - ts < DISMISS_TTL_MS;
    } catch {
      return false;
    }
  }
}

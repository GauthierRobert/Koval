import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  HostListener,
  inject,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService, User } from '../../../services/auth.service';
import { NolioSyncService } from '../../../services/nolio-sync.service';
import { PolarSyncService } from '../../../services/polar-sync.service';
import { SuuntoSyncService } from '../../../services/suunto-sync.service';
import { ErrorToastService } from '../../../services/error-toast.service';
import { TranslateModule } from '@ngx-translate/core';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-connected-apps-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './connected-apps-modal.component.html',
  styleUrl: './connected-apps-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConnectedAppsModalComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private nolioSync = inject(NolioSyncService);
  private polarSync = inject(PolarSyncService);
  private suuntoSync = inject(SuuntoSyncService);
  private destroyRef = inject(DestroyRef);
  private toast = inject(ErrorToastService);

  @Output() closed = new EventEmitter<void>();

  user$ = this.authService.user$;
  polarImporting$ = this.polarSync.importing$;
  suuntoImporting$ = this.suuntoSync.importing$;
  unlinking = false;
  readonly isProd = environment.production;

  private onLinkMessage = (event: MessageEvent) => {
    if (event.origin !== window.location.origin) return;
    if (event.data?.type !== 'ACCOUNT_LINKED') return;
    if (event.data.success) {
      this.authService.refreshUser();
    }
  };

  ngOnInit(): void {
    window.addEventListener('message', this.onLinkMessage);
  }

  ngOnDestroy(): void {
    window.removeEventListener('message', this.onLinkMessage);
  }

  getConnectedCount(user: User): number {
    if (!user.linkedAccounts) return 0;
    return [
      user.linkedAccounts.strava,
      user.linkedAccounts.google,
      user.linkedAccounts.garmin,
      user.linkedAccounts.polar,
      user.linkedAccounts.suunto,
      user.linkedAccounts.zwift,
      user.linkedAccounts.nolioWrite,
    ].filter(Boolean).length;
  }

  canUnlink(user: User, provider: 'strava' | 'google'): boolean {
    if (!user.linkedAccounts) return false;
    const other = provider === 'strava' ? 'google' : 'strava';
    return user.linkedAccounts[other] === true;
  }

  unlinkApp(
    provider:
      | 'strava'
      | 'google'
      | 'garmin'
      | 'polar'
      | 'suunto'
      | 'zwift'
      | 'nolioWrite',
  ) {
    this.unlinking = true;
    let obs: Observable<unknown>;
    switch (provider) {
      case 'strava':
        obs = this.authService.unlinkStrava();
        break;
      case 'google':
        obs = this.authService.unlinkGoogle();
        break;
      case 'garmin':
        obs = this.authService.unlinkGarmin();
        break;
      case 'polar':
        obs = this.polarSync.disconnect();
        break;
      case 'suunto':
        obs = this.suuntoSync.disconnect();
        break;
      case 'zwift':
        obs = this.authService.unlinkZwift();
        break;
      case 'nolioWrite':
        obs = this.nolioSync.disconnectWrite();
        break;
    }
    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => (this.unlinking = false),
      error: () => (this.unlinking = false),
    });
  }

  connectStrava(): void {
    this.authService
      .getStravaAuthUrl()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ authUrl }) => {
        const url = new URL(authUrl);
        url.searchParams.set('state', 'link');
        window.open(url.toString(), '_blank', 'width=600,height=700');
      });
  }

  connectGoogle(): void {
    this.authService
      .getGoogleAuthUrl()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ authUrl }) => {
        const url = new URL(authUrl);
        url.searchParams.set('state', 'link');
        window.open(url.toString(), '_blank', 'width=600,height=700');
      });
  }

  connectGarmin(): void {
    this.http
      .get<{ authUrl: string }>(`${environment.apiUrl}/api/integration/garmin/auth`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ authUrl }) => window.open(authUrl, '_blank', 'width=600,height=700'),
        error: () => {},
      });
  }

  connectPolar(): void {
    this.polarSync
      .getAuthUrl()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ authUrl }) => window.open(authUrl, '_blank', 'width=600,height=700'),
        error: (err) => this.reportConnectError(err, 'Polar'),
      });
  }

  connectSuunto(): void {
    this.suuntoSync
      .getAuthUrl()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ authUrl }) => window.open(authUrl, '_blank', 'width=600,height=700'),
        error: (err) => this.reportConnectError(err, 'Suunto'),
      });
  }

  toggleSuuntoAutoPush(enabled: boolean): void {
    this.suuntoSync
      .setAutoPush(enabled)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (err) => this.reportConnectError(err, 'Suunto auto-push'),
      });
  }

  toggleGarminAutoPush(enabled: boolean): void {
    this.http
      .put<unknown>(`${environment.apiUrl}/api/integration/garmin/auto-push`, { enabled })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.authService.refreshUser(),
        error: (err) => this.reportConnectError(err, 'Garmin auto-push'),
      });
  }

  importPolarHistory(): void {
    this.polarSync
      .importHistory()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const msg = `Polar: ${result.newlyImported} new, ${result.skippedDuplicates} duplicate, ${result.skippedErrors} error${result.skippedErrors === 1 ? '' : 's'}`;
          this.toast.show(msg, 'success');
        },
        error: (err) => this.reportConnectError(err, 'Polar import'),
      });
  }

  importSuuntoHistory(): void {
    this.suuntoSync
      .importHistory()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const msg = `Suunto: ${result.newlyImported} new, ${result.skippedDuplicates} duplicate, ${result.skippedErrors} error${result.skippedErrors === 1 ? '' : 's'}`;
          this.toast.show(msg, 'success');
        },
        error: (err) => this.reportConnectError(err, 'Suunto import'),
      });
  }

  toggleZwiftAutoSync(enabled: boolean): void {
    this.http
      .put<unknown>(`${environment.apiUrl}/api/integration/zwift/auto-sync`, { enabled })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.authService.refreshUser(),
      });
  }

  connectNolioWrite(): void {
    this.nolioSync
      .connectWrite()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (err) => this.reportConnectError(err, 'Nolio workout push'),
      });
  }

  private reportConnectError(err: unknown, label: string): void {
    const e = err as { error?: { message?: string; detail?: string }; status?: number };
    const backendMessage = e?.error?.message || e?.error?.detail;
    const message = backendMessage
      ? `${label}: ${backendMessage}`
      : `${label}: connection failed (status ${e?.status ?? 'unknown'}).`;
    this.toast.show(message, 'error');
  }

  toggleNolioAutoSync(enabled: boolean): void {
    this.nolioSync.setAutoSync(enabled).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closed.emit();
  }
}

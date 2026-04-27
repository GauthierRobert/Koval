import {ChangeDetectionStrategy, Component, EventEmitter, HostListener, inject, OnDestroy, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {AuthService, User} from '../../../services/auth.service';
import {NolioSyncService} from '../../../services/nolio-sync.service';
import {TranslateModule} from '@ngx-translate/core';
import {environment} from '../../../../environments/environment';

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

  @Output() closed = new EventEmitter<void>();

  user$ = this.authService.user$;
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
      user.linkedAccounts.zwift,
      user.linkedAccounts.nolioRead,
      user.linkedAccounts.nolioWrite,
    ].filter(Boolean).length;
  }

  canUnlink(user: User, provider: 'strava' | 'google'): boolean {
    if (!user.linkedAccounts) return false;
    const other = provider === 'strava' ? 'google' : 'strava';
    return user.linkedAccounts[other] === true;
  }

  unlinkApp(provider: 'strava' | 'google' | 'garmin' | 'zwift' | 'nolioRead' | 'nolioWrite') {
    this.unlinking = true;
    let obs: any;
    switch (provider) {
      case 'strava': obs = this.authService.unlinkStrava(); break;
      case 'google': obs = this.authService.unlinkGoogle(); break;
      case 'garmin': obs = this.authService.unlinkGarmin(); break;
      case 'zwift': obs = this.authService.unlinkZwift(); break;
      case 'nolioRead': obs = this.nolioSync.disconnectRead(); break;
      case 'nolioWrite': obs = this.nolioSync.disconnectWrite(); break;
    }
    obs.subscribe({
      next: () => this.unlinking = false,
      error: () => this.unlinking = false,
    });
  }

  connectStrava(): void {
    this.authService.getStravaAuthUrl().subscribe(({authUrl}) => {
      const url = new URL(authUrl);
      url.searchParams.set('state', 'link');
      window.open(url.toString(), '_blank', 'width=600,height=700');
    });
  }

  connectGoogle(): void {
    this.authService.getGoogleAuthUrl().subscribe(({authUrl}) => {
      const url = new URL(authUrl);
      url.searchParams.set('state', 'link');
      window.open(url.toString(), '_blank', 'width=600,height=700');
    });
  }

  connectGarmin(): void {
    this.http.get<{authUrl: string}>(`${environment.apiUrl}/api/integration/garmin/auth`).subscribe({
      next: ({authUrl}) => window.open(authUrl, '_blank', 'width=600,height=700'),
      error: () => {},
    });
  }

  toggleZwiftAutoSync(enabled: boolean): void {
    this.http.put<any>(`${environment.apiUrl}/api/integration/zwift/auto-sync`, {enabled}).subscribe({
      next: () => this.authService.refreshUser(),
    });
  }

  connectNolioRead(): void {
    this.nolioSync.connectRead().subscribe({
      error: (err) => this.reportConnectError(err, 'Nolio activities'),
    });
  }

  connectNolioWrite(): void {
    this.nolioSync.connectWrite().subscribe({
      error: (err) => this.reportConnectError(err, 'Nolio workout push'),
    });
  }

  private reportConnectError(err: any, label: string): void {
    const backendMessage = err?.error?.message || err?.error?.detail;
    const message = backendMessage
      ? `${label}: ${backendMessage}`
      : `${label}: connection failed (status ${err?.status ?? 'unknown'}).`;
    alert(message);
  }

  toggleNolioAutoSync(enabled: boolean): void {
    this.nolioSync.setAutoSync(enabled).subscribe();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closed.emit();
  }
}

import {ChangeDetectionStrategy, Component, EventEmitter, HostListener, inject, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {AuthService, User} from '../../../services/auth.service';
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
export class ConnectedAppsModalComponent {
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  @Output() closed = new EventEmitter<void>();

  user$ = this.authService.user$;
  unlinking = false;

  getConnectedCount(user: User): number {
    if (!user.linkedAccounts) return 0;
    return [user.linkedAccounts.strava, user.linkedAccounts.google, user.linkedAccounts.garmin, user.linkedAccounts.zwift]
      .filter(Boolean).length;
  }

  canUnlink(user: User, provider: 'strava' | 'google'): boolean {
    if (!user.linkedAccounts) return false;
    const other = provider === 'strava' ? 'google' : 'strava';
    return user.linkedAccounts[other] === true;
  }

  unlinkApp(provider: 'strava' | 'google' | 'garmin' | 'zwift') {
    this.unlinking = true;
    let obs;
    switch (provider) {
      case 'strava': obs = this.authService.unlinkStrava(); break;
      case 'google': obs = this.authService.unlinkGoogle(); break;
      case 'garmin': obs = this.authService.unlinkGarmin(); break;
      case 'zwift': obs = this.authService.unlinkZwift(); break;
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

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closed.emit();
  }
}

import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {BluetoothService} from '../../../services/bluetooth.service';
import {AuthService} from '../../../services/auth.service';
import {ClubService, ClubSummary} from '../../../services/club.service';
import {TrainingFilterService} from '../../../services/training-filter.service';
import {combineLatest, map} from 'rxjs';
import {MembershipsModalComponent} from '../../shared/memberships-modal/memberships-modal.component';
import {NotificationPreferencesComponent} from '../../shared/notification-preferences/notification-preferences.component';
import {TranslateService, TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-top-bar',
  standalone: true,
  imports: [CommonModule, RouterModule, MembershipsModalComponent, NotificationPreferencesComponent, TranslateModule],
  templateUrl: './top-bar.component.html',
  styleUrl: './top-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopBarComponent {
  private router = inject(Router);
  private bluetoothService = inject(BluetoothService);
  private authService = inject(AuthService);
  clubService = inject(ClubService);
  private filterService = inject(TrainingFilterService);
  private translateService = inject(TranslateService);
  currentLang = this.translateService.currentLang || 'en';

  toggleLang(): void {
    this.currentLang = this.currentLang === 'en' ? 'fr' : 'en';
    this.translateService.use(this.currentLang);
    localStorage.setItem('lang', this.currentLang);
  }

  isAnalyticsOpen = false;
  isTrainingOpen = false;
  isClubsOpen = false;
  showMemberships = false;
  showNotifPrefs = false;

  user$ = this.authService.user$;
  isCoach$ = this.authService.user$.pipe(map(u => u?.role === 'COACH'));

  connectedCount$ = combineLatest([
    this.bluetoothService.trainerStatus$,
    this.bluetoothService.hrStatus$,
    this.bluetoothService.pmStatus$,
    this.bluetoothService.cadenceStatus$,
  ]).pipe(
    map(statuses => statuses.filter(s => s === 'Connected').length)
  );

  logout(): void {
    this.authService.logout();
  }

  toggleSettings() {
    this.authService.toggleSettings();
  }

  toggleMemberships() {
    this.showMemberships = !this.showMemberships;
  }

  toggleDevices() {
    this.bluetoothService.toggleDeviceManager();
  }

  toggleClubs(event: Event) {
    event.stopPropagation();
    this.isClubsOpen = !this.isClubsOpen;
    if (this.isClubsOpen) {
      this.isTrainingOpen = false;
      this.isAnalyticsOpen = false;
      this.clubService.loadUserClubs();
    }
  }

  closeClubs() {
    this.isClubsOpen = false;
  }

  selectClub(club: ClubSummary) {
    this.isClubsOpen = false;
    this.filterService.setContext(`club:${club.id}`);
    this.router.navigate(['/clubs', club.id]);
  }

  getMembershipLabel(status: string | undefined): string {
    if (!status) return '';
    if (status.startsWith('ACTIVE_OWNER')) return this.translateService.instant('COMMON.OWNER');
    if (status.startsWith('ACTIVE_ADMIN')) return this.translateService.instant('COMMON.ADMIN');
    if (status.startsWith('ACTIVE_COACH')) return this.translateService.instant('COMMON.COACH');
    if (status.startsWith('ACTIVE')) return this.translateService.instant('COMMON.MEMBER_ROLE');
    if (status.startsWith('PENDING')) return this.translateService.instant('COMMON.PENDING');
    return status;
  }

  toggleTraining(event: Event) {
    event.stopPropagation();
    this.isTrainingOpen = !this.isTrainingOpen;
    if (this.isTrainingOpen) { this.isAnalyticsOpen = false; this.isClubsOpen = false; }
  }

  closeTraining() {
    this.isTrainingOpen = false;
  }

  toggleAnalytics(event: Event) {
    event.stopPropagation();
    this.isAnalyticsOpen = !this.isAnalyticsOpen;
    if (this.isAnalyticsOpen) { this.isTrainingOpen = false; this.isClubsOpen = false; }
  }

  closeAnalytics() {
    this.isAnalyticsOpen = false;
  }

}

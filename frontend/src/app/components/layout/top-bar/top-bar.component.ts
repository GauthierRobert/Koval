import {ChangeDetectionStrategy, Component, DestroyRef, HostListener, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {BluetoothService} from '../../../services/bluetooth.service';
import {AuthService} from '../../../services/auth.service';
import {ClubService, ClubSummary} from '../../../services/club.service';
import {TrainingFilterService} from '../../../services/training-filter.service';
import {combineLatest, filter, map} from 'rxjs';
import {MembershipsModalComponent} from '../../shared/memberships-modal/memberships-modal.component';
import {NotificationPreferencesComponent} from '../../shared/notification-preferences/notification-preferences.component';
import {NotificationCenterComponent} from '../../shared/notification-center/notification-center.component';
import {InstallBannerComponent} from '../../shared/install-banner/install-banner.component';
import {NotificationCenterService} from '../../../services/notification-center.service';
import {ConnectedAppsModalComponent} from '../../shared/connected-apps-modal/connected-apps-modal.component';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {ThemeService} from '../../../services/theme.service';
import {ResponsiveService} from '../../../services/responsive.service';

@Component({
  selector: 'app-top-bar',
  standalone: true,
  imports: [CommonModule, RouterModule, MembershipsModalComponent, NotificationPreferencesComponent, NotificationCenterComponent, InstallBannerComponent, ConnectedAppsModalComponent, TranslateModule],
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
  themeService = inject(ThemeService);
  private responsive = inject(ResponsiveService);
  notificationCenter = inject(NotificationCenterService);
  private destroyRef = inject(DestroyRef);
  unreadNotifications$ = this.notificationCenter.unreadCount$;
  currentLang = this.translateService.currentLang || 'en';

  constructor() {
    // Auto-close mobile menu when leaving the mobile breakpoint
    this.responsive.isMobile$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((isMobile) => {
        if (!isMobile && this.mobileMenuOpen) {
          this.mobileMenuOpen = false;
        }
      });
    // Initial unread badge value once the user is authenticated.
    this.authService.user$
      .pipe(filter(u => !!u), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.notificationCenter.refreshUnreadCount());
  }

  toggleLang(): void {
    this.currentLang = this.currentLang === 'en' ? 'fr' : 'en';
    this.translateService.use(this.currentLang);
    localStorage.setItem('lang', this.currentLang);
  }

  isAnalyticsOpen = false;
  isTrainingOpen = false;
  isClubsOpen = false;
  isCoachingOpen = false;
  showMemberships = false;
  showNotifPrefs = false;
  showNotifCenter = false;
  showConnectedApps = false;
  mobileMenuOpen = false;

  goToAiAssistants(): void {
    this.closeMobileMenu();
    this.router.navigate(['/oauth-clients']);
  }

  toggleNotifCenter(): void {
    this.showNotifCenter = !this.showNotifCenter;
    if (this.showNotifCenter && this.authService.isAuthenticated()) {
      this.notificationCenter.refreshUnreadCount();
    }
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen = !this.mobileMenuOpen;
    if (!this.mobileMenuOpen) {
      this.isClubsOpen = false;
      this.isTrainingOpen = false;
      this.isAnalyticsOpen = false;
      this.isCoachingOpen = false;
    }
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen = false;
    this.isClubsOpen = false;
    this.isTrainingOpen = false;
    this.isAnalyticsOpen = false;
    this.isCoachingOpen = false;
  }

  user$ = this.authService.user$;
  isCoach$ = this.authService.isCoach$;

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
      this.isCoachingOpen = false;
      this.clubService.loadUserClubs();
    }
  }

  toggleCoaching(event: Event) {
    event.stopPropagation();
    this.isCoachingOpen = !this.isCoachingOpen;
    if (this.isCoachingOpen) {
      this.isTrainingOpen = false;
      this.isAnalyticsOpen = false;
      this.isClubsOpen = false;
    }
  }

  closeCoaching() {
    this.isCoachingOpen = false;
  }

  closeClubs() {
    this.isClubsOpen = false;
  }

  selectClub(club: ClubSummary) {
    this.isClubsOpen = false;
    this.closeMobileMenu();
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
    if (this.isTrainingOpen) { this.isAnalyticsOpen = false; this.isClubsOpen = false; this.isCoachingOpen = false; }
  }

  closeTraining() {
    this.isTrainingOpen = false;
  }

  toggleAnalytics(event: Event) {
    event.stopPropagation();
    this.isAnalyticsOpen = !this.isAnalyticsOpen;
    if (this.isAnalyticsOpen) { this.isTrainingOpen = false; this.isClubsOpen = false; this.isCoachingOpen = false; }
  }

  closeAnalytics() {
    this.isAnalyticsOpen = false;
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isClubsOpen || this.isTrainingOpen || this.isAnalyticsOpen || this.isCoachingOpen) {
      this.isClubsOpen = false;
      this.isTrainingOpen = false;
      this.isAnalyticsOpen = false;
      this.isCoachingOpen = false;
    }
  }

}

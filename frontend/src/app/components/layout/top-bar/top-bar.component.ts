import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {BluetoothService} from '../../../services/bluetooth.service';
import {AuthService} from '../../../services/auth.service';
import {ClubService, ClubSummary} from '../../../services/club.service';
import {TrainingService} from '../../../services/training.service';
import {combineLatest, map} from 'rxjs';
import {MembershipsModalComponent} from '../../shared/memberships-modal/memberships-modal.component';

@Component({
  selector: 'app-top-bar',
  standalone: true,
  imports: [CommonModule, RouterModule, MembershipsModalComponent],
  templateUrl: './top-bar.component.html',
  styleUrl: './top-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopBarComponent {
  private router = inject(Router);
  private bluetoothService = inject(BluetoothService);
  private authService = inject(AuthService);
  clubService = inject(ClubService);
  private trainingService = inject(TrainingService);

  isAnalyticsOpen = false;
  isTrainingOpen = false;
  isClubsOpen = false;
  showMemberships = false;

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
    this.trainingService.setSource('club');
    this.trainingService.setSelectedClubId(club.id);
    this.router.navigate(['/clubs', club.id]);
  }

  getMembershipLabel(status: string | undefined): string {
    if (!status) return '';
    if (status.startsWith('ACTIVE_OWNER')) return 'Owner';
    if (status.startsWith('ACTIVE_ADMIN')) return 'Admin';
    if (status.startsWith('ACTIVE_COACH')) return 'Coach';
    if (status.startsWith('ACTIVE')) return 'Member';
    if (status.startsWith('PENDING')) return 'Pending';
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

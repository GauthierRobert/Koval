import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {BluetoothService} from '../../../services/bluetooth.service';
import {AuthService} from '../../../services/auth.service';
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

  isAnalyticsOpen = false;
  isTrainingOpen = false;
  showMemberships = false;

  user$ = this.authService.user$;
  isCoach$ = this.authService.user$.pipe(map(u => u?.role === 'COACH'));
  uiMode$ = this.authService.uiMode$;

  setUiMode(mode: 'athlete' | 'coach'): void {
    this.authService.setUiMode(mode);
    if (mode === 'athlete') this.router.navigate(['/dashboard']);
    else this.router.navigate(['/coach']);
  }

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

  toggleTraining(event: Event) {
    event.stopPropagation();
    this.isTrainingOpen = !this.isTrainingOpen;
    if (this.isTrainingOpen) { this.isAnalyticsOpen = false; }
  }

  closeTraining() {
    this.isTrainingOpen = false;
  }

  toggleAnalytics(event: Event) {
    event.stopPropagation();
    this.isAnalyticsOpen = !this.isAnalyticsOpen;
    if (this.isAnalyticsOpen) { this.isTrainingOpen = false; }
  }

  closeAnalytics() {
    this.isAnalyticsOpen = false;
  }

}

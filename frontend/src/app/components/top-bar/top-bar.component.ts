import {Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {ChatService} from '../../services/chat.service';
import {BluetoothService} from '../../services/bluetooth.service';
import {AuthService} from '../../services/auth.service';
import {combineLatest, map} from 'rxjs';

@Component({
  selector: 'app-top-bar',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './top-bar.component.html',
  styleUrl: './top-bar.component.css',
})
export class TopBarComponent {
  private router = inject(Router);
  private chatService = inject(ChatService);
  private bluetoothService = inject(BluetoothService);
  private authService = inject(AuthService);

  isPopupOpen = false;
  isAnalyticsOpen = false;
  isTrainingOpen = false;
  requestDescription = '';

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

  toggleDevices() {
    this.bluetoothService.toggleDeviceManager();
  }

  toggleTraining(event: Event) {
    event.stopPropagation();
    this.isTrainingOpen = !this.isTrainingOpen;
    if (this.isTrainingOpen) { this.isPopupOpen = false; this.isAnalyticsOpen = false; }
  }

  closeTraining() {
    this.isTrainingOpen = false;
  }

  toggleAnalytics(event: Event) {
    event.stopPropagation();
    this.isAnalyticsOpen = !this.isAnalyticsOpen;
    if (this.isAnalyticsOpen) { this.isPopupOpen = false; this.isTrainingOpen = false; }
  }

  closeAnalytics() {
    this.isAnalyticsOpen = false;
  }

  togglePopup(event: Event) {
    event.stopPropagation();
    this.isPopupOpen = !this.isPopupOpen;
    if (this.isPopupOpen) { this.isAnalyticsOpen = false; this.isTrainingOpen = false; }
    if (!this.isPopupOpen) this.requestDescription = '';
  }

  closePopup() {
    this.isPopupOpen = false;
    this.requestDescription = '';
  }

  submitRequest() {
    if (!this.requestDescription.trim()) return;

    const desc = this.requestDescription.trim();
    this.closePopup();

    this.router.navigate(['/chat']).then(() => {
      setTimeout(() => {
        this.chatService.sendMessage(desc);
      }, 100);
    });
  }
}

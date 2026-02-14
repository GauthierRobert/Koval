import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../services/chat.service';
import { BluetoothService } from '../../services/bluetooth.service';
import { TrainingService } from '../../services/training.service';
import { combineLatest, map } from 'rxjs';

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
  private trainingService = inject(TrainingService);

  isPopupOpen = false;
  requestDescription = '';
  ftp$ = this.trainingService.ftp$;

  connectedCount$ = combineLatest([
    this.bluetoothService.trainerStatus$,
    this.bluetoothService.hrStatus$,
    this.bluetoothService.pmStatus$,
    this.bluetoothService.cadenceStatus$,
  ]).pipe(
    map(statuses => statuses.filter(s => s === 'Connected').length)
  );

  onFtpChange(ftp: number) {
    this.trainingService.setFtp(ftp);
  }

  toggleDevices() {
    this.bluetoothService.toggleDeviceManager();
  }

  togglePopup(event: Event) {
    event.stopPropagation();
    this.isPopupOpen = !this.isPopupOpen;
    if (!this.isPopupOpen) {
      this.requestDescription = '';
    }
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

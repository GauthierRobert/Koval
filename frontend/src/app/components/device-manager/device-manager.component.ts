import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BluetoothService } from '../../services/bluetooth.service';

@Component({
  selector: 'app-device-manager',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="device-manager glass">
      <div class="header">
        <h3>DEVICES</h3>
        <button class="close-btn" (click)="onClose()">✕</button>
      </div>

      <div class="device-list">
        <!-- Trainer -->
        <div class="device-item">
          <div class="icon">🚲</div>
          <div class="info">
            <div class="name">Smart Trainer</div>
            <div class="status" [class.connected]="(trainerStatus$ | async) === 'Connected'">
              {{ trainerStatus$ | async }}
            </div>
          </div>
          <button class="action-btn" 
                  [disabled]="(trainerStatus$ | async) === 'Scanning' || (trainerStatus$ | async) === 'Connecting'"
                  (click)="connectTrainer()">
            {{ (trainerStatus$ | async) === 'Connected' ? 'FORGET' : 'PAIR' }}
          </button>
        </div>

        <!-- Heart Rate -->
        <div class="device-item">
          <div class="icon">❤️</div>
          <div class="info">
            <div class="name">Heart Rate Monitor</div>
            <div class="status" [class.connected]="(hrStatus$ | async) === 'Connected'">
              {{ hrStatus$ | async }}
            </div>
          </div>
          <button class="action-btn" 
                  [disabled]="(hrStatus$ | async) === 'Scanning' || (hrStatus$ | async) === 'Connecting'"
                  (click)="connectHR()">
            {{ (hrStatus$ | async) === 'Connected' ? 'FORGET' : 'PAIR' }}
          </button>
        </div>

        <!-- Standalone Power Meter -->
        <div class="device-item">
          <div class="icon">⚡</div>
          <div class="info">
            <div class="name">Power Meter</div>
            <div class="status" [class.connected]="(pmStatus$ | async) === 'Connected'">
              {{ pmStatus$ | async }}
            </div>
          </div>
          <button class="action-btn" 
                  [disabled]="(pmStatus$ | async) === 'Scanning' || (pmStatus$ | async) === 'Connecting'"
                  (click)="connectPM()">
            {{ (pmStatus$ | async) === 'Connected' ? 'FORGET' : 'PAIR' }}
          </button>
        </div>

        <!-- Standalone Cadence -->
        <div class="device-item">
          <div class="icon">🔄</div>
          <div class="info">
            <div class="name">Cadence Sensor</div>
            <div class="status" [class.connected]="(cadenceStatus$ | async) === 'Connected'">
              {{ cadenceStatus$ | async }}
            </div>
          </div>
          <button class="action-btn" 
                  [disabled]="(cadenceStatus$ | async) === 'Scanning' || (cadenceStatus$ | async) === 'Connecting'"
                  (click)="connectCadence()">
            {{ (cadenceStatus$ | async) === 'Connected' ? 'FORGET' : 'PAIR' }}
          </button>
        </div>
      </div>

      <div class="footer-note">
        Ensure your devices are in pairing mode and Bluetooth is enabled in your browser.
      </div>
    </div>
  `,
  styles: [`
    .device-manager {
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 400px;
      padding: 24px;
      z-index: 3000;
      animation: slideUp 0.3s ease-out;
      background: rgba(20, 20, 20, 0.95);
    }
    @keyframes slideUp { from { opacity: 0; transform: translate(-50%, -40%); } }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }
    h3 { margin: 0; font-size: 18px; font-weight: 700; color: white; letter-spacing: 1px; }
    .close-btn { background: none; border: none; color: var(--text-muted); font-size: 20px; cursor: pointer; }

    .device-list { display: flex; flex-direction: column; gap: 16px; margin-bottom: 24px; }
    .device-item {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 12px;
      border: 1px solid var(--glass-border);
    }
    .icon { font-size: 24px; }
    .info { flex: 1; }
    .name { font-size: 14px; font-weight: 600; color: white; margin-bottom: 4px; }
    .status { font-size: 12px; color: var(--text-muted); }
    .status.connected { color: #2ecc71; font-weight: 700; }

    .action-btn {
      padding: 8px 16px;
      border-radius: 8px;
      font-weight: 700;
      font-size: 12px;
      cursor: pointer;
      transition: all 0.2s;
      background: rgba(255, 255, 255, 0.1);
      color: white;
      border: 1px solid var(--glass-border);
    }
    .action-btn:hover:not(:disabled) { background: white; color: black; }
    .action-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .footer-note { font-size: 11px; color: var(--text-muted); text-align: center; line-height: 1.4; }
  `]
})
export class DeviceManagerComponent {
  private bluetoothService = inject(BluetoothService);

  trainerStatus$ = this.bluetoothService.trainerStatus$;
  hrStatus$ = this.bluetoothService.hrStatus$;
  pmStatus$ = this.bluetoothService.pmStatus$;
  cadenceStatus$ = this.bluetoothService.cadenceStatus$;

  connectTrainer() {
    this.bluetoothService.connectTrainer();
  }

  connectHR() {
    this.bluetoothService.connectHeartRate();
  }

  connectPM() {
    this.bluetoothService.connectPowerMeter();
  }

  connectCadence() {
    this.bluetoothService.connectCadenceSensor();
  }

  onClose() {
    this.bluetoothService.toggleDeviceManager(false);
  }
}

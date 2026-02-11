import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TrainingService } from '../../services/training.service';
import { BluetoothService } from '../../services/bluetooth.service';
import { TrainingHistoryComponent } from '../training-history/training-history.component';
import { WorkoutHistoryComponent } from '../workout-history/workout-history.component';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, TrainingHistoryComponent, WorkoutHistoryComponent],
  template: `
    <aside class="sidebar glass">
      <div class="sidebar-content">
        <div class="view-tabs">
          <button class="tab-btn" [class.active]="viewMode === 'plans'" (click)="viewMode = 'plans'">PLANS</button>
          <button class="tab-btn" [class.active]="viewMode === 'history'" (click)="viewMode = 'history'">HISTORY</button>
        </div>
        <app-training-history *ngIf="viewMode === 'plans'"></app-training-history>
        <app-workout-history *ngIf="viewMode === 'history'"></app-workout-history>
      </div>
      <div class="sidebar-footer">
        <div class="device-status-bar" (click)="bluetoothService.toggleDeviceManager()">
           <div class="status-chip" [class.on]="(trainerStatus$ | async) === 'Connected'" title="Smart Trainer">🚲</div>
           <div class="status-chip" [class.on]="(hrStatus$ | async) === 'Connected'" title="Heart Rate">❤️</div>
           <div class="status-chip" [class.on]="(pmStatus$ | async) === 'Connected'" title="Power Meter">⚡</div>
           <div class="status-chip" [class.on]="(cadenceStatus$ | async) === 'Connected'" title="Cadence Sensor">🔄</div>
           <span class="status-text">DEVICES</span>
        </div>

        <div class="settings-panel" *ngIf="ftp$ | async as ftp">
          <label>Functional Threshold Power</label>
          <div class="ftp-input-wrapper">
            <input type="number" [ngModel]="ftp" (ngModelChange)="onFtpChange($event)" />
            <span>W</span>
          </div>
        </div>
        <div class="simulation-panel">
          <label class="switch">
            <input type="checkbox" (change)="toggleSimulation($event)">
            <span class="slider round"></span>
          </label>
          <span class="toggle-label">Simulate Trainer</span>
        </div>
        <div class="user-profile">
          <div class="avatar">GB</div>
          <div class="user-info">
            <span class="name">Senior Dev</span>
            <span class="status">Pro Cyclist</span>
          </div>
        </div>
      </div>
    </aside>
  `,
  styles: [`
    .sidebar {
      width: 280px;
      height: 100%;
      display: flex;
      flex-direction: column;
      background: var(--sidebar-color);
      border-right: 1px solid var(--glass-border);
      backdrop-filter: blur(20px);
      padding: 16px 0;
    }

    .sidebar-content {
      flex: 1;
      overflow-y: auto;
      padding: 0 12px;
      display: flex;
      flex-direction: column;
    }

    .view-tabs {
      display: flex;
      gap: 8px;
      padding: 12px;
      background: rgba(255,255,255,0.02);
      border-radius: 12px;
      margin-bottom: 16px;
    }

    .tab-btn {
      flex: 1;
      padding: 10px;
      background: transparent;
      border: 1px solid var(--glass-border);
      border-radius: 8px;
      color: var(--text-muted);
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
      transition: 0.2s;
    }

    .tab-btn:hover {
      background: var(--surface-hover);
      color: var(--text-color);
    }

    .tab-btn.active {
      background: var(--accent-color);
      color: white;
      border-color: var(--accent-color);
    }

    .sidebar-footer {
      padding: 24px;
      border-top: 1px solid var(--glass-border);
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .device-status-bar {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px;
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: 8px;
      cursor: pointer;
      transition: 0.2s;
    }

    .device-status-bar:hover {
      background: var(--surface-hover);
    }

    .status-chip {
      font-size: 16px;
      opacity: 0.3;
      filter: grayscale(1);
    }

    .status-chip.on {
      opacity: 1;
      filter: grayscale(0);
    }

    .status-text {
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 1px;
      color: var(--text-muted);
      margin-left: auto;
    }

    .settings-panel {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .settings-panel label {
      font-size: 11px;
      text-transform: uppercase;
      color: var(--text-muted);
      letter-spacing: 0.5px;
    }

    .ftp-input-wrapper {
      display: flex;
      align-items: center;
      gap: 10px;
      background: var(--surface-elevated);
      padding: 8px 12px;
      border-radius: 8px;
      border: 1px solid var(--glass-border);
    }

    .ftp-input-wrapper span {
      color: var(--text-muted);
      font-weight: 600;
      font-size: 12px;
    }

    .ftp-input-wrapper input {
      background: transparent;
      border: none;
      color: var(--text-color);
      font-weight: 700;
      width: 60px;
      outline: none;
    }

    .simulation-panel {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .toggle-label {
      font-size: 11px;
      color: var(--text-muted);
    }

    .user-profile {
      display: flex;
      align-items: center;
      gap: 12px;
      padding-top: 12px;
      border-top: 1px solid rgba(255,255,255,0.05);
    }

    .avatar {
      width: 36px;
      height: 36px;
      background: var(--accent-gradient);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 12px;
      color: #000;
    }

    .user-info {
      display: flex;
      flex-direction: column;
    }

    .name {
      font-size: 13px;
      font-weight: 700;
      color: var(--text-color);
    }

    .status {
      font-size: 10px;
      color: var(--accent-color);
    }

    .switch {
      position: relative;
      display: inline-block;
      width: 34px;
      height: 20px;
    }

    .switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(255, 255, 255, 0.12);
      transition: .4s;
    }

    .slider:before {
      position: absolute;
      content: "";
      height: 14px;
      width: 14px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: .4s;
    }

    input:checked + .slider {
      background-color: var(--accent-color);
    }

    input:checked + .slider:before {
      transform: translateX(14px);
    }

    .slider.round {
      border-radius: 20px;
    }

    .slider.round:before {
      border-radius: 50%;
    }
  `]
})
export class SidebarComponent {
  trainingService = inject(TrainingService);
  bluetoothService = inject(BluetoothService);

  viewMode: 'plans' | 'history' = 'plans';
  showDeviceManager = false;

  trainerStatus$ = this.bluetoothService.trainerStatus$;
  hrStatus$ = this.bluetoothService.hrStatus$;
  pmStatus$ = this.bluetoothService.pmStatus$;
  cadenceStatus$ = this.bluetoothService.cadenceStatus$;
  ftp$ = this.trainingService.ftp$;

  toggleDeviceManager() {
    this.showDeviceManager = !this.showDeviceManager;
  }

  onFtpChange(ftp: number) {
    this.trainingService.setFtp(ftp);
  }

  toggleSimulation(event: any) {
    this.bluetoothService.toggleSimulation(event.target.checked);
  }
}

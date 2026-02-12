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
  template: `
    <header class="top-bar glass">
      <div class="brand" routerLink="/dashboard" style="cursor: pointer">
        <div class="logo">
          <div class="logo-inner"></div>
        </div>
        <span class="app-title">Koval <span class="accent">TRAINING</span></span>
      </div>

      <nav class="nav-links">
        <a routerLink="/dashboard" routerLinkActive="active" class="nav-link">DASHBOARD</a>
        <a routerLink="/chat" routerLinkActive="active" class="nav-link">ASSISTANT</a>
        <a routerLink="/calendar" routerLinkActive="active" class="nav-link">CALENDAR</a>
        <a routerLink="/coach" routerLinkActive="active" class="nav-link">COACHING</a>
      </nav>

      <div class="right-section">
        <div class="status-indicators">
          <div class="status-item active">
            <span class="dot"></span>
            <span class="label">SYSTEM:</span>
            <span class="value">STABLE</span>
          </div>
        </div>

        <div class="topbar-controls">
          <div class="ftp-compact" title="Functional Threshold Power">
            <span class="ftp-label">FTP</span>
            <input
              type="number"
              class="ftp-input"
              [ngModel]="ftp$ | async"
              (ngModelChange)="onFtpChange($event)"
            />
            <span class="ftp-unit">W</span>
          </div>

          <button class="device-btn" (click)="toggleDevices()" title="Manage Devices">
            <svg class="bt-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6.5 6.5l11 11L12 23V1l5.5 5.5-11 11"/>
            </svg>
            <span class="device-badge" *ngIf="(connectedCount$ | async)! > 0">{{ connectedCount$ | async }}</span>
          </button>
        </div>

        <div class="action-wrapper">
          <button class="action-btn" (click)="togglePopup($event)">
            NEW TRAINING
          </button>

          <!-- Quick Chat Popup -->
          <div class="quick-chat-popup glass" *ngIf="isPopupOpen" (click)="$event.stopPropagation()">
            <div class="popup-header">
              <h4>Generate Training</h4>
              <button class="close-btn" (click)="closePopup()">×</button>
            </div>
            <p class="popup-intro">Describe your goal to the Assistant:</p>
            <textarea
              [(ngModel)]="requestDescription"
              placeholder="e.g., I want an FTP booster session with over-unders..."
              (keydown.enter)="$event.preventDefault(); submitRequest()"
              autofocus
            ></textarea>
            <div class="popup-footer">
              <button class="submit-btn" [disabled]="!requestDescription.trim()" (click)="submitRequest()">
                GENERATE TRAINING
              </button>
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- Overlay to close popup when clicking outside -->
    <div class="popup-overlay" *ngIf="isPopupOpen" (click)="closePopup()"></div>
  `,
  styles: [`
    .top-bar {
      height: 64px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0 32px;
      border-bottom: 1px solid var(--glass-border);
      background: rgba(15, 15, 17, 0.98);
      position: relative;
      z-index: 1000;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 16px;
      min-width: 280px;
    }

    .logo {
      width: 28px;
      height: 28px;
      background: var(--accent-color);
      transform: rotate(-10deg);
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
    }

    .logo-inner {
      width: 14px;
      height: 14px;
      background: white;
      clip-path: polygon(50% 0%, 100% 100%, 0% 100%);
    }

    .app-title {
      font-weight: 800;
      font-size: 14px;
      letter-spacing: 2px;
      color: var(--text-color);
    }

    .app-title .accent {
      color: var(--accent-color);
      font-weight: 400;
    }

    .nav-links {
      display: flex;
      gap: 40px;
      position: absolute;
      left: 50%;
      transform: translateX(-50%);
    }

    .nav-link {
      display: flex;
      align-items: center;
      color: var(--text-muted);
      text-decoration: none;
      font-weight: 700;
      font-size: 11px;
      letter-spacing: 0.15em;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      padding: 22px 0;
      position: relative;
    }

    .nav-link:hover {
      color: white;
    }

    .nav-link.active {
      color: white;
    }

    .nav-link.active::after {
      content: '';
      position: absolute;
      bottom: -1px;
      left: 0;
      width: 100%;
      height: 2px;
      background: var(--accent-color);
      box-shadow: 0 -2px 10px rgba(255, 102, 0, 0.3);
    }

    .right-section {
      display: flex;
      align-items: center;
      gap: 40px;
    }

    .status-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.1em;
    }

    .status-item .label { color: var(--text-dim); }
    .status-item .value { color: var(--text-muted); }

    .status-item.active .dot {
      width: 4px;
      height: 4px;
      background: var(--success-color);
      border-radius: 50%;
    }

    .topbar-controls {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .ftp-compact {
      display: flex;
      align-items: center;
      gap: 4px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      padding: 0 10px;
      height: 36px;
    }

    .ftp-label {
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 0.5px;
      color: var(--text-muted);
    }

    .ftp-input {
      background: transparent;
      border: none;
      color: var(--text-color);
      font-weight: 700;
      font-size: 13px;
      font-family: inherit;
      width: 40px;
      outline: none;
      text-align: right;
      -moz-appearance: textfield;
    }

    .ftp-input::-webkit-inner-spin-button,
    .ftp-input::-webkit-outer-spin-button {
      -webkit-appearance: none;
      margin: 0;
    }

    .ftp-unit {
      font-size: 10px;
      font-weight: 700;
      color: var(--text-muted);
    }

    .device-btn {
      position: relative;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      width: 36px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s;
      color: var(--text-muted);
    }

    .device-btn:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.2);
      color: var(--text-color);
    }

    .bt-icon {
      width: 16px;
      height: 16px;
    }

    .device-badge {
      position: absolute;
      top: -4px;
      right: -4px;
      background: var(--accent-color);
      color: #000;
      font-size: 9px;
      font-weight: 800;
      width: 16px;
      height: 16px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
    }

    .action-wrapper {
      position: relative;
    }

    .action-btn {
      background: rgba(255, 157, 0, 0.08);
      color: var(--accent-color);
      border: 1px solid rgba(255, 157, 0, 0.2);
      padding: 8px 24px;
      border-radius: 8px;
      font-weight: 800;
      font-size: 10px;
      letter-spacing: 0.1em;
      cursor: pointer;
      transition: all 0.2s;
    }

    .action-btn:hover {
      background: rgba(255, 157, 0, 0.15);
      border-color: var(--accent-color);
    }

    /* Quick Chat Popup Styles */
    .quick-chat-popup {
      position: absolute;
      top: 100%;
      right: 0;
      margin-top: 12px;
      width: 320px;
      background: rgba(22, 22, 28, 0.97);
      border: 1px solid var(--glass-border);
      border-radius: 16px;
      padding: 20px;
      box-shadow: 0 20px 40px rgba(0,0,0,0.4);
      animation: popupSlide 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      z-index: 1001;
    }

    @keyframes popupSlide {
      from { opacity: 0; transform: translateY(-10px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .popup-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .popup-header h4 {
      font-size: 12px;
      font-weight: 800;
      letter-spacing: 1px;
      margin: 0;
      color: var(--accent-color);
    }

    .close-btn {
      background: transparent;
      border: none;
      color: var(--text-muted);
      font-size: 20px;
      cursor: pointer;
      line-height: 1;
    }

    .popup-intro {
      font-size: 11px;
      color: var(--text-muted);
      margin-bottom: 12px;
    }

    textarea {
      width: 100%;
      height: 100px;
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      border-radius: 8px;
      color: var(--text-color);
      padding: 12px;
      font-family: inherit;
      font-size: 13px;
      resize: none;
      outline: none;
      margin-bottom: 16px;
      transition: border-color 0.2s;
    }

    textarea:focus {
      border-color: var(--accent-color);
    }

    .popup-footer {
      display: flex;
      justify-content: flex-end;
    }

    .submit-btn {
      background: var(--accent-color);
      color: white;
      border: none;
      padding: 10px 20px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 11px;
      letter-spacing: 0.5px;
      cursor: pointer;
      transition: transform 0.2s;
    }

    .submit-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .submit-btn:not(:disabled):hover {
      transform: scale(1.02);
    }

    .popup-overlay {
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      z-index: 999;
    }
  `]
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

    // Redirect to chat first to ensure components are ready
    this.router.navigate(['/chat']).then(() => {
      // Small timeout to allow AIChatPage to initialize and listen
      setTimeout(() => {
        this.chatService.sendMessage(desc);
      }, 100);
    });
  }
}

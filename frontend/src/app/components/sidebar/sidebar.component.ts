import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { BluetoothService } from '../../services/bluetooth.service';
import { AuthService, User } from '../../services/auth.service';
import { CoachService } from '../../services/coach.service';
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
        <div class="simulation-panel">
          <label class="switch">
            <input type="checkbox" (change)="toggleSimulation($event)">
            <span class="slider round"></span>
          </label>
          <span class="toggle-label">Simulate Trainer</span>
        </div>
        <ng-container *ngIf="user$ | async as user">
          <div class="join-section" *ngIf="user.role === 'ATHLETE'">
            <span class="join-title">JOIN A TEAM</span>
            <div class="join-row">
              <input
                class="join-input"
                [(ngModel)]="inviteCode"
                (keydown.enter)="redeemCode()"
                placeholder="CODE"
                maxlength="8"
              />
              <button class="join-btn" (click)="redeemCode()" [disabled]="(joining$ | async) || !inviteCode.trim()">
                {{ (joining$ | async) ? '...' : 'JOIN' }}
              </button>
            </div>
            <span class="join-error" *ngIf="joinError">{{ joinError }}</span>
          </div>
        </ng-container>
        <ng-container *ngIf="user$ | async as u">
          <div class="user-profile">
            <div class="avatar" *ngIf="!u.profilePicture">{{ getInitials(u.displayName) }}</div>
            <img class="avatar-img" *ngIf="u.profilePicture" [src]="u.profilePicture" alt="Profile" />
            <div class="user-info">
              <span class="name">{{ u.displayName }}</span>
              <span class="status">{{ u.role }}</span>
            </div>
            <button class="logout-btn" (click)="logout()" title="Logout">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                <polyline points="16 17 21 12 16 7"/>
                <line x1="21" y1="12" x2="9" y2="12"/>
              </svg>
            </button>
          </div>
        </ng-container>
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
      gap: 4px;
      padding: 12px;
      background: rgba(255,255,255,0.02);
      border-radius: 12px;
      margin-bottom: 4px;
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

    .join-section {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding-top: 12px;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
    }

    .join-title {
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 1px;
      color: var(--text-muted);
    }

    .join-row {
      display: flex;
      gap: 6px;
      align-items: center;
    }

    .join-input {
      flex: 1;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 6px;
      padding: 6px 10px;
      color: #e0e0e0;
      font-size: 12px;
      font-family: 'SF Mono', 'Fira Code', monospace;
      font-weight: 700;
      letter-spacing: 2px;
      text-transform: uppercase;
      outline: none;
    }

    .join-input:focus {
      border-color: var(--accent-color);
    }

    .join-input::placeholder {
      letter-spacing: 1px;
      font-size: 10px;
      font-weight: 600;
    }

    .join-btn {
      background: var(--accent-color);
      color: #000;
      border: none;
      padding: 6px 12px;
      border-radius: 6px;
      font-size: 10px;
      font-weight: 800;
      cursor: pointer;
      transition: filter 0.2s;
      white-space: nowrap;
    }

    .join-btn:hover { filter: brightness(1.15); }
    .join-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .join-error {
      font-size: 10px;
      color: var(--danger-color);
      font-weight: 600;
    }

    .avatar-img {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      object-fit: cover;
    }

    .logout-btn {
      margin-left: auto;
      background: transparent;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      padding: 6px;
      color: var(--text-muted);
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .logout-btn:hover {
      background: rgba(255, 255, 255, 0.08);
      color: var(--danger-color, #ef4444);
      border-color: rgba(255, 255, 255, 0.2);
    }
  `]
})
export class SidebarComponent {
  private bluetoothService = inject(BluetoothService);
  private authService = inject(AuthService);
  private coachService = inject(CoachService);

  viewMode: 'plans' | 'history' = 'plans';
  user$ = this.authService.user$;
  inviteCode = '';
  joinError = '';
  private joiningSubject = new BehaviorSubject<boolean>(false);
  joining$ = this.joiningSubject.asObservable();

  toggleSimulation(event: any) {
    this.bluetoothService.toggleSimulation(event.target.checked);
  }

  getInitials(name: string): string {
    return name?.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() || '?';
  }

  logout(): void {
    this.authService.logout();
  }

  redeemCode(): void {
    if (!this.inviteCode.trim()) return;
    this.joiningSubject.next(true);
    this.joinError = '';
    this.coachService.redeemInviteCode(this.inviteCode.trim()).subscribe({
      next: () => {
        this.joiningSubject.next(false);
        this.inviteCode = '';
        // Refresh user to pick up new tags from backend
        this.authService.refreshUser();
      },
      error: () => {
        this.joiningSubject.next(false);
        this.joinError = 'Invalid or expired code';
      },
    });
  }
}

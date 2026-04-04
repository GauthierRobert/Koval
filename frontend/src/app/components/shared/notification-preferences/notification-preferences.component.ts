import {ChangeDetectionStrategy, Component, EventEmitter, inject, isDevMode, NgZone, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {TranslateModule} from '@ngx-translate/core';

export interface NotificationPreferences {
  workoutAssigned: boolean;
  workoutReminder: boolean;
  workoutCompletedCoach: boolean;
  clubSessionCreated: boolean;
  clubSessionCancelled: boolean;
  waitingListPromoted: boolean;
  planActivated: boolean;
}

@Component({
  selector: 'app-notification-preferences',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="notif-prefs-overlay" (click)="close.emit()">
      <div class="notif-prefs-panel glass" (click)="$event.stopPropagation()">
        <div class="notif-prefs-header">
          <h3>{{ 'NOTIFICATIONS.TITLE' | translate }}</h3>
          <button class="close-btn" (click)="close.emit()">&times;</button>
        </div>

        @if (prefs$ | async; as prefs) {
          <div class="notif-prefs-list">
            <label class="notif-toggle">
              <span class="notif-label">{{ 'NOTIFICATIONS.WORKOUT_ASSIGNED' | translate }}</span>
              <input type="checkbox" [(ngModel)]="prefs.workoutAssigned" (ngModelChange)="save(prefs)" />
              <span class="toggle-track"></span>
            </label>
            <label class="notif-toggle">
              <span class="notif-label">{{ 'NOTIFICATIONS.WORKOUT_REMINDER' | translate }}</span>
              <input type="checkbox" [(ngModel)]="prefs.workoutReminder" (ngModelChange)="save(prefs)" />
              <span class="toggle-track"></span>
            </label>
            <label class="notif-toggle">
              <span class="notif-label">{{ 'NOTIFICATIONS.WORKOUT_COMPLETED_COACH' | translate }}</span>
              <input type="checkbox" [(ngModel)]="prefs.workoutCompletedCoach" (ngModelChange)="save(prefs)" />
              <span class="toggle-track"></span>
            </label>
            <label class="notif-toggle">
              <span class="notif-label">{{ 'NOTIFICATIONS.CLUB_SESSION_CREATED' | translate }}</span>
              <input type="checkbox" [(ngModel)]="prefs.clubSessionCreated" (ngModelChange)="save(prefs)" />
              <span class="toggle-track"></span>
            </label>
            <label class="notif-toggle">
              <span class="notif-label">{{ 'NOTIFICATIONS.CLUB_SESSION_CANCELLED' | translate }}</span>
              <input type="checkbox" [(ngModel)]="prefs.clubSessionCancelled" (ngModelChange)="save(prefs)" />
              <span class="toggle-track"></span>
            </label>
            <label class="notif-toggle">
              <span class="notif-label">{{ 'NOTIFICATIONS.WAITING_LIST_PROMOTED' | translate }}</span>
              <input type="checkbox" [(ngModel)]="prefs.waitingListPromoted" (ngModelChange)="save(prefs)" />
              <span class="toggle-track"></span>
            </label>
            <label class="notif-toggle">
              <span class="notif-label">{{ 'NOTIFICATIONS.PLAN_ACTIVATED' | translate }}</span>
              <input type="checkbox" [(ngModel)]="prefs.planActivated" (ngModelChange)="save(prefs)" />
              <span class="toggle-track"></span>
            </label>
          </div>
        } @else {
          <div class="notif-loading">{{ 'NOTIFICATIONS.LOADING' | translate }}</div>
        }

        @if (isDev) {
          <div class="test-section">
            <button class="test-btn" (click)="sendTest()" [disabled]="testSending$ | async">
              {{ (testSending$ | async) ? 'Sending...' : 'Send Test Notification' }}
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .notif-prefs-overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.5);
        z-index: 1000;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .notif-prefs-panel {
        width: 400px;
        max-width: 90vw;
        max-height: 80vh;
        overflow-y: auto;
        border-radius: var(--radius-lg);
        padding: 1.5rem;
      }
      .notif-prefs-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 1.5rem;
      }
      .notif-prefs-header h3 {
        margin: 0;
        font-size: 1.1rem;
        font-weight: 700;
        color: var(--text-color);
      }
      .close-btn {
        background: none;
        border: none;
        color: var(--text-muted);
        font-size: 1.5rem;
        cursor: pointer;
        padding: 0 4px;
        line-height: 1;
      }
      .notif-prefs-list {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .notif-toggle {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.6rem 0.75rem;
        border-radius: var(--radius-sm);
        background: var(--surface-raised);
        cursor: pointer;
        transition: background 0.15s;
      }
      .notif-toggle:hover {
        background: var(--surface-hover);
      }
      .notif-label {
        font-size: 0.85rem;
        color: var(--text-color);
        flex: 1;
      }
      .notif-toggle input {
        display: none;
      }
      .toggle-track {
        width: 40px;
        height: 22px;
        border-radius: 11px;
        background: var(--glass-border);
        position: relative;
        transition: background 0.2s;
        flex-shrink: 0;
      }
      .toggle-track::after {
        content: '';
        position: absolute;
        top: 2px;
        left: 2px;
        width: 18px;
        height: 18px;
        border-radius: 50%;
        background: var(--text-muted);
        transition: transform 0.2s, background 0.2s;
      }
      input:checked + .toggle-track {
        background: var(--accent-color);
      }
      input:checked + .toggle-track::after {
        transform: translateX(18px);
        background: var(--on-primary-color);
      }
      .notif-loading {
        text-align: center;
        color: var(--text-muted);
        padding: 2rem;
        font-size: 0.85rem;
      }
      .test-section {
        margin-top: 1.25rem;
        padding-top: 1rem;
        border-top: 1px solid var(--glass-border);
      }
      .test-btn {
        width: 100%;
        padding: 0.6rem;
        border: 1px dashed var(--accent-color);
        border-radius: var(--radius-sm);
        background: transparent;
        color: var(--accent-color);
        font-size: 0.85rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s, color 0.15s;
      }
      .test-btn:hover:not(:disabled) {
        background: var(--accent-color);
        color: var(--on-primary-color);
      }
      .test-btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    `,
  ],
})
export class NotificationPreferencesComponent implements OnInit {
  @Output() close = new EventEmitter<void>();

  private readonly http = inject(HttpClient);
  private readonly ngZone = inject(NgZone);
  private readonly apiUrl = `${environment.apiUrl}/api/notifications`;

  readonly isDev = isDevMode();
  testSending$ = new BehaviorSubject<boolean>(false);
  prefs$ = new BehaviorSubject<NotificationPreferences | null>(null);

  ngOnInit(): void {
    this.http.get<NotificationPreferences>(`${this.apiUrl}/preferences`).subscribe({
      next: (prefs) => this.ngZone.run(() => this.prefs$.next(prefs)),
      error: () =>
        this.ngZone.run(() =>
          this.prefs$.next({
            workoutAssigned: true,
            workoutReminder: true,
            workoutCompletedCoach: true,
            clubSessionCreated: true,
            clubSessionCancelled: true,
            waitingListPromoted: true,
            planActivated: true,
          }),
        ),
    });
  }

  save(prefs: NotificationPreferences): void {
    this.http.put<NotificationPreferences>(`${this.apiUrl}/preferences`, prefs).subscribe();
  }

  sendTest(): void {
    this.testSending$.next(true);
    this.http.post(`${this.apiUrl}/test`, {}).subscribe({
      next: () => this.ngZone.run(() => this.testSending$.next(false)),
      error: () => this.ngZone.run(() => this.testSending$.next(false)),
    });
  }
}

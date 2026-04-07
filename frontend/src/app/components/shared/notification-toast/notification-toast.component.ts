import {ChangeDetectionStrategy, Component, inject, NgZone, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {BehaviorSubject, Subscription} from 'rxjs';
import {filter} from 'rxjs/operators';
import {NotificationService} from '../../../services/notification.service';
import {ClubSessionService} from '../../../services/club-session.service';
import {MessagePayload} from 'firebase/messaging';

@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible$ | async) {
      <div class="toast-container" (click)="onToastClick()" role="status" aria-live="polite">
        <div class="toast" [class.toast-cancelled]="data['type'] === 'SESSION_CANCELLED'">
          <div class="toast-content">
            <strong>{{ title }}</strong>
            <span>{{ body }}</span>
            @if (data['type'] === 'WAITING_LIST_PROMOTED') {
              <div class="toast-actions">
                <button class="toast-refuse-btn" (click)="onRefuse($event)">REFUSE SPOT</button>
              </div>
            }
          </div>
          <button class="toast-close" (click)="dismiss($event)" aria-label="Dismiss notification">&times;</button>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .toast-container {
        position: fixed;
        top: 16px;
        right: 16px;
        z-index: 10000;
        cursor: pointer;
      }
      .toast {
        background: var(--glass-bg);
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-left: 4px solid var(--primary-color, #00c2ff);
        border-radius: 10px;
        padding: 14px 18px;
        display: flex;
        align-items: flex-start;
        gap: 12px;
        min-width: 300px;
        max-width: 420px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
        animation: slideIn 0.3s ease-out;
        backdrop-filter: none;
      }
      .toast-content {
        display: flex;
        flex-direction: column;
        gap: 4px;
        flex: 1;
      }
      .toast-content strong {
        color: var(--text-color);
        font-size: 14px;
      }
      .toast-content span {
        color: var(--text-muted);
        font-size: 13px;
      }
      .toast-close {
        background: none;
        border: none;
        color: var(--text-muted);
        font-size: 18px;
        cursor: pointer;
        padding: 0 4px;
        line-height: 1;
      }
      .toast-actions {
        margin-top: 8px;
      }
      .toast-refuse-btn {
        background: var(--danger-subtle);
        color: var(--danger-color, #ef4444);
        border: 1px solid var(--danger-border);;
        border-radius: 6px;
        padding: 4px 12px;
        font-size: 12px;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s;
      }
      .toast-refuse-btn:hover {
        background: rgba(239, 68, 68, 0.25);
      }
      .toast-cancelled {
        border-left-color:var(--danger-color, #ef4444);
      }
      @keyframes slideIn {
        from {
          transform: translateX(100%);
          opacity: 0;
        }
        to {
          transform: translateX(0);
          opacity: 1;
        }
      }
    `,
  ],
})
export class NotificationToastComponent implements OnInit, OnDestroy {
  private readonly notificationService = inject(NotificationService);
  private readonly clubSessionService = inject(ClubSessionService);
  private readonly router = inject(Router);
  private readonly ngZone = inject(NgZone);
  private sub: Subscription | null = null;
  private dismissTimer: ReturnType<typeof setTimeout> | null = null;

  visible$ = new BehaviorSubject<boolean>(false);
  title = '';
  body = '';
  data: Record<string, string> = {};

  ngOnInit(): void {
    this.sub = this.notificationService.foregroundNotification$
      .pipe(filter((p): p is MessagePayload => p !== null))
      .subscribe((payload) => {
        this.title = payload.notification?.title || 'Notification';
        this.body = payload.notification?.body || '';
        this.data = (payload.data as Record<string, string>) || {};
        this.ngZone.run(() => this.visible$.next(true));

        if (this.dismissTimer) clearTimeout(this.dismissTimer);
        const timeout = this.data['type'] === 'WAITING_LIST_PROMOTED' ? 15000 : 5000;
        this.dismissTimer = setTimeout(() => this.ngZone.run(() => this.visible$.next(false)), timeout);
      });
  }

  onToastClick(): void {
    this.visible$.next(false);
    const type = this.data['type'];
    if (type === 'TRAINING_ASSIGNED') {
      this.router.navigate(['/calendar']);
    } else if (type === 'SESSION_CREATED' || type === 'WAITING_LIST_PROMOTED' || type === 'SESSION_CANCELLED') {
      const clubId = this.data['clubId'];
      if (clubId) {
        this.router.navigate(['/clubs', clubId]);
      }
    }
  }

  onRefuse(event: Event): void {
    event.stopPropagation();
    const clubId = this.data['clubId'];
    const sessionId = this.data['sessionId'];
    if (clubId && sessionId) {
      this.clubSessionService.cancelSession(clubId, sessionId).subscribe({ error: () => {} });
    }
    this.visible$.next(false);
  }

  dismiss(event: Event): void {
    event.stopPropagation();
    this.visible$.next(false);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.dismissTimer) clearTimeout(this.dismissTimer);
  }
}

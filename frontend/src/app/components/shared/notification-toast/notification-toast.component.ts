import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { NotificationService } from '../../../services/notification.service';
import { ClubService } from '../../../services/club.service';
import { MessagePayload } from 'firebase/messaging';

@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container" *ngIf="visible" (click)="onToastClick()">
      <div class="toast">
        <div class="toast-content">
          <strong>{{ title }}</strong>
          <span>{{ body }}</span>
          <div class="toast-actions" *ngIf="data['type'] === 'WAITING_LIST_PROMOTED'">
            <button class="toast-refuse-btn" (click)="onRefuse($event)">REFUSE SPOT</button>
          </div>
        </div>
        <button class="toast-close" (click)="dismiss($event)">&times;</button>
      </div>
    </div>
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
        background: var(--surface-elevated, #18182a);
        border: 1px solid var(--border, rgba(255, 255, 255, 0.08));
        border-left: 4px solid var(--primary, #00c2ff);
        border-radius: 10px;
        padding: 14px 18px;
        display: flex;
        align-items: flex-start;
        gap: 12px;
        min-width: 300px;
        max-width: 420px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
        animation: slideIn 0.3s ease-out;
      }
      .toast-content {
        display: flex;
        flex-direction: column;
        gap: 4px;
        flex: 1;
      }
      .toast-content strong {
        color: var(--text-primary, #eeeef8);
        font-size: 14px;
      }
      .toast-content span {
        color: var(--text-secondary, #8080a0);
        font-size: 13px;
      }
      .toast-close {
        background: none;
        border: none;
        color: var(--text-muted, #404060);
        font-size: 18px;
        cursor: pointer;
        padding: 0 4px;
        line-height: 1;
      }
      .toast-actions {
        margin-top: 8px;
      }
      .toast-refuse-btn {
        background: rgba(239, 68, 68, 0.15);
        color: #f87171;
        border: 1px solid rgba(239, 68, 68, 0.3);
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
  private readonly clubService = inject(ClubService);
  private readonly router = inject(Router);
  private sub: Subscription | null = null;
  private dismissTimer: ReturnType<typeof setTimeout> | null = null;

  visible = false;
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
        this.visible = true;

        if (this.dismissTimer) clearTimeout(this.dismissTimer);
        const timeout = this.data['type'] === 'WAITING_LIST_PROMOTED' ? 15000 : 5000;
        this.dismissTimer = setTimeout(() => (this.visible = false), timeout);
      });
  }

  onToastClick(): void {
    this.visible = false;
    const type = this.data['type'];
    if (type === 'TRAINING_ASSIGNED') {
      this.router.navigate(['/calendar']);
    } else if (type === 'SESSION_CREATED' || type === 'WAITING_LIST_PROMOTED') {
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
      this.clubService.cancelSession(clubId, sessionId).subscribe({ error: () => {} });
    }
    this.visible = false;
  }

  dismiss(event: Event): void {
    event.stopPropagation();
    this.visible = false;
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.dismissTimer) clearTimeout(this.dismissTimer);
  }
}

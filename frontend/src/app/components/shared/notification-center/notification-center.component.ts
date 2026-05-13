import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  OnInit,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import {
  NotificationCenterService,
  PersistedNotification,
} from '../../../services/notification-center.service';

@Component({
  selector: 'app-notification-center',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="notif-center-overlay" (click)="closed.emit()">
      <div class="notif-center-panel glass" (click)="$event.stopPropagation()">
        <div class="notif-center-header">
          <h3>{{ 'NOTIFICATION_CENTER.TITLE' | translate }}</h3>
          <div class="notif-center-actions">
            @if ((center.unreadCount$ | async) ?? 0; as unread) {
              @if (unread > 0) {
                <button class="link-btn" (click)="center.markAllRead()">
                  {{ 'NOTIFICATION_CENTER.MARK_ALL_READ' | translate }}
                </button>
              }
            }
            <button
              class="icon-btn"
              type="button"
              (click)="openPrefs.emit()"
              [attr.aria-label]="'NOTIFICATION_CENTER.PREFERENCES' | translate"
              [title]="'NOTIFICATION_CENTER.PREFERENCES' | translate"
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="3" />
                <path
                  d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"
                />
              </svg>
            </button>
            <button class="close-btn" (click)="closed.emit()">&times;</button>
          </div>
        </div>

        @if (center.notifications$ | async; as items) {
          @if (items.length === 0) {
            <div class="notif-empty">{{ 'NOTIFICATION_CENTER.EMPTY' | translate }}</div>
          } @else {
            <ul class="notif-list">
              @for (n of items; track n.id) {
                <li class="notif-item" [class.unread]="!n.read" (click)="onClick(n)">
                  <div class="notif-content">
                    <div class="notif-title">{{ n.title }}</div>
                    <div class="notif-body">{{ n.body }}</div>
                    <div class="notif-time">{{ n.createdAt | date: 'short' }}</div>
                  </div>
                  <button
                    class="notif-delete"
                    (click)="onDelete($event, n.id)"
                    [attr.aria-label]="'NOTIFICATION_CENTER.DELETE' | translate"
                  >
                    &times;
                  </button>
                </li>
              }
            </ul>
          }
        }
      </div>
    </div>
  `,
  styles: [
    `
      .notif-center-overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.45);
        display: flex;
        justify-content: flex-end;
        align-items: flex-start;
        padding: 70px 16px 16px;
        z-index: 1000;
      }
      .notif-center-panel {
        width: 380px;
        max-width: 100%;
        max-height: 80vh;
        display: flex;
        flex-direction: column;
        border-radius: 12px;
        overflow: hidden;
      }
      .notif-center-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 14px 16px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      }
      .notif-center-header h3 {
        margin: 0;
        font-size: 15px;
      }
      .notif-center-actions {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .link-btn {
        background: none;
        border: none;
        color: var(--accent-color);
        cursor: pointer;
        font-size: 12px;
        padding: 4px 6px;
      }
      .icon-btn {
        background: none;
        border: none;
        color: inherit;
        opacity: 0.75;
        cursor: pointer;
        padding: 4px;
        display: inline-flex;
        align-items: center;
        border-radius: 6px;
        transition:
          opacity 0.15s,
          background 0.15s;
      }
      .icon-btn:hover {
        opacity: 1;
        background: rgba(255, 255, 255, 0.06);
      }
      .close-btn {
        background: none;
        border: none;
        color: inherit;
        font-size: 22px;
        cursor: pointer;
        line-height: 1;
      }
      .notif-empty {
        padding: 30px 16px;
        text-align: center;
        opacity: 0.65;
        font-size: 13px;
      }
      .notif-list {
        list-style: none;
        margin: 0;
        padding: 0;
        overflow-y: auto;
      }
      .notif-item {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 12px 16px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.05);
        cursor: pointer;
        transition: background 0.15s;
      }
      .notif-item:hover {
        background: rgba(255, 255, 255, 0.04);
      }
      .notif-item.unread {
        background: rgba(94, 177, 255, 0.06);
      }
      .notif-item.unread .notif-title {
        font-weight: 600;
      }
      .notif-content {
        flex: 1;
        min-width: 0;
      }
      .notif-title {
        font-size: 13px;
      }
      .notif-body {
        font-size: 12px;
        opacity: 0.8;
        margin-top: 2px;
      }
      .notif-time {
        font-size: 11px;
        opacity: 0.55;
        margin-top: 4px;
      }
      .notif-delete {
        background: none;
        border: none;
        color: inherit;
        opacity: 0.5;
        font-size: 18px;
        cursor: pointer;
        padding: 0 4px;
        line-height: 1;
      }
      .notif-delete:hover {
        opacity: 1;
      }
    `,
  ],
})
export class NotificationCenterComponent implements OnInit {
  protected center = inject(NotificationCenterService);
  private router = inject(Router);

  @Output() closed = new EventEmitter<void>();
  @Output() openPrefs = new EventEmitter<void>();

  ngOnInit(): void {
    this.center.loadPage(0, 50);
    this.center.refreshUnreadCount();
  }

  onClick(n: PersistedNotification): void {
    if (!n.read) {
      this.center.markRead(n.id);
    }
    this.navigateForNotification(n);
  }

  onDelete(event: Event, id: string): void {
    event.stopPropagation();
    this.center.delete(id);
  }

  private navigateForNotification(n: PersistedNotification): void {
    const data = n.data ?? {};
    const type = n.type ?? data['type'];
    if ((type === 'workoutAssigned' || type === 'TRAINING_ASSIGNED') && data['trainingId']) {
      this.router.navigate(['/dashboard']);
    } else if ((type === 'clubSessionCreated' || type === 'SESSION_CREATED') && data['clubId']) {
      this.router.navigate(['/clubs', data['clubId']]);
    } else if (data['clubId']) {
      this.router.navigate(['/clubs', data['clubId']]);
    }
    this.closed.emit();
  }
}

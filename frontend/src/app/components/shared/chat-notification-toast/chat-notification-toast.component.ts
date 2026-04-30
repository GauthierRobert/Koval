import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  ChatNotification,
  ChatNotificationService,
} from '../../../services/chat-notification.service';

/**
 * Stack of toast cards for incoming chat messages, mounted globally in
 * {@code app.component.html}. Subscribes to {@link ChatNotificationService}
 * which already filters out own messages, muted rooms, and the active room.
 *
 * Click a card → route to /messages/:roomId and dismiss.
 */
@Component({
  selector: 'app-chat-notification-toast',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chat-notif-stack" aria-live="polite">
      @for (n of (service.notifications$ | async) ?? []; track n.roomId) {
        <button
          type="button"
          class="chat-notif"
          [class.chat-notif--grouped]="n.count > 1"
          (click)="open(n)">
          <div class="chat-notif__head">
            <span class="chat-notif__title">{{ n.roomTitle }}</span>
            @if (n.count > 1) {
              <span class="chat-notif__count">{{ n.count }} new</span>
            }
            <span class="chat-notif__close" (click)="dismiss($event, n.roomId)" aria-label="Dismiss">×</span>
          </div>
          @if (n.count <= 1) {
            <div class="chat-notif__line">
              <strong>{{ n.recent[0].senderDisplayName }}:</strong>
              <span>{{ n.recent[0].content }}</span>
            </div>
          } @else {
            @for (m of n.recent; track $index) {
              <div class="chat-notif__line">
                <strong>{{ m.senderDisplayName }}:</strong>
                <span>{{ m.content }}</span>
              </div>
            }
          }
        </button>
      }
    </div>
  `,
  styles: [
    `
      .chat-notif-stack {
        position: fixed;
        top: 16px;
        right: 16px;
        z-index: 9999;
        display: flex;
        flex-direction: column;
        gap: 10px;
        max-width: 360px;
        pointer-events: none;
      }
      .chat-notif {
        pointer-events: auto;
        text-align: left;
        font: inherit;
        appearance: none;
        cursor: pointer;
        background: var(--glass-bg, rgba(20, 20, 27, 0.96));
        color: var(--text-primary, #fff);
        border: 1px solid var(--glass-border, rgba(255, 255, 255, 0.1));
        border-left: 4px solid #60a5fa;
        border-radius: 10px;
        padding: 10px 14px 12px;
        box-shadow: 0 8px 28px rgba(0, 0, 0, 0.5);
        animation: slideIn 0.25s ease-out;
        display: flex;
        flex-direction: column;
        gap: 4px;
        min-width: 280px;
      }
      .chat-notif:hover {
        background: var(--card-bg, rgba(28, 28, 36, 0.98));
      }
      .chat-notif--grouped {
        border-left-color: #fbbf24;
      }
      .chat-notif__head {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
        font-weight: 600;
      }
      .chat-notif__title {
        flex: 1;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .chat-notif__count {
        font-size: 11px;
        font-weight: 600;
        color: #fbbf24;
        background: rgba(251, 191, 36, 0.12);
        padding: 2px 6px;
        border-radius: 999px;
      }
      .chat-notif__close {
        cursor: pointer;
        color: var(--text-muted, rgba(255, 255, 255, 0.6));
        font-size: 18px;
        line-height: 1;
        padding: 0 4px;
      }
      .chat-notif__close:hover {
        color: var(--text-primary, #fff);
      }
      .chat-notif__line {
        font-size: 13px;
        color: var(--text-secondary, rgba(255, 255, 255, 0.85));
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .chat-notif__line strong {
        color: var(--text-primary, #fff);
        margin-right: 4px;
      }
      @keyframes slideIn {
        from {
          transform: translateX(110%);
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
export class ChatNotificationToastComponent {
  readonly service = inject(ChatNotificationService);
  private readonly router = inject(Router);

  open(n: ChatNotification): void {
    this.service.dismiss(n.roomId);
    this.router.navigate(['/messages', n.roomId]);
  }

  dismiss(event: Event, roomId: string): void {
    event.stopPropagation();
    this.service.dismiss(roomId);
  }
}

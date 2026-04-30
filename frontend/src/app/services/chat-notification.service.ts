import { inject, Injectable, NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { firstValueFrom } from 'rxjs';
import { ChatSseService } from './chat-sse.service';
import { ChatRoomService } from './chat-room.service';
import { ChatApiService } from './chat-api.service';
import { AuthService } from './auth.service';
import { ChatMessage, ChatRoomScope } from '../models/chat.models';

export interface ChatNotificationPreview {
  senderDisplayName: string;
  content: string;
}

export interface ChatNotification {
  roomId: string;
  roomTitle: string;
  scope: ChatRoomScope;
  /** Last few messages for preview. Older are dropped. */
  recent: ChatNotificationPreview[];
  /** Total messages aggregated since this toast was opened. */
  count: number;
  /** Last update — used to time-out auto-dismiss. */
  updatedAt: number;
}

/**
 * Listens to chat SSE and surfaces in-app + browser notifications for new
 * messages in rooms the user belongs to. Notifications from the same room
 * are grouped (WhatsApp-style); a click navigates to that room.
 *
 * Skipped: own messages, system messages, muted rooms, and messages for the
 * room currently being viewed (when the tab is visible).
 */
@Injectable({ providedIn: 'root' })
export class ChatNotificationService {
  private readonly sse = inject(ChatSseService);
  private readonly rooms = inject(ChatRoomService);
  private readonly api = inject(ChatApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  private readonly subject = new BehaviorSubject<ChatNotification[]>([]);
  readonly notifications$ = this.subject.asObservable();

  private static readonly AUTO_DISMISS_MS = 6000;
  private static readonly MAX_RECENT = 3;

  private timers = new Map<string, ReturnType<typeof setTimeout>>();
  /** Cache of room title/scope/muted, populated lazily for unknown rooms. */
  private roomCache = new Map<string, { title: string; scope: ChatRoomScope; muted: boolean }>();

  constructor() {
    this.sse.onChatMessage$.subscribe((msg) => this.handleIncoming(msg));
  }

  dismiss(roomId: string): void {
    const t = this.timers.get(roomId);
    if (t) {
      clearTimeout(t);
      this.timers.delete(roomId);
    }
    this.subject.next(this.subject.value.filter((n) => n.roomId !== roomId));
  }

  dismissAll(): void {
    for (const t of this.timers.values()) clearTimeout(t);
    this.timers.clear();
    this.subject.next([]);
  }

  /** Called on route change: drop any pending notification for the room being opened. */
  notifyRoomOpened(roomId: string): void {
    this.dismiss(roomId);
  }

  private async handleIncoming(msg: ChatMessage): Promise<void> {
    if (msg.type !== 'TEXT') return;
    if (msg.deleted) return;

    const me = this.auth.currentUser?.id;
    if (me && msg.senderId === me) return;

    if (
      this.rooms.activeRoomIdSnapshot === msg.roomId &&
      typeof document !== 'undefined' &&
      document.visibilityState === 'visible'
    ) {
      return;
    }

    const info = await this.resolveRoomInfo(msg.roomId);
    if (!info) return;
    if (info.muted) return;

    this.upsert(msg, info);
    this.maybeFireBrowserNotification(msg, info);
  }

  private upsert(msg: ChatMessage, info: { title: string; scope: ChatRoomScope }): void {
    const preview: ChatNotificationPreview = {
      senderDisplayName: msg.senderDisplayName,
      content: msg.content,
    };
    const current = this.subject.value;
    const existing = current.find((n) => n.roomId === msg.roomId);

    let next: ChatNotification[];
    if (existing) {
      const updated: ChatNotification = {
        ...existing,
        recent: [...existing.recent, preview].slice(-ChatNotificationService.MAX_RECENT),
        count: existing.count + 1,
        updatedAt: Date.now(),
      };
      next = current.map((n) => (n.roomId === msg.roomId ? updated : n));
    } else {
      next = [
        ...current,
        {
          roomId: msg.roomId,
          roomTitle: info.title,
          scope: info.scope,
          recent: [preview],
          count: 1,
          updatedAt: Date.now(),
        },
      ];
    }
    this.subject.next(next);
    this.scheduleDismiss(msg.roomId);
  }

  private scheduleDismiss(roomId: string): void {
    const existing = this.timers.get(roomId);
    if (existing) clearTimeout(existing);
    // Run timer outside Angular so we don't trigger CD until it fires.
    this.zone.runOutsideAngular(() => {
      const t = setTimeout(() => {
        this.zone.run(() => this.dismiss(roomId));
      }, ChatNotificationService.AUTO_DISMISS_MS);
      this.timers.set(roomId, t);
    });
  }

  private async resolveRoomInfo(
    roomId: string,
  ): Promise<{ title: string; scope: ChatRoomScope; muted: boolean } | null> {
    const cached = this.roomCache.get(roomId);
    if (cached) return cached;

    const room = this.rooms.roomsSnapshot.find((r) => r.id === roomId);
    if (room) {
      const info = { title: room.title, scope: room.scope, muted: room.muted };
      this.roomCache.set(roomId, info);
      return info;
    }

    try {
      const detail = await firstValueFrom(this.api.getRoom(roomId));
      const info = {
        title: detail.title,
        scope: detail.scope,
        muted: detail.currentUserMuted,
      };
      this.roomCache.set(roomId, info);
      return info;
    } catch {
      return null;
    }
  }

  private maybeFireBrowserNotification(
    msg: ChatMessage,
    info: { title: string },
  ): void {
    if (typeof window === 'undefined' || typeof Notification === 'undefined') return;
    if (Notification.permission !== 'granted') return;
    if (document.visibilityState === 'visible') return;

    try {
      const n = new Notification(info.title, {
        body: `${msg.senderDisplayName}: ${msg.content}`,
        tag: `chat-${msg.roomId}`,
        icon: '/assets/logo-128.png',
      });
      n.onclick = () => {
        window.focus();
        this.zone.run(() => this.router.navigate(['/messages', msg.roomId]));
        n.close();
      };
    } catch {
      // ignored — browser may reject the notification (e.g. iframe context)
    }
  }
}

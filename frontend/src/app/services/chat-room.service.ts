import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { ChatMessage, ChatRoomDetail, ChatRoomSummary } from '../models/chat.models';
import { ChatApiService } from './chat-api.service';
import { ChatSseService } from './chat-sse.service';

/**
 * Global state facade for the chat feature. Delegates all HTTP to {@link ChatApiService}
 * and merges SSE events from {@link ChatSseService} into its observables.
 *
 * Used by {@link ChatMessagePanelComponent} (the /messages page).
 * {@link EmbeddedChatComponent} uses {@link ChatApiService} directly for isolated state.
 */
@Injectable({ providedIn: 'root' })
export class ChatRoomService {
  private readonly api = inject(ChatApiService);
  private readonly sse = inject(ChatSseService);

  private roomsSubject = new BehaviorSubject<ChatRoomSummary[]>([]);
  rooms$ = this.roomsSubject.asObservable();

  private activeRoomIdSubject = new BehaviorSubject<string | null>(null);
  activeRoomId$ = this.activeRoomIdSubject.asObservable();

  private activeRoomMessagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  activeRoomMessages$ = this.activeRoomMessagesSubject.asObservable();

  private activeRoomDetailSubject = new BehaviorSubject<ChatRoomDetail | null>(null);
  activeRoomDetail$ = this.activeRoomDetailSubject.asObservable();

  get activeRoomMessagesSnapshot(): ChatMessage[] { return this.activeRoomMessagesSubject.value; }
  get activeRoomDetailSnapshot(): ChatRoomDetail | null { return this.activeRoomDetailSubject.value; }

  constructor() {
    this.sse.onChatMessage$.subscribe((msg) => this.handleIncomingMessage(msg));
  }

  initialize(): void {
    this.loadMyRooms();
    this.sse.connect();
  }

  shutdown(): void {
    this.sse.disconnect();
    this.roomsSubject.next([]);
    this.activeRoomIdSubject.next(null);
    this.activeRoomMessagesSubject.next([]);
    this.activeRoomDetailSubject.next(null);
  }

  loadMyRooms(): void {
    this.api.getRooms().subscribe({
      next: (rooms) => this.roomsSubject.next(rooms),
      error: () => this.roomsSubject.next([]),
    });
  }

  openRoom(roomId: string): void {
    this.activeRoomIdSubject.next(roomId);
    this.activeRoomMessagesSubject.next([]);
    this.activeRoomDetailSubject.next(null);
    this.api.getRoom(roomId).subscribe({ next: (d) => this.activeRoomDetailSubject.next(d) });
    this.api.getMessages(roomId).subscribe({
      next: (msgs) => this.activeRoomMessagesSubject.next(msgs),
      error: () => this.activeRoomMessagesSubject.next([]),
    });
    this.markRead(roomId);
  }

  loadOlderMessages(roomId: string, before: string): Observable<ChatMessage[]> {
    return this.api.getMessages(roomId, before).pipe(
      tap((older) => {
        if (this.activeRoomIdSubject.value === roomId) {
          this.activeRoomMessagesSubject.next([...older, ...this.activeRoomMessagesSubject.value]);
        }
      }),
    );
  }

  postMessage(roomId: string, content: string): Observable<ChatMessage> {
    return this.api.postMessage(roomId, content);
  }

  deleteMessage(messageId: string): Observable<void> {
    return this.api.deleteMessage(messageId).pipe(
      tap(() => {
        const msgs = this.activeRoomMessagesSubject.value.map((m) =>
          m.id === messageId ? { ...m, deleted: true, content: '' } : m,
        );
        this.activeRoomMessagesSubject.next(msgs);
      }),
    );
  }

  joinRoom(roomId: string): Observable<void> {
    return this.api.joinRoom(roomId).pipe(tap(() => this.loadMyRooms()));
  }

  leaveRoom(roomId: string): Observable<void> {
    return this.api.leaveRoom(roomId).pipe(
      tap(() => {
        this.roomsSubject.next(this.roomsSubject.value.filter((r) => r.id !== roomId));
        if (this.activeRoomIdSubject.value === roomId) {
          this.activeRoomIdSubject.next(null);
          this.activeRoomMessagesSubject.next([]);
          this.activeRoomDetailSubject.next(null);
        }
      }),
    );
  }

  setMuted(roomId: string, muted: boolean): Observable<void> {
    return this.api.setMuted(roomId, muted).pipe(
      tap(() => this.roomsSubject.next(
        this.roomsSubject.value.map((r) => r.id === roomId ? { ...r, muted } : r),
      )),
    );
  }

  markRead(roomId: string): void {
    this.api.markRead(roomId).subscribe({
      next: () => this.roomsSubject.next(
        this.roomsSubject.value.map((r) => r.id === roomId ? { ...r, unreadCount: 0 } : r),
      ),
    });
  }

  openDirectWith(otherUserId: string): Observable<ChatRoomDetail> {
    return this.api.createDirect(otherUserId).pipe(
      tap((detail) => { this.loadMyRooms(); this.openRoom(detail.id); }),
    );
  }

  // --- SSE handler ---

  private handleIncomingMessage(msg: ChatMessage): void {
    if (this.activeRoomIdSubject.value === msg.roomId) {
      this.activeRoomMessagesSubject.next([...this.activeRoomMessagesSubject.value, msg]);
      this.markRead(msg.roomId);
    }

    const rooms = this.roomsSubject.value;
    const idx = rooms.findIndex((r) => r.id === msg.roomId);
    if (idx >= 0) {
      const existing = rooms[idx];
      const isActive = this.activeRoomIdSubject.value === msg.roomId;
      const updated: ChatRoomSummary = {
        ...existing,
        lastMessageAt: msg.createdAt,
        lastMessagePreview: msg.content,
        lastMessageSenderId: msg.senderId,
        unreadCount: isActive ? 0 : existing.unreadCount + 1,
      };
      const next = [...rooms];
      next.splice(idx, 1);
      next.unshift(updated);
      this.roomsSubject.next(next);
    } else {
      this.loadMyRooms();
    }
  }
}

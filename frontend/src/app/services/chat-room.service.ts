import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ChatMessage,
  ChatRoomDetail,
  ChatRoomScope,
  ChatRoomSummary,
  CreateDirectRoomRequest,
  PostMessageRequest,
} from '../models/chat.models';
import { ChatSseService } from './chat-sse.service';

/**
 * State and HTTP client for the human-to-human chat feature.
 *
 * This is distinct from {@code ChatService} (the AI assistant chat) — do not confuse the two.
 * The SSE stream from {@link ChatSseService} feeds into this service's observables so
 * components don't need to subscribe to two sources.
 */
@Injectable({ providedIn: 'root' })
export class ChatRoomService {
  private readonly http = inject(HttpClient);
  private readonly sse = inject(ChatSseService);
  private readonly apiUrl = `${environment.apiUrl}/api/chat`;

  private roomsSubject = new BehaviorSubject<ChatRoomSummary[]>([]);
  rooms$: Observable<ChatRoomSummary[]> = this.roomsSubject.asObservable();

  private activeRoomIdSubject = new BehaviorSubject<string | null>(null);
  activeRoomId$: Observable<string | null> = this.activeRoomIdSubject.asObservable();

  private activeRoomMessagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  activeRoomMessages$: Observable<ChatMessage[]> = this.activeRoomMessagesSubject.asObservable();

  private activeRoomDetailSubject = new BehaviorSubject<ChatRoomDetail | null>(null);
  activeRoomDetail$: Observable<ChatRoomDetail | null> = this.activeRoomDetailSubject.asObservable();

  /** Sync snapshot for infinite-scroll to read oldest message timestamp. */
  get activeRoomMessagesSnapshot(): ChatMessage[] {
    return this.activeRoomMessagesSubject.value;
  }

  constructor() {
    // Merge live SSE events into the observed state. Because there is a single
    // per-user SSE connection, every message for every room flows through here.
    this.sse.onChatMessage$.subscribe((msg) => this.handleIncomingMessage(msg));
  }

  /** Call once on app start / login to prime the room list and connect the SSE stream. */
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
    this.http.get<ChatRoomSummary[]>(`${this.apiUrl}/rooms`).subscribe({
      next: (rooms) => this.roomsSubject.next(rooms),
      error: () => this.roomsSubject.next([]),
    });
  }

  openRoom(roomId: string): void {
    this.activeRoomIdSubject.next(roomId);
    this.activeRoomMessagesSubject.next([]);
    this.activeRoomDetailSubject.next(null);
    this.http.get<ChatRoomDetail>(`${this.apiUrl}/rooms/${roomId}`).subscribe({
      next: (detail) => this.activeRoomDetailSubject.next(detail),
    });
    this.http.get<ChatMessage[]>(`${this.apiUrl}/rooms/${roomId}/messages`).subscribe({
      next: (messages) => this.activeRoomMessagesSubject.next(messages),
      error: () => this.activeRoomMessagesSubject.next([]),
    });
    this.markRead(roomId);
  }

  loadOlderMessages(roomId: string, before: string): Observable<ChatMessage[]> {
    const url = `${this.apiUrl}/rooms/${roomId}/messages?before=${encodeURIComponent(before)}`;
    return this.http.get<ChatMessage[]>(url).pipe(
      tap((older) => {
        if (this.activeRoomIdSubject.value !== roomId) return;
        // Backend returns oldest-first, so prepend older batch to the head.
        this.activeRoomMessagesSubject.next([...older, ...this.activeRoomMessagesSubject.value]);
      }),
    );
  }

  postMessage(roomId: string, content: string): Observable<ChatMessage> {
    const body: PostMessageRequest = { content, clientNonce: this.makeNonce() };
    return this.http.post<ChatMessage>(`${this.apiUrl}/rooms/${roomId}/messages`, body);
  }

  deleteMessage(messageId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/messages/${messageId}`).pipe(
      tap(() => {
        // Mark the message as deleted in the local state.
        const msgs = this.activeRoomMessagesSubject.value.map((m) =>
          m.id === messageId ? { ...m, deleted: true, content: '' } : m,
        );
        this.activeRoomMessagesSubject.next(msgs);
      }),
    );
  }

  joinRoom(roomId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/rooms/${roomId}/join`, {}).pipe(
      tap(() => this.loadMyRooms()),
    );
  }

  leaveRoom(roomId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/rooms/${roomId}/leave`, {}).pipe(
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
    return this.http.post<void>(`${this.apiUrl}/rooms/${roomId}/mute`, { muted }).pipe(
      tap(() => {
        this.roomsSubject.next(
          this.roomsSubject.value.map((r) => (r.id === roomId ? { ...r, muted } : r)),
        );
      }),
    );
  }

  markRead(roomId: string): void {
    this.http.post<void>(`${this.apiUrl}/rooms/${roomId}/read`, {}).subscribe({
      next: () => {
        this.roomsSubject.next(
          this.roomsSubject.value.map((r) => (r.id === roomId ? { ...r, unreadCount: 0 } : r)),
        );
      },
    });
  }

  openDirectWith(otherUserId: string): Observable<ChatRoomDetail> {
    const body: CreateDirectRoomRequest = { otherUserId };
    return this.http.post<ChatRoomDetail>(`${this.apiUrl}/rooms/direct`, body).pipe(
      tap((detail) => {
        // The new DM room may not yet be in the list.
        this.loadMyRooms();
        this.openRoom(detail.id);
      }),
    );
  }

  findRoomByParent(
    scope: ChatRoomScope,
    clubId: string,
    refId?: string,
  ): Observable<ChatRoomDetail> {
    let url = `${this.apiUrl}/rooms/by-parent?scope=${scope}&clubId=${encodeURIComponent(clubId)}`;
    if (refId) url += `&refId=${encodeURIComponent(refId)}`;
    return this.http.get<ChatRoomDetail>(url);
  }

  // ---------- internal ----------

  private handleIncomingMessage(msg: ChatMessage): void {
    // Append to active room if it's currently open.
    if (this.activeRoomIdSubject.value === msg.roomId) {
      this.activeRoomMessagesSubject.next([...this.activeRoomMessagesSubject.value, msg]);
      // Mark as read in the background so the unread badge doesn't flash up.
      this.markRead(msg.roomId);
    }

    // Update the room summary (last message + unread count).
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
      // New room we didn't know about (e.g., someone just DM'd us). Refresh.
      this.loadMyRooms();
    }
  }

  private makeNonce(): string {
    return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }
}

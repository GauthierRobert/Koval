import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ChatMessage,
  ChatRoomDetail,
  ChatRoomScope,
  ChatRoomSummary,
} from '../models/chat.models';
import { generateNonce } from '../utils/chat-message.utils';

/**
 * Pure HTTP client for the chat REST API. No state, no BehaviorSubjects.
 *
 * Consumed only from inside the club detail page — by {@link ClubChatTabComponent}
 * (rooms list for last-message previews) and {@link EmbeddedChatComponent}
 * (isolated per-room state). The chat API must never be called outside the
 * club page; do not import this service from app boot, layout, or any other
 * route.
 */
@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/chat`;

  getRooms(): Observable<ChatRoomSummary[]> {
    return this.http.get<ChatRoomSummary[]>(`${this.base}/rooms`);
  }

  getRoom(roomId: string): Observable<ChatRoomDetail> {
    return this.http.get<ChatRoomDetail>(`${this.base}/rooms/${roomId}`);
  }

  getMessages(roomId: string, before?: string): Observable<ChatMessage[]> {
    let url = `${this.base}/rooms/${roomId}/messages`;
    if (before) url += `?before=${encodeURIComponent(before)}`;
    return this.http.get<ChatMessage[]>(url);
  }

  postMessage(roomId: string, content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.base}/rooms/${roomId}/messages`, {
      content,
      clientNonce: generateNonce(),
    });
  }

  deleteMessage(messageId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/messages/${messageId}`);
  }

  joinRoom(roomId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/rooms/${roomId}/join`, {});
  }

  leaveRoom(roomId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/rooms/${roomId}/leave`, {});
  }

  setMuted(roomId: string, muted: boolean): Observable<void> {
    return this.http.post<void>(`${this.base}/rooms/${roomId}/mute`, { muted });
  }

  markRead(roomId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/rooms/${roomId}/read`, {});
  }

  createDirect(otherUserId: string): Observable<ChatRoomDetail> {
    return this.http.post<ChatRoomDetail>(`${this.base}/rooms/direct`, { otherUserId });
  }

  findByParent(scope: ChatRoomScope, clubId: string, refId?: string, title?: string): Observable<ChatRoomDetail> {
    let url = `${this.base}/rooms/by-parent?scope=${scope}&clubId=${encodeURIComponent(clubId)}`;
    if (refId) url += `&refId=${encodeURIComponent(refId)}`;
    if (title) url += `&title=${encodeURIComponent(title)}`;
    return this.http.get<ChatRoomDetail>(url);
  }
}

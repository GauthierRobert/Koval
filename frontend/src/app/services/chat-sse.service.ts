import { inject, Injectable, NgZone } from '@angular/core';
import { Subject } from 'rxjs';
import { environment } from '../../environments/environment';
import { ChatMessage } from '../models/chat.models';

/**
 * Per-user SSE consumer for the chat stream.
 *
 * Mirrors the pattern used by {@code ClubFeedSseService} — uses {@code fetch} with
 * a streaming body reader so the Authorization header can be attached (the native
 * EventSource API does not support custom headers).
 */
@Injectable({ providedIn: 'root' })
export class ChatSseService {
  private ngZone = inject(NgZone);
  private abortController: AbortController | null = null;
  private reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
  private connected = false;

  private chatMessageSubject = new Subject<ChatMessage>();
  onChatMessage$ = this.chatMessageSubject.asObservable();

  connect(): void {
    if (this.connected) return;
    const token = localStorage.getItem('token');
    if (!token) return;

    this.connected = true;
    this.abortController = new AbortController();
    this.ngZone.runOutsideAngular(() => this.startStream(token));
  }

  disconnect(): void {
    this.connected = false;
    this.abortController?.abort();
    this.abortController = null;
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
  }

  private async startStream(token: string): Promise<void> {
    try {
      const response = await fetch(`${environment.apiUrl}/api/chat/stream`, {
        headers: { Authorization: `Bearer ${token}` },
        signal: this.abortController?.signal,
      });

      if (!response.ok || !response.body) {
        this.scheduleReconnect(token);
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        buffer = buffer.replace(/\r\n/g, '\n');
        const events = buffer.split('\n\n');
        buffer = events.pop() ?? '';

        for (const raw of events) {
          this.parseEvent(raw);
        }
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return;
    }

    if (this.connected) this.scheduleReconnect(token);
  }

  private scheduleReconnect(token: string): void {
    if (!this.connected) return;
    this.reconnectTimeout = setTimeout(() => this.startStream(token), 3000);
  }

  private parseEvent(raw: string): void {
    const lines = raw.trim().split('\n');
    let eventName = '';
    const dataLines: string[] = [];

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5));
      }
    }

    if (!eventName || dataLines.length === 0) return;
    const data = dataLines.join('\n');

    try {
      // The backend sends the payload as a JSON-encoded string inside the SSE data field.
      // First unwrap the outer string, then parse the inner JSON message.
      const parsedData = JSON.parse(data);
      const payload = typeof parsedData === 'string' ? JSON.parse(parsedData) : parsedData;
      this.ngZone.run(() => {
        if (eventName === 'chat_message') {
          this.chatMessageSubject.next(payload as ChatMessage);
        }
      });
    } catch {
      // Ignore parse errors (e.g., the "connected" heartbeat).
    }
  }
}

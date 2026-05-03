import { Injectable } from '@angular/core';
import { ChatMessage, ChatRoomDetail, ChatRoomScope } from '../models/chat.models';

export interface ChatRoomCacheEntry {
  detail: ChatRoomDetail;
  messages: ChatMessage[];
}

/**
 * Session-scoped cache keyed by the parent-scope identity used by
 * EmbeddedChatComponent (scope + clubId + refId + title).
 *
 * Lets tab switches between previously-visited rooms render synchronously
 * with no HTTP round-trip. SSE keeps the cached entries fresh.
 *
 * Two layers:
 * - in-memory `entries`: full detail + messages, dies on reload.
 * - localStorage `roomId-only` map: lets cold loads skip the expensive
 *   {@code GET /api/chat/rooms/by-parent} resolution and go straight to
 *   {@code GET /api/chat/rooms/{roomId}} + messages in parallel.
 */
@Injectable({ providedIn: 'root' })
export class ChatRoomCacheService {
  private static readonly LS_KEY = 'chat-room-id-cache-v1';

  private readonly entries = new Map<string, ChatRoomCacheEntry>();

  static keyFor(scope: ChatRoomScope, clubId: string, refId?: string, title?: string): string {
    return `${scope}|${clubId}|${refId ?? ''}|${title ?? ''}`;
  }

  get(key: string): ChatRoomCacheEntry | undefined {
    return this.entries.get(key);
  }

  set(key: string, entry: ChatRoomCacheEntry): void {
    this.entries.set(key, { detail: entry.detail, messages: [...entry.messages] });
    this.setPersistedRoomId(key, entry.detail.id);
  }

  setDetail(key: string, detail: ChatRoomDetail): void {
    const existing = this.entries.get(key);
    this.entries.set(key, { detail, messages: existing?.messages ?? [] });
    this.setPersistedRoomId(key, detail.id);
  }

  replaceMessages(key: string, messages: ChatMessage[]): void {
    const existing = this.entries.get(key);
    if (!existing) return;
    this.entries.set(key, { detail: existing.detail, messages: [...messages] });
  }

  appendMessage(key: string, message: ChatMessage): void {
    const existing = this.entries.get(key);
    if (!existing) return;
    if (existing.messages.some((m) => m.id === message.id)) return;
    this.entries.set(key, {
      detail: existing.detail,
      messages: [...existing.messages, message],
    });
  }

  prependMessages(key: string, older: ChatMessage[]): void {
    const existing = this.entries.get(key);
    if (!existing) return;
    this.entries.set(key, {
      detail: existing.detail,
      messages: [...older, ...existing.messages],
    });
  }

  updateMessage(key: string, messageId: string, patch: Partial<ChatMessage>): void {
    const existing = this.entries.get(key);
    if (!existing) return;
    this.entries.set(key, {
      detail: existing.detail,
      messages: existing.messages.map((m) => (m.id === messageId ? { ...m, ...patch } : m)),
    });
  }

  clear(): void {
    this.entries.clear();
  }

  // ---- localStorage roomId fast-path ----

  /** Returns a previously-resolved roomId for {@code key}, or null if absent. */
  getPersistedRoomId(key: string): string | null {
    return this.readPersisted()[key] ?? null;
  }

  setPersistedRoomId(key: string, roomId: string): void {
    const map = this.readPersisted();
    if (map[key] === roomId) return;
    map[key] = roomId;
    this.writePersisted(map);
  }

  /** Drop a stale roomId — e.g. after the room responds 404. */
  clearPersistedRoomId(key: string): void {
    const map = this.readPersisted();
    if (!(key in map)) return;
    delete map[key];
    this.writePersisted(map);
  }

  private readPersisted(): Record<string, string> {
    try {
      const raw = localStorage.getItem(ChatRoomCacheService.LS_KEY);
      return raw ? (JSON.parse(raw) as Record<string, string>) : {};
    } catch {
      return {};
    }
  }

  private writePersisted(map: Record<string, string>): void {
    try {
      localStorage.setItem(ChatRoomCacheService.LS_KEY, JSON.stringify(map));
    } catch {
      // Quota exceeded or storage unavailable — fast-path is best-effort.
    }
  }
}

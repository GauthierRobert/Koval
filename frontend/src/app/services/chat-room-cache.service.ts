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
 */
@Injectable({ providedIn: 'root' })
export class ChatRoomCacheService {
  private readonly entries = new Map<string, ChatRoomCacheEntry>();

  static keyFor(scope: ChatRoomScope, clubId: string, refId?: string, title?: string): string {
    return `${scope}|${clubId}|${refId ?? ''}|${title ?? ''}`;
  }

  get(key: string): ChatRoomCacheEntry | undefined {
    return this.entries.get(key);
  }

  set(key: string, entry: ChatRoomCacheEntry): void {
    this.entries.set(key, { detail: entry.detail, messages: [...entry.messages] });
  }

  setDetail(key: string, detail: ChatRoomDetail): void {
    const existing = this.entries.get(key);
    this.entries.set(key, { detail, messages: existing?.messages ?? [] });
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
}

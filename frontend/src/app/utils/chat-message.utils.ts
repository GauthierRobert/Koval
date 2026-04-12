import { ChatMessage } from '../models/chat.models';

/** Max time gap (ms) to group consecutive messages from the same sender. */
export const GROUP_GAP_MS = 5 * 60 * 1000;

/** Should we show the full header (avatar + name) or collapse into the previous bubble? */
export function isGroupStart(msg: ChatMessage, index: number, messages: ChatMessage[]): boolean {
  if (msg.type === 'SYSTEM') return false;
  if (index === 0) return true;
  const prev = messages[index - 1];
  if (prev.type === 'SYSTEM') return true;
  if (prev.senderId !== msg.senderId) return true;
  return new Date(msg.createdAt).getTime() - new Date(prev.createdAt).getTime() > GROUP_GAP_MS;
}

/** Show a date separator line above this message if it's a new calendar day. */
export function isNewDay(msg: ChatMessage, index: number, messages: ChatMessage[]): boolean {
  if (index === 0) return true;
  return new Date(messages[index - 1].createdAt).toDateString() !== new Date(msg.createdAt).toDateString();
}

/** Human-friendly date label for chat separators. */
export function formatChatDate(isoDate: string): string {
  const d = new Date(isoDate);
  const today = new Date();
  if (d.toDateString() === today.toDateString()) return 'Today';
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
}

/** Idempotency nonce for message posts. */
export function generateNonce(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

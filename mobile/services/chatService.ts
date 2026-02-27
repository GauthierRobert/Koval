import {getToken} from './api';
import {API_URL} from '../constants/env';

export interface ChatHistory {
  id: string;
  title?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ConversationMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatHistoryDetail {
  metadata: ChatHistory;
  messages: ConversationMessage[];
}

export type SSEEventType = 'status' | 'content' | 'conversation_id' | 'tool_call' | 'tool_result';

export interface SSEEvent {
  event: SSEEventType;
  data: string;
}

/** Fetch all chat histories for the current user. */
export async function fetchChatHistories(): Promise<ChatHistory[]> {
  const token = await getToken();
  const res = await fetch(`${API_URL}/api/ai/history`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed to load chat histories');
  return res.json();
}

/** Fetch full conversation for a history ID. */
export async function fetchChatHistory(id: string): Promise<ChatHistoryDetail> {
  const token = await getToken();
  const res = await fetch(`${API_URL}/api/ai/history/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed to load chat history');
  return res.json();
}

/** Delete a chat history. */
export async function deleteChatHistory(id: string): Promise<void> {
  const token = await getToken();
  await fetch(`${API_URL}/api/ai/history/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}

/**
 * Stream a chat message. Yields SSEEvent objects parsed from the SSE stream.
 * The caller is responsible for consuming the async generator.
 */
export async function* streamChat(
  message: string,
  chatHistoryId: string | null
): AsyncGenerator<SSEEvent> {
  const token = await getToken();

  const res = await fetch(`${API_URL}/api/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ message, chatHistoryId }),
  });

  if (!res.ok || !res.body) {
    throw new Error(`Stream request failed: ${res.status}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // SSE format: one or more "event: X\ndata: Y\n\n" blocks
    const blocks = buffer.split('\n\n');
    // Keep the last (possibly incomplete) block in the buffer
    buffer = blocks.pop() ?? '';

    for (const block of blocks) {
      const lines = block.split('\n');
      let eventType: SSEEventType = 'content';
      let data = '';
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.slice(6).trim() as SSEEventType;
        } else if (line.startsWith('data:')) {
          data = line.slice(5).trim();
        }
      }
      if (data) {
        yield { event: eventType, data };
      }
    }
  }
}

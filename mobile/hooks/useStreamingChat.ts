import {useCallback, useRef, useState} from 'react';
import {
    ChatHistory,
    deleteChatHistory,
    fetchChatHistories,
    fetchChatHistory,
    streamChat,
} from '../services/chatService';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  streaming?: boolean;
}

interface UseStreamingChatReturn {
  messages: Message[];
  histories: ChatHistory[];
  activeChatId: string | null;
  isStreaming: boolean;
  error: string | null;
  sendMessage: (text: string) => Promise<void>;
  loadHistory: (id: string) => Promise<void>;
  loadHistories: () => Promise<void>;
  newChat: () => void;
  removeHistory: (id: string) => Promise<void>;
}

let msgCounter = 0;
function uid() {
  return String(++msgCounter);
}

export function useStreamingChat(): UseStreamingChatReturn {
  const [messages, setMessages] = useState<Message[]>([]);
  const [histories, setHistories] = useState<ChatHistory[]>([]);
  const [activeChatId, setActiveChatId] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Hold the resolved chatHistoryId across streaming chunks
  const chatIdRef = useRef<string | null>(null);

  const sendMessage = useCallback(async (text: string) => {
    if (!text.trim() || isStreaming) return;
    setError(null);

    const userMsg: Message = { id: uid(), role: 'user', content: text };
    const assistantMsgId = uid();
    const assistantMsg: Message = { id: assistantMsgId, role: 'assistant', content: '', streaming: true };

    setMessages(prev => [...prev, userMsg, assistantMsg]);
    setIsStreaming(true);

    try {
      let fullContent = '';
      for await (const event of streamChat(text, chatIdRef.current ?? activeChatId)) {
        if (event.event === 'content') {
          fullContent += event.data;
          setMessages(prev =>
            prev.map(m => (m.id === assistantMsgId ? { ...m, content: fullContent } : m))
          );
        } else if (event.event === 'conversation_id') {
          chatIdRef.current = event.data;
          setActiveChatId(event.data);
        }
      }
      // Mark streaming done
      setMessages(prev =>
        prev.map(m => (m.id === assistantMsgId ? { ...m, streaming: false } : m))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Streaming error');
      setMessages(prev =>
        prev.map(m =>
          m.id === assistantMsgId
            ? { ...m, content: 'Error: failed to get response', streaming: false }
            : m
        )
      );
    } finally {
      setIsStreaming(false);
    }
  }, [isStreaming, activeChatId]);

  const loadHistories = useCallback(async () => {
    try {
      const list = await fetchChatHistories();
      setHistories(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load histories');
    }
  }, []);

  const loadHistory = useCallback(async (id: string) => {
    try {
      const detail = await fetchChatHistory(id);
      setActiveChatId(id);
      chatIdRef.current = id;
      setMessages(
        detail.messages.map(m => ({
          id: uid(),
          role: m.role as 'user' | 'assistant',
          content: m.content,
        }))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load history');
    }
  }, []);

  const newChat = useCallback(() => {
    setMessages([]);
    setActiveChatId(null);
    chatIdRef.current = null;
    setError(null);
  }, []);

  const removeHistory = useCallback(async (id: string) => {
    await deleteChatHistory(id);
    setHistories(prev => prev.filter(h => h.id !== id));
    if (activeChatId === id) newChat();
  }, [activeChatId, newChat]);

  return {
    messages,
    histories,
    activeChatId,
    isStreaming,
    error,
    sendMessage,
    loadHistory,
    loadHistories,
    newChat,
    removeHistory,
  };
}

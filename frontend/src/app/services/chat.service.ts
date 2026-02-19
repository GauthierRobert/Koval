import { Injectable, NgZone, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { skip, take } from 'rxjs/operators';
import { TrainingService } from './training.service';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  createdTraining?: { id: string; title: string; sportType: string; estimatedDurationSeconds?: number };
}

export interface ChatHistoryItem {
  id: string;
  userId: string;
  title: string;
  startedAt: string;
  lastUpdatedAt: string;
}

export type ActivityStatus = 'idle' | 'in_progress' | 'complete' | 'error';

interface ChatHistoryDetail {
  metadata: ChatHistoryItem;
  messages: { role: string; content: string }[];
}

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private apiUrl = 'http://localhost:8080/api/ai';
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);
  private trainingService = inject(TrainingService);

  private chatMessagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  chatMessages$ = this.chatMessagesSubject.asObservable();

  private chatHistoriesSubject = new BehaviorSubject<ChatHistoryItem[]>([]);
  chatHistories$ = this.chatHistoriesSubject.asObservable();

  private streamingSubject = new BehaviorSubject<boolean>(false);
  streaming$ = this.streamingSubject.asObservable();

  private activeChatIdSubject = new BehaviorSubject<string | null>(null);
  activeChatId$ = this.activeChatIdSubject.asObservable();

  private activityStatusSubject = new BehaviorSubject<ActivityStatus>('idle');
  activityStatus$ = this.activityStatusSubject.asObservable();

  private emitScheduled = false;
  private pendingEmit = false;

  private emit(): void {
    this.pendingEmit = true;

    if (this.emitScheduled) return;
    this.emitScheduled = true;

    requestAnimationFrame(() => {
      this.emitScheduled = false;
      if (this.pendingEmit) {
        this.pendingEmit = false;
        this.ngZone.run(() => {
          this.chatMessagesSubject.next([...this.chatMessagesSubject.value]);
        });
      }
    });
  }

  private emitImmediate(): void {
    this.emitScheduled = false;
    this.pendingEmit = false;
    this.ngZone.run(() => {
      this.chatMessagesSubject.next([...this.chatMessagesSubject.value]);
    });
  }

  async sendMessage(message: string): Promise<void> {
    return this.sendMessageStream(message);
  }

  private async sendMessageStream(message: string): Promise<void> {
    // Snapshot current training IDs to detect newly created ones after the response
    const knownIds = new Set<string>();
    this.trainingService.trainings$.pipe(take(1)).subscribe((ts) => ts.forEach((t) => knownIds.add(t.id)));

    this.addMessage({ role: 'user', content: message, timestamp: new Date() });

    const aiMessage: ChatMessage = { role: 'assistant', content: '', timestamp: new Date() };
    this.addMessage(aiMessage);
    this.ngZone.run(() => this.streamingSubject.next(true));

    try {
      const jwt = localStorage.getItem('token');
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };
      if (jwt) {
        headers['Authorization'] = `Bearer ${jwt}`;
      }

      const response = await fetch(`${this.apiUrl}/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          message,
          chatHistoryId: this.activeChatIdSubject.value,
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

        const events = buffer.split('\n\n');

        buffer = events.pop() || '';

        for (const event of events) {
          if (!event.trim()) continue;

          const lines = event.split('\n');
          let eventType = '';
          const dataLines: string[] = [];

          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              dataLines.push(line.substring(5));
            } else if (line.startsWith('id:') || line.startsWith('retry:')) {
              continue;
            } else if (line.startsWith(':')) {
              continue;
            }
          }

          const data = dataLines.join('\n');

          if (eventType === 'status') {
            const statusValue = data.trim() as ActivityStatus;
            this.ngZone.run(() => {
              this.activityStatusSubject.next(statusValue);
            });
          } else if (eventType === 'content') {
            aiMessage.content += data;
            this.emit();
          } else if (eventType === 'conversation_id') {
            const conversationId = data.trim();
            if (conversationId) {
              this.ngZone.run(() => this.activeChatIdSubject.next(conversationId));
            }
          }
        }
      }

      if (buffer.trim()) {
        const lines = buffer.split('\n');
        let eventType = '';
        const dataLines: string[] = [];

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            dataLines.push(line.substring(5));
          }
        }

        const data = dataLines.join('\n');

        if (eventType === 'content' && data) {
          aiMessage.content += data;
          this.emit();
        }
      }

      this.emitImmediate();

      this.loadHistories();

      // Detect newly created training: subscribe before calling loadTrainings so we catch the emission
      this.trainingService.trainings$.pipe(skip(1), take(1)).subscribe((newTrainings) => {
        const newT = newTrainings.find((t) => !knownIds.has(t.id));
        if (newT) {
          aiMessage.createdTraining = {
            id: newT.id,
            title: newT.title,
            sportType: newT.sportType,
            estimatedDurationSeconds: newT.estimatedDurationSeconds,
          };
          this.ngZone.run(() => this.emitImmediate());
        }
      });
      this.trainingService.loadTrainings();
    } catch {
      if (!aiMessage.content) {
        aiMessage.content =
          "Sorry, I'm having trouble connecting to the assistant. Is the system operational?";
      }
      this.emit();
    } finally {
      this.ngZone.run(() => {
        this.streamingSubject.next(false);
      });
    }
  }

  loadHistories(): void {
    this.http
      .get<ChatHistoryItem[]>(`${this.apiUrl}/history`)
      .subscribe({
        next: (histories) => this.chatHistoriesSubject.next(histories),
        error: () => this.chatHistoriesSubject.next([]),
      });
  }

  loadConversation(chatHistoryId: string): void {
    this.http.get<ChatHistoryDetail>(`${this.apiUrl}/history/${chatHistoryId}`).subscribe({
      next: (detail) => {
        this.activeChatIdSubject.next(chatHistoryId);
        const messages: ChatMessage[] = detail.messages
          .filter((m) => m.role === 'user' || m.role === 'assistant')
          .map((m) => ({
            role: m.role as 'user' | 'assistant',
            content: m.content,
            timestamp: new Date(detail.metadata.lastUpdatedAt),
          }));
        this.chatMessagesSubject.next(messages);
      },
      error: () => this.newChat(),
    });
  }

  deleteConversation(chatHistoryId: string): void {
    this.http.delete(`${this.apiUrl}/history/${chatHistoryId}`).subscribe({
      next: () => {
        const current = this.chatHistoriesSubject.value;
        this.chatHistoriesSubject.next(current.filter((h) => h.id !== chatHistoryId));
        if (this.activeChatIdSubject.value === chatHistoryId) {
          this.newChat();
        }
      },
    });
  }

  newChat(): void {
    this.activeChatIdSubject.next(null);
    this.chatMessagesSubject.next([]);
    this.activityStatusSubject.next('idle');
  }

  addMessage(msg: ChatMessage): void {
    const current = this.chatMessagesSubject.value;
    this.chatMessagesSubject.next([...current, msg]);
  }
}

import {inject, Injectable, NgZone} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, firstValueFrom} from 'rxjs';
import {skip, take} from 'rxjs/operators';
import {TranslateService} from '@ngx-translate/core';
import {TrainingService} from './training.service';
import {environment} from '../../environments/environment';
import {parseRemainingBuffer, parseSseBuffer} from './sse-parser.util';

export type AgentType =
  | 'TRAINING_CREATION'
  | 'SCHEDULING'
  | 'ANALYSIS'
  | 'COACH_MANAGEMENT'
  | 'CLUB_MANAGEMENT'
  | 'GENERAL';

export interface ActionStep {
  name: string;
  label: string;
  status: 'pending' | 'done' | 'error';
}

export interface PlanTask {
  task: string;
  agentType: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  agentType?: string;
  createdTraining?: { id: string; title: string; sportType: string; estimatedDurationSeconds?: number };
  actions?: ActionStep[];
  isPlan?: boolean;
  planTasks?: PlanTask[];
}

export interface ChatHistoryItem {
  id: string;
  userId: string;
  title: string;
  startedAt: string;
  lastUpdatedAt: string;
  lastAgentType?: string;
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
  private apiUrl = `${environment.apiUrl}/api/ai`;
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);
  private trainingService = inject(TrainingService);
  private translate = inject(TranslateService);

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

  private agentTypeSubject = new BehaviorSubject<AgentType | null>(null);
  agentType$ = this.agentTypeSubject.asObservable();

  private lastAgentTypeSubject = new BehaviorSubject<string | null>(null);
  lastAgentType$ = this.lastAgentTypeSubject.asObservable();

  private emitScheduled = false;
  private pendingEmit = false;
  private streamingCount = 0;

  setAgentType(agentType: AgentType | null): void {
    this.agentTypeSubject.next(agentType);
  }

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

  private readonly MAX_MESSAGE_CHARS = 8_000;

  async sendMessage(message: string): Promise<void> {
    if (message.length > this.MAX_MESSAGE_CHARS) {
      this.addMessage({ role: 'user', content: message, timestamp: new Date() });
      this.addMessage({
        role: 'assistant',
        content: this.translate.instant('AI_CHAT.ERROR_MESSAGE_TOO_LONG', {
          actual: message.length.toLocaleString(),
          max: this.MAX_MESSAGE_CHARS.toLocaleString(),
        }),
        timestamp: new Date(),
      });
      this.ngZone.run(() => this.activityStatusSubject.next('error'));
      return;
    }

    // Snapshot trainings BEFORE the AI runs so we can diff after the response and
    // attach `createdTraining` to the assistant message. We don't rely on a prior
    // page-init load — pull a fresh list here, falling back to whatever is cached.
    const knownIds = new Set<string>();
    try {
      const ts = await firstValueFrom(this.trainingService.loadTrainings());
      ts.forEach((t) => knownIds.add(t.id));
    } catch {
      this.trainingService.trainings$
        .pipe(take(1))
        .subscribe((ts) => ts.forEach((t) => knownIds.add(t.id)));
    }

    this.addMessage({ role: 'user', content: message, timestamp: new Date() });

    let plan: PlanTask[] = [];
    try {
      plan = await this.fetchPlan(message);
    } catch {
      plan = [{ task: message, agentType: 'TRAINING_CREATION' }];
    }

    if (plan.length > 1) {
      const planMsg: ChatMessage = {
        role: 'assistant',
        content: '',
        timestamp: new Date(),
        isPlan: true,
        planTasks: plan,
      };
      this.addMessage(planMsg);
      this.ngZone.run(() => this.activityStatusSubject.next('complete'));
      return;
    }

    return this.streamToAiMessage(message, this.activeChatIdSubject.value, knownIds);
  }

  async executePlan(tasks: PlanTask[]): Promise<void> {
    const convId = this.activeChatIdSubject.value;
    await Promise.all(tasks.map((t) => this.streamToAiMessage(t.task, convId, null)));
  }

  private setStreaming(delta: 1 | -1): void {
    this.streamingCount += delta;
    this.ngZone.run(() => this.streamingSubject.next(this.streamingCount > 0));
  }

  private async fetchPlan(message: string): Promise<PlanTask[]> {
    const jwt = localStorage.getItem('token');
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (jwt) headers['Authorization'] = `Bearer ${jwt}`;

    const response = await fetch(`${this.apiUrl}/plan`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ message }),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  }

  private async streamToAiMessage(
    message: string,
    convId: string | null,
    knownIds: Set<string> | null,
  ): Promise<void> {
    const aiMessage: ChatMessage = { role: 'assistant', content: '', timestamp: new Date(), actions: [] };
    this.addMessage(aiMessage);
    this.setStreaming(1);

    try {
      const jwt = localStorage.getItem('token');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (jwt) headers['Authorization'] = `Bearer ${jwt}`;

      const body: Record<string, string | null> = { message, chatHistoryId: convId };
      const selectedAgent = this.agentTypeSubject.value;
      if (selectedAgent) body['agentType'] = selectedAgent;

      const response = await fetch(`${this.apiUrl}/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);

      const chatHistoryId = response.headers.get('X-Chat-History-Id');
      if (chatHistoryId) {
        this.ngZone.run(() => this.activeChatIdSubject.next(chatHistoryId));
      }

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const { events: sseEvents, remaining } = parseSseBuffer(buffer);
        buffer = remaining;

        for (const { eventType, data } of sseEvents) {
          this.handleSseEvent(eventType, data, aiMessage);
        }
      }

      const remainingEvent = parseRemainingBuffer(buffer);
      if (remainingEvent && remainingEvent.eventType === 'content') {
        aiMessage.content += remainingEvent.data;
        this.emit();
      }

      this.emitImmediate();
      this.loadHistories();

      if (knownIds) {
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
      }
    } catch {
      if (!aiMessage.content) {
        aiMessage.content = this.translate.instant('AI_CHAT.ERROR_CONNECTION');
      }
      this.emit();
    } finally {
      this.setStreaming(-1);
    }
  }

  private handleSseEvent(eventType: string, data: string, aiMessage: ChatMessage): void {
    if (eventType === 'status') {
      const statusValue = data.trim() as ActivityStatus;
      this.ngZone.run(() => this.activityStatusSubject.next(statusValue));
    } else if (eventType === 'content') {
      aiMessage.content += data;
      this.emit();
    } else if (eventType === 'error') {
      aiMessage.content = data.trim();
      this.ngZone.run(() => this.activityStatusSubject.next('error'));
      this.emitImmediate();
    } else if (eventType === 'conversation_id') {
      const conversationId = data.trim();
      if (conversationId) {
        this.ngZone.run(() => this.activeChatIdSubject.next(conversationId));
      }
    } else if (eventType === 'agent') {
      const agentLabel = data.trim();
      aiMessage.agentType = agentLabel;
      this.ngZone.run(() => this.lastAgentTypeSubject.next(agentLabel));
      this.emit();
    } else if (eventType === 'tool_call') {
      try {
        const { name, label } = JSON.parse(data);
        if (!aiMessage.actions) aiMessage.actions = [];
        aiMessage.actions.push({ name, label, status: 'pending' });
        this.emit();
      } catch { /* ignore malformed */ }
    } else if (eventType === 'tool_result') {
      try {
        const { name, label, success } = JSON.parse(data);
        const step = [...(aiMessage.actions ?? [])].reverse().find(
          (a) => a.name === name && a.status === 'pending',
        );
        if (step) {
          step.label = label;
          step.status = success ? 'done' : 'error';
        }
        this.emit();
      } catch { /* ignore malformed */ }
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
    this.lastAgentTypeSubject.next(null);
  }

  addMessage(msg: ChatMessage): void {
    const current = this.chatMessagesSubject.value;
    this.chatMessagesSubject.next([...current, msg]);
  }
}

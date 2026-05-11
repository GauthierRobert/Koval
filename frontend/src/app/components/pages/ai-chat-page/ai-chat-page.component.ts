import {ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, OnInit, ViewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AgentType, ChatService, PlanTask} from '../../../services/chat.service';
import {Training} from '../../../models/training.model';
import {TrainingService} from '../../../services/training.service';
import {WorkoutVisualizationComponent} from '../../shared/workout-visualization/workout-visualization.component';

interface AgentOption {
  label: string;
  value: AgentType | null;
}

@Component({
  selector: 'app-ai-chat-page',
  standalone: true,
  imports: [CommonModule, FormsModule, WorkoutVisualizationComponent, TranslateModule],
  templateUrl: './ai-chat-page.component.html',
  styleUrl: './ai-chat-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AIChatPageComponent implements OnInit {
  @ViewChild('scrollMe') private scrollContainer!: ElementRef;

  chatService = inject(ChatService);
  private trainingService = inject(TrainingService);
  private translate = inject(TranslateService);
  private destroyRef = inject(DestroyRef);
  userInput = '';
  fetchedTrainings: Record<string, Training> = {};
  private nearBottom = true;

  chatSidebarOpen = false;
  selectedAgentIndex = 0;

  agentOptions: AgentOption[] = [
    { label: 'AI_CHAT.AGENT_AUTO', value: null },
    { label: 'AI_CHAT.AGENT_CREATE', value: 'TRAINING_CREATION' },
    { label: 'AI_CHAT.AGENT_SCHEDULE', value: 'SCHEDULING' },
    { label: 'AI_CHAT.AGENT_ANALYSE', value: 'ANALYSIS' },
    { label: 'AI_CHAT.AGENT_COACH', value: 'COACH_MANAGEMENT' },
    { label: 'AI_CHAT.AGENT_CLUB', value: 'CLUB_MANAGEMENT' },
  ];

  ngOnInit(): void {
    this.chatService.loadHistories();

    this.chatService.chatMessages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((messages) => {
        if (this.nearBottom) {
          requestAnimationFrame(() => this.scrollToBottom());
        }
        for (const msg of messages) {
          if (msg.createdTraining?.id && !this.fetchedTrainings[msg.createdTraining.id]) {
            this.trainingService.getTrainingById(msg.createdTraining.id)
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe({
                next: (training) => (this.fetchedTrainings[training.id] = training),
              });
          }
        }
      });
  }

  onScroll(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;
    this.nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 150;
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }

  selectAgent(index: number): void {
    this.selectedAgentIndex = index;
    this.chatService.setAgentType(this.agentOptions[index].value);
  }

  getAgentLabel(agentType: string | undefined): string {
    if (!agentType) return '';
    const keys: Record<string, string> = {
      TRAINING_CREATION: 'AI_CHAT.AGENT_CREATE',
      SCHEDULING: 'AI_CHAT.AGENT_SCHEDULE',
      ANALYSIS: 'AI_CHAT.AGENT_ANALYSE',
      COACH_MANAGEMENT: 'AI_CHAT.AGENT_COACH',
      CLUB_MANAGEMENT: 'AI_CHAT.AGENT_CLUB',
      GENERAL: 'AI_CHAT.AGENT_GENERAL',
    };
    const key = keys[agentType];
    return key ? this.translate.instant(key) : agentType;
  }

  newChat(): void {
    this.chatService.newChat();
  }

  loadConversation(chatHistoryId: string): void {
    this.chatService.loadConversation(chatHistoryId);
    this.nearBottom = true;
  }

  deleteConversation(chatHistoryId: string, event: Event): void {
    event.stopPropagation();
    this.chatService.deleteConversation(chatHistoryId);
  }

  quickSend(text: string): void {
    this.userInput = text;
    this.sendMessage();
  }

  quickSendKey(key: string): void {
    this.quickSend(this.translate.instant(key));
  }

  sendMessage(): void {
    if (!this.userInput.trim()) return;
    const text = this.userInput.trim();
    this.userInput = '';
    this.nearBottom = true;
    this.chatService.sendMessage(text);
  }

  executePlan(tasks: PlanTask[]): void {
    this.nearBottom = true;
    this.chatService.executePlan(tasks);
  }
}

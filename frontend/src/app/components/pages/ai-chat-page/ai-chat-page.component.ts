import {ChangeDetectionStrategy, Component, ElementRef, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
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
  imports: [CommonModule, FormsModule, WorkoutVisualizationComponent],
  templateUrl: './ai-chat-page.component.html',
  styleUrl: './ai-chat-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AIChatPageComponent implements OnInit, OnDestroy {
  @ViewChild('scrollMe') private scrollContainer!: ElementRef;

  chatService = inject(ChatService);
  private trainingService = inject(TrainingService);
  userInput = '';
  fetchedTrainings: Record<string, Training> = {};
  private nearBottom = true;
  private subscription!: Subscription;

  selectedAgentIndex = 0;

  agentOptions: AgentOption[] = [
    { label: 'AUTO', value: null },
    { label: 'CREATE', value: 'TRAINING_CREATION' },
    { label: 'SCHEDULE', value: 'SCHEDULING' },
    { label: 'ANALYSE', value: 'ANALYSIS' },
    { label: 'COACH', value: 'COACH_MANAGEMENT' },
  ];

  ngOnInit(): void {
    this.chatService.loadHistories();

    this.subscription = this.chatService.chatMessages$.subscribe((messages) => {
      if (this.nearBottom) {
        requestAnimationFrame(() => this.scrollToBottom());
      }
      // Fetch full training data for any created trainings not yet loaded
      for (const msg of messages) {
        if (msg.createdTraining?.id && !this.fetchedTrainings[msg.createdTraining.id]) {
          this.trainingService.getTrainingById(msg.createdTraining.id).subscribe({
            next: (training) => this.fetchedTrainings[training.id] = training,
          });
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
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
    const labels: Record<string, string> = {
      TRAINING_CREATION: 'CREATE',
      SCHEDULING: 'SCHEDULE',
      ANALYSIS: 'ANALYSE',
      COACH_MANAGEMENT: 'COACH',
      GENERAL: 'GENERAL',
    };
    return labels[agentType] ?? agentType;
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

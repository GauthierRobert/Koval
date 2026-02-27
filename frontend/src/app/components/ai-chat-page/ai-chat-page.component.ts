import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ChatService} from '../../services/chat.service';
import {Training} from '../../services/training.service';
import {ScheduleModalComponent} from '../schedule-modal/schedule-modal.component';

@Component({
  selector: 'app-ai-chat-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ScheduleModalComponent],
  templateUrl: './ai-chat-page.component.html',
  styleUrl: './ai-chat-page.component.css',
})
export class AIChatPageComponent implements OnInit, OnDestroy {
  @ViewChild('scrollMe') private scrollContainer!: ElementRef;

  chatService = inject(ChatService);
  userInput = '';
  selectedTrainingForSchedule: Training | null = null;
  showScheduleModal = false;
  private nearBottom = true;
  private subscription!: Subscription;

  ngOnInit(): void {
    this.chatService.loadHistories();

    // Auto-scroll when messages change and user is near the bottom
    this.subscription = this.chatService.chatMessages$.subscribe(() => {
      if (this.nearBottom) {
        requestAnimationFrame(() => this.scrollToBottom());
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  onScroll(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;
    // Consider "near bottom" if within 150px of the bottom
    this.nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 150;
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
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

  openScheduleModal(training: { id: string; title: string; sportType: string; estimatedDurationSeconds?: number }): void {
    this.selectedTrainingForSchedule = training as Training;
    this.showScheduleModal = true;
  }

  onScheduleModalClose(): void {
    this.showScheduleModal = false;
    this.selectedTrainingForSchedule = null;
  }
}

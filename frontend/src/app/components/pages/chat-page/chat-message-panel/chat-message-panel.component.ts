import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../../services/auth.service';
import { ChatRoomService } from '../../../../services/chat-room.service';
import { ChatMessage, ChatRoomDetail } from '../../../../models/chat.models';

/**
 * Displays the message history for a single chat room and lets the user post new messages.
 *
 * Consumed by the top-level {@code ChatPageComponent} (which passes the active-room id)
 * and by {@code EmbeddedChatComponent} (which resolves a room from a parent entity scope).
 * When the {@code roomId} input changes, the component opens the room in
 * {@link ChatRoomService} so the shared observables stream the right data.
 */
@Component({
  selector: 'app-chat-message-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-message-panel.component.html',
  styleUrl: './chat-message-panel.component.css',
})
export class ChatMessagePanelComponent implements OnInit, OnChanges, OnDestroy {
  @Input() roomId: string | null = null;
  @Input() showHeader = true;

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLElement>;

  readonly chatRoomService = inject(ChatRoomService);
  private readonly authService = inject(AuthService);

  readonly messages$ = this.chatRoomService.activeRoomMessages$;
  readonly roomDetail$ = this.chatRoomService.activeRoomDetail$;

  draft = '';
  sending = false;
  private subs = new Subscription();
  private lastMessageCount = 0;

  ngOnInit(): void {
    if (this.roomId) this.chatRoomService.openRoom(this.roomId);
    // Auto-scroll to the bottom whenever new messages arrive.
    this.subs.add(
      this.chatRoomService.activeRoomMessages$.subscribe((msgs) => {
        if (msgs.length !== this.lastMessageCount) {
          this.lastMessageCount = msgs.length;
          requestAnimationFrame(() => this.scrollToBottom());
        }
      }),
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['roomId'] && !changes['roomId'].firstChange && this.roomId) {
      this.chatRoomService.openRoom(this.roomId);
    }
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  trackById(_index: number, msg: ChatMessage): string {
    return msg.id;
  }

  isOwnMessage(msg: ChatMessage): boolean {
    return msg.senderId === this.authService.currentUser?.id;
  }

  send(): void {
    const text = this.draft.trim();
    if (!text || !this.roomId || this.sending) return;
    this.sending = true;
    this.chatRoomService.postMessage(this.roomId, text).subscribe({
      next: () => {
        this.draft = '';
        this.sending = false;
      },
      error: () => {
        this.sending = false;
      },
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }
}

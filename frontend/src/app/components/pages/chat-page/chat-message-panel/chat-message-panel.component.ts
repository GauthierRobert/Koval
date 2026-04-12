import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
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
 * Features:
 *  - Smart auto-scroll: only scrolls to bottom when user is already at bottom
 *  - Infinite scroll: loads older messages when user scrolls to the top
 *  - Message grouping: consecutive messages from same sender within 5 min collapse
 *  - Date separators between days
 *  - System message styling
 *  - Delete own messages
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
  private readonly cdr = inject(ChangeDetectorRef);

  readonly messages$ = this.chatRoomService.activeRoomMessages$;
  readonly roomDetail$ = this.chatRoomService.activeRoomDetail$;

  draft = '';
  sending = false;
  loadingOlder = false;
  private subs = new Subscription();
  private lastMessageCount = 0;
  private nearBottom = true;

  /** Threshold in pixels to consider "at bottom" for auto-scroll. */
  private static readonly SCROLL_BOTTOM_THRESHOLD = 80;
  /** Threshold in pixels from top to trigger loading older messages. */
  private static readonly SCROLL_TOP_THRESHOLD = 60;
  /** Max time gap (ms) to group consecutive messages from the same sender. */
  private static readonly GROUP_GAP_MS = 5 * 60 * 1000;

  ngOnInit(): void {
    if (this.roomId) this.chatRoomService.openRoom(this.roomId);
    this.subs.add(
      this.chatRoomService.activeRoomMessages$.subscribe((msgs) => {
        if (msgs.length > this.lastMessageCount && this.nearBottom) {
          requestAnimationFrame(() => this.scrollToBottom());
        }
        this.lastMessageCount = msgs.length;
        this.cdr.markForCheck();
      }),
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['roomId'] && !changes['roomId'].firstChange && this.roomId) {
      this.nearBottom = true;
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

  /** Should we show the full header (avatar + name) or collapse into the previous bubble? */
  isGroupStart(msg: ChatMessage, index: number, messages: ChatMessage[]): boolean {
    if (msg.type === 'SYSTEM') return false;
    if (index === 0) return true;
    const prev = messages[index - 1];
    if (prev.type === 'SYSTEM') return true;
    if (prev.senderId !== msg.senderId) return true;
    const gap = new Date(msg.createdAt).getTime() - new Date(prev.createdAt).getTime();
    return gap > ChatMessagePanelComponent.GROUP_GAP_MS;
  }

  /** Show a date separator line above this message if it's a new calendar day. */
  isNewDay(msg: ChatMessage, index: number, messages: ChatMessage[]): boolean {
    if (index === 0) return true;
    const prev = messages[index - 1];
    const d1 = new Date(prev.createdAt).toDateString();
    const d2 = new Date(msg.createdAt).toDateString();
    return d1 !== d2;
  }

  formatDate(isoDate: string): string {
    const d = new Date(isoDate);
    const today = new Date();
    if (d.toDateString() === today.toDateString()) return 'Today';
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
    return d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
  }

  send(): void {
    const text = this.draft.trim();
    if (!text || !this.roomId || this.sending) return;
    this.sending = true;
    this.chatRoomService.postMessage(this.roomId, text).subscribe({
      next: () => {
        this.draft = '';
        this.sending = false;
        this.nearBottom = true;
        requestAnimationFrame(() => this.scrollToBottom());
        this.cdr.markForCheck();
      },
      error: () => {
        this.sending = false;
        this.cdr.markForCheck();
      },
    });
  }

  deleteMessage(messageId: string): void {
    this.chatRoomService.deleteMessage(messageId).subscribe();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  onScroll(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;

    // Track whether user is near the bottom for smart auto-scroll.
    this.nearBottom =
      el.scrollHeight - el.scrollTop - el.clientHeight < ChatMessagePanelComponent.SCROLL_BOTTOM_THRESHOLD;

    // Infinite scroll: load older messages when near the top.
    if (el.scrollTop < ChatMessagePanelComponent.SCROLL_TOP_THRESHOLD && !this.loadingOlder && this.roomId) {
      const messages = this.chatRoomService.activeRoomMessagesSnapshot;
      if (messages.length > 0) {
        const oldest = messages[0];
        this.loadingOlder = true;
        const prevHeight = el.scrollHeight;
        this.chatRoomService.loadOlderMessages(this.roomId, oldest.createdAt).subscribe({
          next: () => {
            // Preserve scroll position after prepending older messages.
            requestAnimationFrame(() => {
              const newHeight = el.scrollHeight;
              el.scrollTop = newHeight - prevHeight;
            });
            this.loadingOlder = false;
            this.cdr.markForCheck();
          },
          error: () => {
            this.loadingOlder = false;
            this.cdr.markForCheck();
          },
        });
      }
    }
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }
}

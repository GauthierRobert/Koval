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
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../services/auth.service';
import { ChatSseService } from '../../../services/chat-sse.service';
import { ChatMessage, ChatRoomDetail, ChatRoomScope, PostMessageRequest } from '../../../models/chat.models';

/**
 * Self-contained embedded chat that does NOT share state with the global
 * {@link ChatRoomService}. This prevents state pollution when multiple
 * embedded chats or the main chat page are mounted simultaneously.
 *
 * Inputs:
 *  - scope: CLUB, GROUP, etc.
 *  - clubId: the owning club
 *  - refId: optional parent entity id (omit for CLUB scope)
 *  - showHeader: whether to display the room title bar
 */
@Component({
  selector: 'app-embedded-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './embedded-chat.component.html',
  styleUrl: './embedded-chat.component.css',
})
export class EmbeddedChatComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) scope!: ChatRoomScope;
  @Input({ required: true }) clubId!: string;
  @Input() refId?: string;
  @Input() showHeader = false;

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLElement>;

  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly sse = inject(ChatSseService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly apiUrl = `${environment.apiUrl}/api/chat`;

  roomDetail: ChatRoomDetail | null = null;
  messages: ChatMessage[] = [];
  loading = true;
  draft = '';
  sending = false;
  loadingOlder = false;
  private roomId: string | null = null;
  private nearBottom = true;
  private subs = new Subscription();

  private static readonly SCROLL_BOTTOM_THRESHOLD = 80;
  private static readonly SCROLL_TOP_THRESHOLD = 60;
  private static readonly GROUP_GAP_MS = 5 * 60 * 1000;

  ngOnInit(): void {
    this.resolveRoom();
    // Listen to SSE messages for this specific room only.
    this.subs.add(
      this.sse.onChatMessage$.subscribe((msg) => {
        if (msg.roomId === this.roomId) {
          this.messages = [...this.messages, msg];
          if (this.nearBottom) {
            requestAnimationFrame(() => this.scrollToBottom());
          }
          this.cdr.markForCheck();
        }
      }),
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['scope'] || changes['clubId'] || changes['refId']) && this.scope && this.clubId) {
      this.resolveRoom();
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

  isGroupStart(msg: ChatMessage, index: number): boolean {
    if (msg.type === 'SYSTEM') return false;
    if (index === 0) return true;
    const prev = this.messages[index - 1];
    if (prev.type === 'SYSTEM') return true;
    if (prev.senderId !== msg.senderId) return true;
    return new Date(msg.createdAt).getTime() - new Date(prev.createdAt).getTime() > EmbeddedChatComponent.GROUP_GAP_MS;
  }

  isNewDay(msg: ChatMessage, index: number): boolean {
    if (index === 0) return true;
    const prev = this.messages[index - 1];
    return new Date(prev.createdAt).toDateString() !== new Date(msg.createdAt).toDateString();
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
    const body: PostMessageRequest = { content: text, clientNonce: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}` };
    this.http.post<ChatMessage>(`${this.apiUrl}/rooms/${this.roomId}/messages`, body).subscribe({
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
    this.http.delete<void>(`${this.apiUrl}/messages/${messageId}`).subscribe({
      next: () => {
        this.messages = this.messages.map((m) =>
          m.id === messageId ? { ...m, deleted: true, content: '' } : m,
        );
        this.cdr.markForCheck();
      },
    });
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
    this.nearBottom =
      el.scrollHeight - el.scrollTop - el.clientHeight < EmbeddedChatComponent.SCROLL_BOTTOM_THRESHOLD;
    if (el.scrollTop < EmbeddedChatComponent.SCROLL_TOP_THRESHOLD && !this.loadingOlder && this.roomId && this.messages.length > 0) {
      this.loadingOlder = true;
      const oldest = this.messages[0];
      const prevHeight = el.scrollHeight;
      const url = `${this.apiUrl}/rooms/${this.roomId}/messages?before=${encodeURIComponent(oldest.createdAt)}`;
      this.http.get<ChatMessage[]>(url).subscribe({
        next: (older) => {
          this.messages = [...older, ...this.messages];
          requestAnimationFrame(() => {
            el.scrollTop = el.scrollHeight - prevHeight;
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

  private resolveRoom(): void {
    this.loading = true;
    this.roomId = null;
    this.messages = [];
    this.roomDetail = null;
    let url = `${this.apiUrl}/rooms/by-parent?scope=${this.scope}&clubId=${encodeURIComponent(this.clubId)}`;
    if (this.refId) url += `&refId=${encodeURIComponent(this.refId)}`;
    this.http.get<ChatRoomDetail>(url).subscribe({
      next: (detail) => {
        this.roomId = detail.id;
        this.roomDetail = detail;
        this.loading = false;
        this.cdr.markForCheck();
        this.loadMessages();
      },
      error: () => {
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  private loadMessages(): void {
    if (!this.roomId) return;
    this.http.get<ChatMessage[]>(`${this.apiUrl}/rooms/${this.roomId}/messages`).subscribe({
      next: (msgs) => {
        this.messages = msgs;
        this.cdr.markForCheck();
        requestAnimationFrame(() => this.scrollToBottom());
      },
    });
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }
}

import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { ChatApiService } from '../../../services/chat-api.service';
import { ChatSseService } from '../../../services/chat-sse.service';
import { ChatMessage, ChatRoomDetail, ChatRoomScope } from '../../../models/chat.models';
import { ChatMessageListComponent } from '../chat-message-list/chat-message-list.component';

/**
 * Self-contained embedded chat with **isolated** state (no singleton dependency).
 * Resolves the chat room from a parent entity scope, then delegates all rendering
 * to the shared {@link ChatMessageListComponent}.
 */
@Component({
  selector: 'app-embedded-chat',
  standalone: true,
  imports: [CommonModule, ChatMessageListComponent],
  template: `
    @if (loading) {
      <div class="embedded-loading">Loading chat…</div>
    } @else if (!roomDetail) {
      <div class="embedded-loading">Chat is not available yet.</div>
    } @else {
      <app-chat-message-list
        #messageList
        [messages]="messages"
        [roomDetail]="roomDetail"
        [showHeader]="showHeader"
        [loadingOlder]="loadingOlder"
        [sending]="sending"
        [currentUserId]="authService.currentUser?.id ?? null"
        (sendMessage)="onSend($event)"
        (deleteMsg)="onDelete($event)"
        (scrolledNearTop)="onLoadOlder()"
        (joinRoom)="api.joinRoom(roomDetail!.id).subscribe()"
        (leaveRoom)="api.leaveRoom(roomDetail!.id).subscribe()"
        (toggleMute)="api.setMuted(roomDetail!.id, !roomDetail!.currentUserMuted).subscribe()">
      </app-chat-message-list>
    }
  `,
  styles: [`
    :host { display: block; height: 100%; min-height: 480px; }
    .embedded-loading { padding: 24px; color: var(--text-muted, rgba(255, 255, 255, 0.5)); text-align: center; font-size: 0.9rem; margin: auto; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmbeddedChatComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) scope!: ChatRoomScope;
  @Input({ required: true }) clubId!: string;
  @Input() refId?: string;
  @Input() title?: string;
  @Input() showHeader = false;

  @ViewChild('messageList') private messageList?: ChatMessageListComponent;

  readonly api = inject(ChatApiService);
  readonly authService = inject(AuthService);
  private readonly sse = inject(ChatSseService);
  private readonly cdr = inject(ChangeDetectorRef);

  roomDetail: ChatRoomDetail | null = null;
  messages: ChatMessage[] = [];
  loading = true;
  sending = false;
  loadingOlder = false;
  private roomId: string | null = null;
  private subs = new Subscription();

  ngOnInit(): void {
    this.resolveRoom();
    this.subs.add(
      this.sse.onChatMessage$.subscribe((msg) => {
        if (msg.roomId !== this.roomId) return;
        if (this.messages.some((m) => m.id === msg.id)) return;
        this.messages = [...this.messages, msg];
        this.messageList?.scrollToBottomIfNeeded();
        this.cdr.markForCheck();
      }),
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['scope'] || changes['clubId'] || changes['refId'] || changes['title']) && this.scope && this.clubId) {
      this.resolveRoom();
    }
  }

  ngOnDestroy(): void { this.subs.unsubscribe(); }

  onSend(text: string): void {
    if (!this.roomId) return;
    this.sending = true;
    this.api.postMessage(this.roomId, text).subscribe({
      next: (msg) => {
        if (!this.messages.some((m) => m.id === msg.id)) {
          this.messages = [...this.messages, msg];
          this.messageList?.scrollToBottomIfNeeded();
        }
        this.sending = false;
        this.cdr.markForCheck();
      },
      error: () => { this.sending = false; this.cdr.markForCheck(); },
    });
  }

  onDelete(messageId: string): void {
    this.api.deleteMessage(messageId).subscribe({
      next: () => {
        this.messages = this.messages.map((m) => m.id === messageId ? { ...m, deleted: true, content: '' } : m);
        this.cdr.markForCheck();
      },
    });
  }

  onLoadOlder(): void {
    if (this.loadingOlder || !this.roomId || this.messages.length === 0) return;
    this.loadingOlder = true;
    const prevHeight = this.messageList?.scrollHeight ?? 0;
    this.api.getMessages(this.roomId, this.messages[0].createdAt).subscribe({
      next: (older) => {
        this.messages = [...older, ...this.messages];
        this.messageList?.preserveScrollAfterPrepend(prevHeight);
        this.loadingOlder = false;
        this.cdr.markForCheck();
      },
      error: () => { this.loadingOlder = false; this.cdr.markForCheck(); },
    });
  }

  private resolveRoom(): void {
    this.loading = true;
    this.roomId = null;
    this.messages = [];
    this.roomDetail = null;
    this.api.findByParent(this.scope, this.clubId, this.refId, this.title).subscribe({
      next: (detail) => {
        this.roomId = detail.id;
        this.roomDetail = detail;
        this.loading = false;
        this.cdr.markForCheck();
        this.api.getMessages(detail.id).subscribe({
          next: (msgs) => {
            this.messages = msgs;
            this.cdr.markForCheck();
            requestAnimationFrame(() => this.messageList?.scrollToBottomIfNeeded());
          },
        });
      },
      error: () => { this.loading = false; this.cdr.markForCheck(); },
    });
  }
}

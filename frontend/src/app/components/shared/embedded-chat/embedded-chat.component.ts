import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin, Subscription } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { ChatApiService } from '../../../services/chat-api.service';
import { ChatRoomCacheService } from '../../../services/chat-room-cache.service';
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
        [clubName]="clubName"
        [clubLogoUrl]="clubLogoUrl"
        [scope]="scope"
        [showBackButton]="showBackButton"
        (sendMessage)="onSend($event)"
        (deleteMsg)="onDelete($event)"
        (scrolledNearTop)="onLoadOlder()"
        (joinRoom)="api.joinRoom(roomDetail!.id).subscribe()"
        (leaveRoom)="api.leaveRoom(roomDetail!.id).subscribe()"
        (toggleMute)="api.setMuted(roomDetail!.id, !roomDetail!.currentUserMuted).subscribe()"
        (backClick)="backClick.emit()">
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
  @Input() clubName?: string | null;
  @Input() clubLogoUrl?: string | null;
  @Input() showBackButton = false;

  @Output() backClick = new EventEmitter<void>();

  @ViewChild('messageList') private messageList?: ChatMessageListComponent;

  readonly api = inject(ChatApiService);
  readonly authService = inject(AuthService);
  private readonly sse = inject(ChatSseService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly cache = inject(ChatRoomCacheService);

  roomDetail: ChatRoomDetail | null = null;
  messages: ChatMessage[] = [];
  loading = true;
  sending = false;
  loadingOlder = false;
  private hasMoreOlder = true;
  private roomId: string | null = null;
  private cacheKey: string | null = null;
  private subs = new Subscription();

  ngOnInit(): void {
    this.resolveRoom();
    this.subs.add(
      this.sse.onChatMessage$.subscribe((msg) => {
        this.handleIncomingMessage(msg);
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
    const key = this.cacheKey;
    this.api.postMessage(this.roomId, text).subscribe({
      next: (msg) => {
        if (!this.messages.some((m) => m.id === msg.id)) {
          this.messages = [...this.messages, msg];
          if (key) this.cache.appendMessage(key, msg);
          this.messageList?.scrollToBottomIfNeeded();
        }
        this.sending = false;
        this.cdr.markForCheck();
      },
      error: () => { this.sending = false; this.cdr.markForCheck(); },
    });
  }

  onDelete(messageId: string): void {
    const key = this.cacheKey;
    this.api.deleteMessage(messageId).subscribe({
      next: () => {
        this.messages = this.messages.map((m) => m.id === messageId ? { ...m, deleted: true, content: '' } : m);
        if (key) this.cache.updateMessage(key, messageId, { deleted: true, content: '' });
        this.cdr.markForCheck();
      },
    });
  }

  onLoadOlder(): void {
    if (this.loadingOlder || !this.hasMoreOlder || !this.roomId || this.messages.length === 0) return;
    this.loadingOlder = true;
    this.cdr.markForCheck();
    const prevDistFromBottom = this.messageList?.distanceFromBottom ?? 0;
    const key = this.cacheKey;
    this.api.getMessages(this.roomId, this.messages[0].createdAt).subscribe({
      next: (older) => {
        if (older.length === 0) {
          this.hasMoreOlder = false;
          this.loadingOlder = false;
          this.cdr.markForCheck();
          return;
        }
        this.messages = [...older, ...this.messages];
        if (key) this.cache.prependMessages(key, older);
        this.cdr.markForCheck();
        this.messageList?.preserveScrollAfterPrepend(prevDistFromBottom, () => {
          this.loadingOlder = false;
          this.cdr.markForCheck();
        });
      },
      error: () => { this.loadingOlder = false; this.cdr.markForCheck(); },
    });
  }

  private handleIncomingMessage(msg: ChatMessage): void {
    if (msg.roomId !== this.roomId) return;
    if (this.messages.some((m) => m.id === msg.id)) return;
    this.messages = [...this.messages, msg];
    if (this.cacheKey) this.cache.appendMessage(this.cacheKey, msg);
    this.messageList?.scrollToBottomIfNeeded();
    this.cdr.markForCheck();
  }

  private resolveRoom(): void {
    const key = ChatRoomCacheService.keyFor(this.scope, this.clubId, this.refId, this.title);
    this.cacheKey = key;
    this.hasMoreOlder = true;

    const cached = this.cache.get(key);
    if (cached) {
      this.roomId = cached.detail.id;
      this.roomDetail = cached.detail;
      this.messages = cached.messages;
      this.loading = false;
      this.cdr.markForCheck();
      requestAnimationFrame(() => this.messageList?.scrollToBottomIfNeeded());
      return;
    }

    this.loading = true;
    this.roomId = null;
    this.messages = [];
    this.roomDetail = null;

    // Fast path: if we previously resolved this scope to a roomId, skip the expensive
    // by-parent endpoint and fetch detail + messages in parallel. Falls back to
    // by-parent on any error (stale id, room deleted, lost membership, …).
    const persistedRoomId = this.cache.getPersistedRoomId(key);
    if (persistedRoomId) {
      forkJoin({
        detail: this.api.getRoom(persistedRoomId),
        messages: this.api.getMessages(persistedRoomId),
      }).subscribe({
        next: ({ detail, messages }) => {
          if (this.cacheKey !== key) return;
          this.roomId = detail.id;
          this.roomDetail = detail;
          this.messages = messages;
          this.loading = false;
          this.cache.set(key, { detail, messages });
          this.cdr.markForCheck();
          requestAnimationFrame(() => this.messageList?.scrollToBottomIfNeeded());
        },
        error: () => {
          if (this.cacheKey !== key) return;
          this.cache.clearPersistedRoomId(key);
          this.fetchByParent(key);
        },
      });
      return;
    }

    this.fetchByParent(key);
  }

  private fetchByParent(key: string): void {
    this.api.findByParent(this.scope, this.clubId, this.refId, this.title).subscribe({
      next: (detail) => {
        if (this.cacheKey !== key) return;
        this.roomId = detail.id;
        this.roomDetail = detail;
        this.loading = false;
        this.cache.setDetail(key, detail);
        this.cdr.markForCheck();
        this.api.getMessages(detail.id).subscribe({
          next: (msgs) => {
            if (this.cacheKey !== key) return;
            this.messages = msgs;
            this.cache.set(key, { detail, messages: msgs });
            this.cdr.markForCheck();
            requestAnimationFrame(() => this.messageList?.scrollToBottomIfNeeded());
          },
        });
      },
      error: () => {
        if (this.cacheKey !== key) return;
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }
}

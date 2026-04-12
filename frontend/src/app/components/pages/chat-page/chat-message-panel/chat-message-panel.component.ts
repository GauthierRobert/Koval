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
import { AuthService } from '../../../../services/auth.service';
import { ChatRoomService } from '../../../../services/chat-room.service';
import { ChatMessageListComponent } from '../../../shared/chat-message-list/chat-message-list.component';

/**
 * Thin wrapper that bridges the global {@link ChatRoomService} state
 * to the shared {@link ChatMessageListComponent} presentational component.
 *
 * Used by the top-level {@code /messages/:roomId} page.
 */
@Component({
  selector: 'app-chat-message-panel',
  standalone: true,
  imports: [CommonModule, ChatMessageListComponent],
  template: `
    <app-chat-message-list
      #messageList
      [messages]="(chatRoomService.activeRoomMessages$ | async) ?? []"
      [roomDetail]="(chatRoomService.activeRoomDetail$ | async) ?? null"
      [showHeader]="showHeader"
      [loadingOlder]="loadingOlder"
      [sending]="sending"
      [currentUserId]="authService.currentUser?.id ?? null"
      (sendMessage)="onSend($event)"
      (deleteMsg)="onDelete($event)"
      (scrolledNearTop)="onLoadOlder()"
      (joinRoom)="chatRoomService.joinRoom(roomId!).subscribe()"
      (leaveRoom)="chatRoomService.leaveRoom(roomId!).subscribe()"
      (toggleMute)="onToggleMute()">
    </app-chat-message-list>
  `,
  styles: [':host { display: flex; flex-direction: column; height: 100%; min-height: 0; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatMessagePanelComponent implements OnInit, OnChanges, OnDestroy {
  @Input() roomId: string | null = null;
  @Input() showHeader = true;

  @ViewChild('messageList') private messageList?: ChatMessageListComponent;

  readonly chatRoomService = inject(ChatRoomService);
  readonly authService = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  sending = false;
  loadingOlder = false;
  private subs = new Subscription();

  ngOnInit(): void {
    if (this.roomId) this.chatRoomService.openRoom(this.roomId);
    this.subs.add(
      this.chatRoomService.activeRoomMessages$.subscribe(() => {
        this.messageList?.scrollToBottomIfNeeded();
        this.cdr.markForCheck();
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

  onSend(text: string): void {
    if (!this.roomId) return;
    this.sending = true;
    this.chatRoomService.postMessage(this.roomId, text).subscribe({
      next: () => { this.sending = false; this.cdr.markForCheck(); },
      error: () => { this.sending = false; this.cdr.markForCheck(); },
    });
  }

  onDelete(messageId: string): void {
    this.chatRoomService.deleteMessage(messageId).subscribe();
  }

  onLoadOlder(): void {
    if (this.loadingOlder || !this.roomId) return;
    const msgs = this.chatRoomService.activeRoomMessagesSnapshot;
    if (msgs.length === 0) return;
    this.loadingOlder = true;
    const prevHeight = this.messageList?.scrollHeight ?? 0;
    this.chatRoomService.loadOlderMessages(this.roomId, msgs[0].createdAt).subscribe({
      next: () => {
        this.messageList?.preserveScrollAfterPrepend(prevHeight);
        this.loadingOlder = false;
        this.cdr.markForCheck();
      },
      error: () => { this.loadingOlder = false; this.cdr.markForCheck(); },
    });
  }

  onToggleMute(): void {
    const detail = this.chatRoomService.activeRoomDetailSnapshot;
    if (detail) this.chatRoomService.setMuted(detail.id, !detail.currentUserMuted).subscribe();
  }
}

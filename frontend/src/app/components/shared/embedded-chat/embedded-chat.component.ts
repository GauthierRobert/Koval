import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject } from 'rxjs';
import { ChatRoomService } from '../../../services/chat-room.service';
import { ChatRoomScope } from '../../../models/chat.models';
import { ChatMessagePanelComponent } from '../../pages/chat-page/chat-message-panel/chat-message-panel.component';

/**
 * Drops a chat message panel into any container by resolving a room from its parent entity.
 * Used by the club detail "Chat" tab, and future session/group detail pages.
 *
 * Inputs:
 *   - scope: which parent type (CLUB, GROUP, SINGLE_SESSION, etc.)
 *   - clubId: required — the owning club
 *   - refId: optional — the parent entity id (omit for CLUB scope)
 */
@Component({
  selector: 'app-embedded-chat',
  standalone: true,
  imports: [CommonModule, ChatMessagePanelComponent],
  template: `
    @if (roomId$ | async; as roomId) {
      <app-chat-message-panel [roomId]="roomId" [showHeader]="showHeader"></app-chat-message-panel>
    } @else if (loading) {
      <div class="embedded-chat-loading">Loading chat…</div>
    } @else {
      <div class="embedded-chat-empty">Chat is not available for this item yet.</div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        height: 100%;
        min-height: 480px;
      }
      .embedded-chat-loading,
      .embedded-chat-empty {
        padding: 24px;
        color: var(--text-muted, rgba(255, 255, 255, 0.5));
        text-align: center;
        font-size: 0.9rem;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmbeddedChatComponent implements OnInit, OnChanges {
  @Input({ required: true }) scope!: ChatRoomScope;
  @Input({ required: true }) clubId!: string;
  @Input() refId?: string;
  @Input() showHeader = false;

  private readonly chatRoomService = inject(ChatRoomService);

  readonly roomIdSubject = new BehaviorSubject<string | null>(null);
  readonly roomId$ = this.roomIdSubject.asObservable();
  loading = false;

  ngOnInit(): void {
    this.resolveRoom();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['scope'] || changes['clubId'] || changes['refId']) && this.scope && this.clubId) {
      this.resolveRoom();
    }
  }

  private resolveRoom(): void {
    this.loading = true;
    this.roomIdSubject.next(null);
    this.chatRoomService.findRoomByParent(this.scope, this.clubId, this.refId).subscribe({
      next: (detail) => {
        this.roomIdSubject.next(detail.id);
        this.loading = false;
      },
      error: () => {
        this.roomIdSubject.next(null);
        this.loading = false;
      },
    });
  }
}

import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatMessage, ChatRoomDetail, ChatRoomScope } from '../../../models/chat.models';
import { isGroupStart, isNewDay, formatChatDate } from '../../../utils/chat-message.utils';

/**
 * Pure presentational component that renders a chat message list with composer.
 *
 * All data flows in via @Input; all user actions flow out via @Output.
 * Smart auto-scroll and infinite-scroll detection are handled internally.
 */
@Component({
  selector: 'app-chat-message-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-message-list.component.html',
  styleUrl: './chat-message-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatMessageListComponent {
  @Input() messages: ChatMessage[] = [];
  @Input() roomDetail: ChatRoomDetail | null = null;
  @Input() showHeader = true;
  @Input() loadingOlder = false;
  @Input() sending = false;
  @Input() currentUserId: string | null = null;
  @Input() clubName?: string | null;
  @Input() clubLogoUrl?: string | null;
  @Input() scope?: ChatRoomScope;
  @Input() showBackButton = false;

  @Output() sendMessage = new EventEmitter<string>();
  @Output() deleteMsg = new EventEmitter<string>();
  @Output() scrolledNearTop = new EventEmitter<void>();
  @Output() joinRoom = new EventEmitter<void>();
  @Output() leaveRoom = new EventEmitter<void>();
  @Output() toggleMute = new EventEmitter<void>();
  @Output() backClick = new EventEmitter<void>();

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLElement>;

  private readonly cdr = inject(ChangeDetectorRef);

  draft = '';
  menuOpen = false;
  private nearBottom = true;

  private static readonly SCROLL_BOTTOM_THRESHOLD = 80;
  private static readonly SCROLL_TOP_THRESHOLD = 60;

  // --- Exposed to template ---

  readonly isGroupStart = isGroupStart;
  readonly isNewDay = isNewDay;
  readonly formatChatDate = formatChatDate;

  trackById(_index: number, msg: ChatMessage): string {
    return msg.id;
  }

  isOwn(msg: ChatMessage): boolean {
    return msg.senderId === this.currentUserId;
  }

  onSend(): void {
    const text = this.draft.trim();
    if (!text || this.sending) return;
    this.sendMessage.emit(text);
    this.draft = '';
    this.nearBottom = true;
    requestAnimationFrame(() => this.scrollToBottom());
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.onSend();
    }
  }

  onScroll(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;
    this.nearBottom =
      el.scrollHeight - el.scrollTop - el.clientHeight < ChatMessageListComponent.SCROLL_BOTTOM_THRESHOLD;
    if (el.scrollTop < ChatMessageListComponent.SCROLL_TOP_THRESHOLD && !this.loadingOlder && this.messages.length > 0) {
      this.scrolledNearTop.emit();
    }
  }

  /** Called by parent after messages change to auto-scroll if user was at bottom. */
  scrollToBottomIfNeeded(): void {
    if (this.nearBottom) requestAnimationFrame(() => this.scrollToBottom());
  }

  /** Preserve scroll position after older messages are prepended. */
  preserveScrollAfterPrepend(prevHeight: number): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) requestAnimationFrame(() => { el.scrollTop = el.scrollHeight - prevHeight; });
  }

  get scrollHeight(): number {
    return this.scrollContainer?.nativeElement?.scrollHeight ?? 0;
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }

  // --- Mobile header: scope icon + overflow menu ---

  get scopeIconName(): 'club' | 'group' | 'objective' {
    if (this.scope === 'GROUP') return 'group';
    if (this.scope === 'OBJECTIVE') return 'objective';
    return 'club';
  }

  get clubInitial(): string {
    return (this.clubName ?? '').trim().charAt(0).toUpperCase();
  }

  toggleMenu(event: Event): void {
    event.stopPropagation();
    this.menuOpen = !this.menuOpen;
    this.cdr.markForCheck();
  }

  closeMenu(): void {
    if (!this.menuOpen) return;
    this.menuOpen = false;
    this.cdr.markForCheck();
  }

  runMenuAction(action: 'join' | 'mute' | 'leave'): void {
    this.menuOpen = false;
    if (action === 'join') this.joinRoom.emit();
    else if (action === 'mute') this.toggleMute.emit();
    else this.leaveRoom.emit();
    this.cdr.markForCheck();
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.closeMenu();
  }
}

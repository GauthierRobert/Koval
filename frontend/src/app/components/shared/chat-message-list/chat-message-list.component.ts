import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  HostListener,
  Input,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScrollingModule, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { ScrollingModule as ExperimentalScrollingModule } from '@angular/cdk-experimental/scrolling';
import { ChatMessage, ChatRoomDetail, ChatRoomScope } from '../../../models/chat.models';
import { isGroupStart, isNewDay, formatChatDate } from '../../../utils/chat-message.utils';

/**
 * Pure presentational component that renders a chat message list with composer.
 *
 * All data flows in via @Input; all user actions flow out via @Output.
 * The message list is virtualized via the CDK autosize strategy so the DOM
 * cost stays bounded for long histories. Smart auto-scroll and infinite-scroll
 * detection are handled internally against the virtual viewport.
 */
@Component({
  selector: 'app-chat-message-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ScrollingModule, ExperimentalScrollingModule],
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

  @ViewChild('viewport') private viewport?: CdkVirtualScrollViewport;

  private readonly cdr = inject(ChangeDetectorRef);

  draft = '';
  menuOpen = false;
  private nearBottom = true;
  private prevScrollTop = 0;

  private static readonly SCROLL_BOTTOM_THRESHOLD = 80;
  private static readonly SCROLL_TOP_THRESHOLD = 60;

  // --- Exposed to template ---

  readonly isGroupStart = isGroupStart;
  readonly isNewDay = isNewDay;
  readonly formatChatDate = formatChatDate;

  trackById = (_index: number, msg: ChatMessage): string => msg.id;

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
    const viewport = this.viewport;
    if (!viewport) return;
    const distanceFromBottom = viewport.measureScrollOffset('bottom');
    const distanceFromTop = viewport.measureScrollOffset('top');
    this.nearBottom = distanceFromBottom < ChatMessageListComponent.SCROLL_BOTTOM_THRESHOLD;
    const scrolledUp = distanceFromTop < this.prevScrollTop;
    this.prevScrollTop = distanceFromTop;
    if (
      scrolledUp &&
      distanceFromTop < ChatMessageListComponent.SCROLL_TOP_THRESHOLD &&
      !this.loadingOlder &&
      this.messages.length > 0
    ) {
      this.scrolledNearTop.emit();
    }
  }

  /** Called by parent after messages change to auto-scroll if user was at bottom. */
  scrollToBottomIfNeeded(): void {
    if (this.nearBottom) requestAnimationFrame(() => this.scrollToBottom());
  }

  /**
   * Preserve scroll position after older messages are prepended.
   * Anchors to distance-from-bottom (which doesn't change on prepend).
   * Calls onComplete after the scroll has been applied so callers can
   * safely re-enable load-older without racing the scroll event.
   */
  preserveScrollAfterPrepend(prevDistanceFromBottom: number, onComplete?: () => void): void {
    const viewport = this.viewport;
    if (!viewport) {
      onComplete?.();
      return;
    }
    requestAnimationFrame(() => {
      viewport.checkViewportSize();
      requestAnimationFrame(() => {
        const el = viewport.elementRef.nativeElement;
        const target = Math.max(0, el.scrollHeight - el.clientHeight - prevDistanceFromBottom);
        viewport.scrollToOffset(target);
        this.prevScrollTop = target;
        requestAnimationFrame(() => onComplete?.());
      });
    });
  }

  get distanceFromBottom(): number {
    return this.viewport?.measureScrollOffset('bottom') ?? 0;
  }

  private scrollToBottom(): void {
    const viewport = this.viewport;
    if (!viewport) return;
    viewport.scrollTo({ bottom: 0 });
    requestAnimationFrame(() => {
      this.prevScrollTop = viewport.measureScrollOffset('top');
    });
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

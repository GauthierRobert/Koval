import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {FeedCommentEntry, ReactionEmoji} from '../../../../../../../services/club.service';
import {KovalMentionInputComponent} from '../../../../../../shared/koval-mention-input/koval-mention-input.component';
import {KovalMentionTextComponent} from '../../../../../../shared/koval-mention-text/koval-mention-text.component';
import {FeedReactionBarComponent} from '../../../../../../shared/feed-reaction-bar/feed-reaction-bar.component';

interface CommentReplyEvent {
  eventId: string;
  parentCommentId: string;
  content: string;
  mentionUserIds: string[];
}

interface CommentReactionEvent {
  eventId: string;
  commentId: string;
  emoji: ReactionEmoji;
}

@Component({
  selector: 'app-feed-comments-section',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    KovalMentionInputComponent,
    KovalMentionTextComponent,
    FeedReactionBarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="comments-section">
      <button data-testid="comments-toggle" class="comments-toggle" (click)="toggleExpanded($event)">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        @if (commentCount === 1) {
          {{ 'CLUB_FEED.COMMENT_COUNT_ONE' | translate }}
        } @else {
          {{ 'CLUB_FEED.COMMENT_COUNT' | translate: {count: commentCount} }}
        }
        <svg class="expand-chevron" [class.rotated]="expanded" width="12" height="12" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </button>

      @if (expanded) {
        <div class="comments-list">
          @for (parent of topLevelComments; track parent.id) {
            <div class="comment-thread">
              <ng-container [ngTemplateOutlet]="commentTpl"
                            [ngTemplateOutletContext]="{c: parent, isReply: false}"></ng-container>

              @for (reply of repliesOf(parent.id); track reply.id) {
                <div class="reply-row">
                  <ng-container [ngTemplateOutlet]="commentTpl"
                                [ngTemplateOutletContext]="{c: reply, isReply: true}"></ng-container>
                </div>
              }

              @if (replyOpenFor === parent.id) {
                <div class="reply-input-row" data-testid="reply-input-row">
                  <app-koval-mention-input
                    [clubId]="clubId"
                    [placeholder]="'CLUB_FEED.REPLY_PLACEHOLDER' | translate"
                    [value]="replyText"
                    [resetSignal]="replyResetTick"
                    testId="reply-input"
                    (textChange)="replyText = $event"
                    (mentionsChange)="replyMentionIds = $event"
                    (submitted)="submitReply(parent)">
                  </app-koval-mention-input>
                  <button data-testid="reply-post-btn"
                          class="comment-post-btn" [disabled]="!replyText.trim()"
                          (click)="submitReply(parent); $event.stopPropagation()">
                    {{ 'CLUB_FEED.COMMENT_POST' | translate }}
                  </button>
                </div>
              }
            </div>
          }
        </div>

        <div class="comment-input-row">
          <app-koval-mention-input
            [clubId]="clubId"
            [placeholder]="'CLUB_FEED.COMMENT_PLACEHOLDER' | translate"
            [value]="commentText"
            [resetSignal]="commentResetTick"
            testId="comment-input"
            (textChange)="commentText = $event"
            (mentionsChange)="commentMentionIds = $event"
            (submitted)="submitComment()">
          </app-koval-mention-input>
          <button data-testid="comment-post-btn"
                  class="comment-post-btn" [disabled]="!commentText.trim()"
                  (click)="submitComment(); $event.stopPropagation()">
            {{ 'CLUB_FEED.COMMENT_POST' | translate }}
          </button>
        </div>
      }
    </div>

    <ng-template #commentTpl let-c="c" let-isReply="isReply">
      <div class="comment-item" [class.reply-item]="isReply">
        <div class="comment-avatar">
          @if (c.profilePicture) {
            <img [src]="c.profilePicture" [alt]="c.displayName" />
          } @else {
            {{ c.displayName.charAt(0).toUpperCase() }}
          }
        </div>
        <div class="comment-body">
          <div class="comment-meta">
            <span class="comment-author">{{ c.displayName }}</span>
            <span class="comment-time">{{ relativeTime(c.createdAt) }}</span>
            @if (c.updatedAt) {
              <span class="comment-edited">Ã‚Â· {{ 'CLUB_FEED.EDITED' | translate }}</span>
            }
          </div>
          @if (editingId === c.id) {
            <div class="comment-edit-row">
              <input
                class="comment-input"
                [(ngModel)]="editText"
                (keydown.enter)="confirmEdit(c)"
                (keydown.escape)="cancelEdit()"
                (click)="$event.stopPropagation()"
              />
              <button class="comment-action-btn comment-action-btn--primary"
                      [disabled]="!editText.trim() || editText.trim() === c.content"
                      (click)="confirmEdit(c); $event.stopPropagation()">
                {{ 'COMMON.SAVE' | translate }}
              </button>
              <button class="comment-action-btn"
                      (click)="cancelEdit(); $event.stopPropagation()">
                {{ 'COMMON.CANCEL' | translate }}
              </button>
            </div>
          } @else {
            <div class="comment-text">
              <app-koval-mention-text [text]="c.content" [mentions]="c.mentions ?? []"></app-koval-mention-text>
            </div>
            <app-feed-reaction-bar
              [reactions]="c.reactions"
              [currentUserId]="currentUserId"
              (toggle)="onCommentReact(c, $event)">
            </app-feed-reaction-bar>
            <div class="comment-actions">
              @if (!isReply) {
                <button data-testid="reply-btn" class="comment-action-link"
                        (click)="openReply(c); $event.stopPropagation()">
                  {{ 'CLUB_FEED.REPLY' | translate }}
                </button>
              }
              @if (c.userId === currentUserId) {
                <button class="comment-action-link"
                        (click)="startEdit(c); $event.stopPropagation()">
                  {{ 'COMMON.EDIT' | translate }}
                </button>
                <button class="comment-action-link comment-action-link--danger"
                        (click)="confirmDelete(c); $event.stopPropagation()">
                  {{ 'COMMON.DELETE' | translate }}
                </button>
              }
            </div>
          }
        </div>
      </div>
    </ng-template>
  `,
  styles: `
    .comments-section { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--glass-border); }
    .comments-toggle { display: flex; align-items: center; gap: 6px; background: none; border: none; color: var(--text-muted); font-size: var(--text-xs); cursor: pointer; padding: 0; }
    .comments-toggle:hover { color: var(--text-color); }
    .expand-chevron { transition: transform 0.2s; }
    .expand-chevron.rotated { transform: rotate(180deg); }
    .comments-list { display: flex; flex-direction: column; gap: 8px; margin-top: var(--space-sm); }
    .comment-thread { display: flex; flex-direction: column; gap: 6px; }
    .reply-row { padding-left: 32px; border-left: 2px solid var(--glass-border); }
    .reply-input-row { display: flex; gap: 6px; padding-left: 32px; align-items: flex-start; }
    .comment-item { display: flex; gap: var(--space-sm); }
    .comment-avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--surface-elevated); display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .comment-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .comment-body { flex: 1; min-width: 0; }
    .comment-meta { display: flex; align-items: center; gap: var(--space-xs); }
    .comment-author { font-size: var(--text-xs); font-weight: 600; color: var(--text-color); }
    .comment-time { font-size: 9px; color: var(--text-muted); font-family: monospace; }
    .comment-edited { font-size: 9px; color: var(--text-muted); font-style: italic; }
    .comment-text { font-size: var(--text-xs); color: var(--text-color); line-height: 1.4; word-break: break-word; }
    .comment-actions { display: flex; gap: 8px; margin-top: 2px; }
    .comment-action-link { background: none; border: none; padding: 0; font-size: 10px; color: var(--text-muted); cursor: pointer; font-weight: 500; }
    .comment-action-link:hover { color: var(--accent-color); }
    .comment-action-link--danger:hover { color: var(--danger-color); }
    .comment-edit-row { display: flex; gap: 6px; margin-top: 2px; align-items: center; flex-wrap: wrap; }
    .comment-action-btn { background: var(--surface-elevated); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 4px 8px; font-size: 10px; color: var(--text-color); cursor: pointer; font-weight: 600; }
    .comment-action-btn:hover { background: var(--glass-bg); }
    .comment-action-btn--primary { background: var(--accent-color); color: #000; border-color: var(--accent-color); }
    .comment-action-btn--primary:disabled { opacity: 0.4; cursor: not-allowed; }
    .comment-input-row { display: flex; gap: 6px; margin-top: var(--space-sm); align-items: flex-start; }
    .comment-input { flex: 1; background: var(--surface-elevated); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 6px 10px; font-size: var(--text-xs); color: var(--text-color); outline: none; }
    .comment-input::placeholder { color: var(--text-muted); }
    .comment-input:focus { border-color: var(--accent-color); }
    .comment-post-btn { background: var(--accent-color); color: #000; border: none; border-radius: var(--radius-sm); padding: 6px 12px; font-size: var(--text-xs); font-weight: 600; cursor: pointer; white-space: nowrap; }
    .comment-post-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .comment-post-btn:hover:not(:disabled) { opacity: 0.9; }
  `,
})
export class FeedCommentsSectionComponent {
  @Input({required: true}) clubId!: string;
  @Input() eventId!: string;
  @Input() comments: FeedCommentEntry[] = [];
  @Input() currentUserId: string | null = null;

  @Output() commentSubmitted = new EventEmitter<{eventId: string; content: string; mentionUserIds: string[]}>();
  @Output() replySubmitted = new EventEmitter<CommentReplyEvent>();
  @Output() commentEdited = new EventEmitter<{eventId: string; commentId: string; content: string}>();
  @Output() commentDeleted = new EventEmitter<{eventId: string; commentId: string}>();
  @Output() commentReacted = new EventEmitter<CommentReactionEvent>();

  private translate = inject(TranslateService);

  expanded = false;
  commentText = '';
  commentMentionIds: string[] = [];
  commentResetTick = 0;
  replyText = '';
  replyMentionIds: string[] = [];
  replyResetTick = 0;
  replyOpenFor: string | null = null;
  editingId: string | null = null;
  editText = '';

  get commentCount(): number {
    return this.comments?.length ?? 0;
  }

  get topLevelComments(): FeedCommentEntry[] {
    return (this.comments ?? []).filter((c) => !c.parentCommentId);
  }

  repliesOf(parentId: string): FeedCommentEntry[] {
    return (this.comments ?? []).filter((c) => c.parentCommentId === parentId);
  }

  toggleExpanded(ev: Event): void {
    ev.stopPropagation();
    this.expanded = !this.expanded;
  }

  submitComment(): void {
    const text = this.commentText.trim();
    if (!text) return;
    this.commentSubmitted.emit({
      eventId: this.eventId,
      content: text,
      mentionUserIds: this.commentMentionIds,
    });
    this.commentText = '';
    this.commentMentionIds = [];
    this.commentResetTick++;
  }

  openReply(c: FeedCommentEntry): void {
    this.replyOpenFor = this.replyOpenFor === c.id ? null : c.id;
    this.replyText = '';
    this.replyMentionIds = [];
    this.replyResetTick++;
  }

  submitReply(parent: FeedCommentEntry): void {
    const text = this.replyText.trim();
    if (!text) return;
    this.replySubmitted.emit({
      eventId: this.eventId,
      parentCommentId: parent.id,
      content: text,
      mentionUserIds: this.replyMentionIds,
    });
    this.replyText = '';
    this.replyMentionIds = [];
    this.replyOpenFor = null;
    this.replyResetTick++;
  }

  onCommentReact(c: FeedCommentEntry, emoji: ReactionEmoji): void {
    this.commentReacted.emit({eventId: this.eventId, commentId: c.id, emoji});
  }

  startEdit(c: FeedCommentEntry): void {
    this.editingId = c.id;
    this.editText = c.content;
  }

  cancelEdit(): void {
    this.editingId = null;
    this.editText = '';
  }

  confirmEdit(c: FeedCommentEntry): void {
    const text = this.editText.trim();
    if (!text || text === c.content) {
      this.cancelEdit();
      return;
    }
    this.commentEdited.emit({eventId: this.eventId, commentId: c.id, content: text});
    this.cancelEdit();
  }

  confirmDelete(c: FeedCommentEntry): void {
    if (!confirm(this.translate.instant('CLUB_FEED.CONFIRM_DELETE_COMMENT'))) return;
    this.commentDeleted.emit({eventId: this.eventId, commentId: c.id});
  }

  relativeTime(dateStr?: string): string {
    if (!dateStr) return '';
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'now';
    if (mins < 60) return `${mins}m`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h`;
    const days = Math.floor(hours / 24);
    return `${days}d`;
  }
}

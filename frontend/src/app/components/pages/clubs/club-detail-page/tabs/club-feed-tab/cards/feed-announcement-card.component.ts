import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {ClubFeedEventResponse} from '../../../../../../../services/club.service';

@Component({
  selector: 'app-feed-announcement-card',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-testid="feed-event" class="feed-card feed-card--announcement">
      <div class="announcement-header">
        <div class="author-avatar">
          @if (event.authorProfilePicture) {
            <img [src]="event.authorProfilePicture" [alt]="event.authorName" />
          } @else {
            {{ event.authorName?.charAt(0)?.toUpperCase() }}
          }
        </div>
        <div class="author-info">
          <span class="author-name">{{ event.authorName }}</span>
          <span class="author-role">{{ 'CLUB_FEED.COACH' | translate }}</span>
        </div>
        <span class="announcement-time">{{ relativeTime(event.createdAt) }}</span>
      </div>
      <div class="announcement-content">{{ event.announcementContent }}</div>

      <!-- Comments section -->
      <div class="comments-section">
        <button class="comments-toggle" (click)="commentsExpanded = !commentsExpanded">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          @if (commentCount === 1) {
            {{ 'CLUB_FEED.COMMENT_COUNT_ONE' | translate }}
          } @else {
            {{ 'CLUB_FEED.COMMENT_COUNT' | translate: {count: commentCount} }}
          }
          <svg class="expand-chevron" [class.rotated]="commentsExpanded" width="12" height="12" viewBox="0 0 24 24"
               fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>

        @if (commentsExpanded) {
          <div class="comments-list">
            @for (c of event.comments; track c.id) {
              <div class="comment-item">
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
                  </div>
                  <div class="comment-text">{{ c.content }}</div>
                </div>
              </div>
            }
          </div>

          <div class="comment-input-row">
            <input
              class="comment-input"
              [placeholder]="'CLUB_FEED.COMMENT_PLACEHOLDER' | translate"
              [(ngModel)]="commentText"
              (keydown.enter)="submitComment()"
            />
            <button class="comment-post-btn" [disabled]="!commentText.trim()" (click)="submitComment()">
              {{ 'CLUB_FEED.COMMENT_POST' | translate }}
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: `
    .feed-card--announcement { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: var(--radius-md); padding: var(--space-md); }
    .announcement-header { display: flex; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-sm); }
    .author-avatar { width: 36px; height: 36px; border-radius: 50%; background: var(--surface-elevated); display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .author-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .author-info { flex: 1; display: flex; flex-direction: column; }
    .author-name { font-size: var(--text-sm); font-weight: 600; color: var(--text-color); }
    .author-role { font-size: 9px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--primary); }
    .announcement-time { font-size: 9px; color: var(--text-muted); font-family: monospace; white-space: nowrap; }
    .announcement-content { font-size: var(--text-sm); line-height: 1.5; color: var(--text-color); white-space: pre-wrap; word-break: break-word; }

    .comments-section { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--glass-border); }
    .comments-toggle { display: flex; align-items: center; gap: 6px; background: none; border: none; color: var(--text-muted); font-size: var(--text-xs); cursor: pointer; padding: 0; }
    .comments-toggle:hover { color: var(--text-color); }
    .expand-chevron { transition: transform 0.2s; }
    .expand-chevron.rotated { transform: rotate(180deg); }
    .comments-list { display: flex; flex-direction: column; gap: 8px; margin-top: var(--space-sm); }
    .comment-item { display: flex; gap: var(--space-sm); }
    .comment-avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--surface-elevated); display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .comment-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .comment-body { flex: 1; min-width: 0; }
    .comment-meta { display: flex; align-items: center; gap: var(--space-xs); }
    .comment-author { font-size: var(--text-xs); font-weight: 600; color: var(--text-color); }
    .comment-time { font-size: 9px; color: var(--text-muted); font-family: monospace; }
    .comment-text { font-size: var(--text-xs); color: var(--text-color); line-height: 1.4; word-break: break-word; }
    .comment-input-row { display: flex; gap: 6px; margin-top: var(--space-sm); }
    .comment-input { flex: 1; background: var(--surface-elevated); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 6px 10px; font-size: var(--text-xs); color: var(--text-color); outline: none; }
    .comment-input::placeholder { color: var(--text-muted); }
    .comment-input:focus { border-color: var(--primary); }
    .comment-post-btn { background: var(--primary); color: #000; border: none; border-radius: var(--radius-sm); padding: 6px 12px; font-size: var(--text-xs); font-weight: 600; cursor: pointer; white-space: nowrap; }
    .comment-post-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .comment-post-btn:hover:not(:disabled) { opacity: 0.9; }
  `,
})
export class FeedAnnouncementCardComponent {
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;
  @Output() commentSubmitted = new EventEmitter<{eventId: string; content: string}>();

  commentText = '';
  commentsExpanded = false;

  get commentCount(): number {
    return this.event.comments?.length ?? 0;
  }

  submitComment(): void {
    const text = this.commentText.trim();
    if (!text) return;
    this.commentSubmitted.emit({eventId: this.event.id, content: text});
    this.commentText = '';
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

import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {ClubFeedEventResponse} from '../../../../../../../services/club.service';

@Component({
  selector: 'app-feed-race-completion-card',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="feed-card feed-card--pinned">
      <div class="card-pin-badge">{{ 'CLUB_FEED.RACE_COMPLETED' | translate }}</div>
      <div class="card-header">
        <svg class="trophy-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/>
          <path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/>
          <path d="M4 22h16"/>
          <path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/>
          <path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/>
          <path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/>
        </svg>
        <div class="card-header-info">
          <span class="card-title">{{ event.raceTitle }}</span>
          <span class="card-subtitle">{{ formatDate(event.raceDate) }}</span>
        </div>
      </div>

      @if (event.raceCompletions && event.raceCompletions.length > 0) {
        <div class="completion-list">
          @for (c of event.raceCompletions; track c.userId) {
            <div class="completion-user">
              <div class="user-avatar-sm">
                @if (c.profilePicture) {
                  <img [src]="c.profilePicture" [alt]="c.displayName" />
                } @else {
                  {{ c.displayName.charAt(0).toUpperCase() }}
                }
              </div>
              <span class="user-name">{{ c.displayName }}</span>
              @if (c.finishTime) {
                <span class="finish-time">{{ c.finishTime }}</span>
              }
              @if (c.stravaActivityId) {
                <span class="strava-badge" title="Strava">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="#fc4c02">
                    <path d="M15.387 17.944l-2.089-4.116h-3.065L15.387 24l5.15-10.172h-3.066m-7.008-5.599l2.836 5.598h4.172L10.463 0l-7 13.828h4.169"/>
                  </svg>
                </span>
              }
            </div>
          }
        </div>
      }

      <div class="card-actions">
        @if (!hasGivenKudos) {
          <button class="kudos-btn" [disabled]="kudosLoading" (click)="onGiveKudos()">
            @if (kudosLoading) { <span class="spinner-sm"></span> }
            {{ 'CLUB_FEED.GIVE_KUDOS' | translate }}
          </button>
        } @else {
          <span class="kudos-given">{{ 'CLUB_FEED.KUDOS_GIVEN' | translate }}</span>
        }
      </div>

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
    .feed-card { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: var(--radius-md); padding: var(--space-md); }
    .feed-card--pinned { border-left: 3px solid var(--primary); }
    .card-pin-badge { font-size: 9px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: var(--primary); margin-bottom: var(--space-xs); }
    .card-header { display: flex; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-sm); }
    .trophy-icon { color: var(--warning, #f59e0b); flex-shrink: 0; }
    .card-header-info { flex: 1; display: flex; flex-direction: column; }
    .card-title { font-size: var(--text-sm); font-weight: 600; color: var(--text-color); }
    .card-subtitle { font-size: var(--text-xs); color: var(--text-muted); }
    .completion-list { display: flex; flex-direction: column; gap: 6px; padding: var(--space-sm) 0; border-top: 1px solid var(--glass-border); }
    .completion-user { display: flex; align-items: center; gap: var(--space-sm); }
    .user-avatar-sm { width: 24px; height: 24px; border-radius: 50%; background: var(--surface-elevated); display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .user-avatar-sm img { width: 100%; height: 100%; object-fit: cover; }
    .user-name { flex: 1; font-size: var(--text-xs); font-weight: 500; color: var(--text-color); }
    .finish-time { font-size: 10px; font-weight: 600; color: var(--success); font-family: monospace; }
    .strava-badge { display: flex; align-items: center; }
    .card-actions { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--glass-border); }
    .kudos-btn { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; padding: 8px; border: none; border-radius: var(--radius-sm); background: #fc4c02; color: #fff; font-size: var(--text-xs); font-weight: 600; cursor: pointer; transition: opacity 0.2s; }
    .kudos-btn:hover:not(:disabled) { opacity: 0.9; }
    .kudos-btn:disabled { opacity: 0.6; cursor: not-allowed; }
    .kudos-given { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; padding: 8px; font-size: var(--text-xs); font-weight: 600; color: var(--success); }
    .spinner-sm { width: 12px; height: 12px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.6s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

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
export class FeedRaceCompletionCardComponent {
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;
  @Output() kudosRequested = new EventEmitter<string>();
  @Output() commentSubmitted = new EventEmitter<{eventId: string; content: string}>();

  kudosLoading = false;
  commentText = '';
  commentsExpanded = false;

  get hasGivenKudos(): boolean {
    return !!this.currentUserId && (this.event.kudosGivenBy ?? []).includes(this.currentUserId);
  }

  get commentCount(): number {
    return this.event.comments?.length ?? 0;
  }

  onGiveKudos(): void {
    this.kudosLoading = true;
    this.kudosRequested.emit(this.event.id);
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

  formatDate(dateStr?: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-US', {month: 'long', day: 'numeric', year: 'numeric'});
  }
}

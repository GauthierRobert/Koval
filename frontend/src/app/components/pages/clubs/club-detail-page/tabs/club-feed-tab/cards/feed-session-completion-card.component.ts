import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {ClubFeedEventResponse, ReactionEmoji} from '../../../../../../../services/club.service';
import {SportIconComponent} from '../../../../../../shared/sport-icon/sport-icon.component';
import {FeedReactionBarComponent} from '../../../../../../shared/feed-reaction-bar/feed-reaction-bar.component';
import {FeedCommentsSectionComponent} from './feed-comments-section.component';

@Component({
  selector: 'app-feed-session-completion-card',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    SportIconComponent,
    FeedReactionBarComponent,
    FeedCommentsSectionComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-testid="feed-event" class="feed-card feed-card--pinned" [class.feed-card--expanded]="expanded">
      <div class="card-pin-badge">{{ 'CLUB_FEED.PINNED' | translate }}</div>
      <div class="card-header">
        <app-sport-icon [sport]="$any(event.sessionSport ?? 'CYCLING')" [size]="20"></app-sport-icon>
        <div class="card-header-info">
          <span class="card-title">{{ event.sessionTitle }}</span>
          <span class="card-subtitle">{{ formatDate(event.sessionScheduledAt) }}</span>
        </div>
        <span class="completion-count">{{ event.completions?.length ?? 0 }}</span>
      </div>

      <div class="completion-summary" (click)="expanded = !expanded">
        <div class="avatar-stack">
          @for (c of displayedCompletions; track c.userId) {
            <div class="avatar-circle" [title]="c.displayName">
              @if (c.profilePicture) {
                <img [src]="c.profilePicture" [alt]="c.displayName" />
              } @else {
                {{ c.displayName.charAt(0).toUpperCase() }}
              }
            </div>
          }
          @if (extraCount > 0) {
            <div class="avatar-circle avatar-extra">+{{ extraCount }}</div>
          }
        </div>
        <span class="completion-label">
          {{ 'CLUB_FEED.ATHLETES_COMPLETED' | translate: {count: event.completions?.length ?? 0} }}
        </span>
        <div class="completion-actions">
          @if (!hasGivenKudos) {
            <button data-testid="feed-kudos-btn" class="kudos-btn" [disabled]="kudosLoading"
              [title]="'CLUB_FEED.KUDOS_HINT' | translate"
              (click)="onGiveKudos(); $event.stopPropagation()">
              @if (kudosLoading) {
                <span class="spinner-sm"></span>
              }
              <svg class="strava-logo" viewBox="0 0 24 24" width="14" height="14" fill="white"><path d="M15.387 17.944l-2.089-4.116h-3.065L15.387 24l5.15-10.172h-3.066m-7.008-5.599l2.836 5.598h4.172L10.463 0l-7 13.828h4.169"/></svg>
              {{ 'CLUB_FEED.GIVE_KUDOS' | translate }}
            </button>
          } @else {
            <span class="kudos-given" [title]="'CLUB_FEED.KUDOS_HINT' | translate">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
              {{ 'CLUB_FEED.KUDOS_GIVEN' | translate }}
            </span>
          }
        </div>
        <svg class="expand-chevron" [class.rotated]="expanded" width="14" height="14" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>

      @if (expanded) {
        <div class="completion-list">
          @for (c of event.completions; track c.userId) {
            <div class="completion-user">
              <div class="user-avatar-sm">
                @if (c.profilePicture) {
                  <img [src]="c.profilePicture" [alt]="c.displayName" />
                } @else {
                  {{ c.displayName.charAt(0).toUpperCase() }}
                }
              </div>
              <span class="user-name">{{ c.displayName }}</span>
              @if (c.stravaActivityId) {
                <span class="strava-badge" title="Strava">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="#fc4c02">
                    <path d="M15.387 17.944l-2.089-4.116h-3.065L15.387 24l5.15-10.172h-3.066m-7.008-5.599l2.836 5.598h4.172L10.463 0l-7 13.828h4.169"/>
                  </svg>
                </span>
              }
              <span class="completion-time">{{ formatTime(c.completedAt) }}</span>
            </div>
          }
        </div>
      }

      <app-feed-reaction-bar
        [reactions]="event.reactions"
        [currentUserId]="currentUserId"
        (toggle)="reacted.emit({eventId: event.id, emoji: $event})">
      </app-feed-reaction-bar>

      <app-feed-comments-section
        [clubId]="clubId"
        [eventId]="event.id"
        [comments]="event.comments ?? []"
        [currentUserId]="currentUserId"
        (commentSubmitted)="commentSubmitted.emit($event)"
        (replySubmitted)="replySubmitted.emit($event)"
        (commentEdited)="commentEdited.emit($event)"
        (commentDeleted)="commentDeleted.emit($event)"
        (commentReacted)="commentReacted.emit($event)">
      </app-feed-comments-section>
    </div>
  `,
  styles: `
    .feed-card { background: var(--glass-bg); border-radius: var(--radius-md); padding: var(--space-md); }
    .feed-card--pinned { border-left: 3px solid var(--primary); }
    .card-pin-badge { font-size: 9px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: var(--primary); margin-bottom: var(--space-xs); }
    .card-header { display: flex; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-sm); }
    .card-header-info { flex: 1; display: flex; flex-direction: column; }
    .card-title { font-size: var(--text-sm); font-weight: 600; color: var(--text-color); }
    .card-subtitle { font-size: var(--text-xs); color: var(--text-muted); }
    .completion-count { font-size: var(--text-lg); font-weight: 700; color: var(--primary); min-width: 28px; text-align: center; }
    .completion-summary { display: flex; align-items: center; gap: var(--space-sm); cursor: pointer; padding: var(--space-xs) 0; }
    .avatar-stack { display: flex; }
    .avatar-circle { width: 28px; height: 28px; border-radius: 50%; background: var(--surface-elevated); border: 2px solid var(--bg-color); display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 600; color: var(--text-muted); margin-left: -8px; overflow: hidden; flex-shrink: 0; }
    .avatar-circle:first-child { margin-left: 0; }
    .avatar-circle img { width: 100%; height: 100%; object-fit: cover; }
    .avatar-extra { background: var(--primary); color: #000; font-size: 9px; }
    .completion-label { flex: 1; font-size: var(--text-xs); color: var(--text-muted); }
    .expand-chevron { color: var(--text-muted); transition: transform 0.2s; flex-shrink: 0; }
    .expand-chevron.rotated { transform: rotate(180deg); }
    .completion-list { display: flex; flex-direction: column; gap: 6px; padding: var(--space-sm) 0; border-top: 1px solid var(--glass-border); margin-top: var(--space-xs); }
    .completion-user { display: flex; align-items: center; gap: var(--space-sm); }
    .user-avatar-sm { width: 24px; height: 24px; border-radius: 50%; background: var(--surface-elevated); display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .user-avatar-sm img { width: 100%; height: 100%; object-fit: cover; }
    .user-name { flex: 1; font-size: var(--text-xs); font-weight: 500; color: var(--text-color); }
    .strava-badge { display: flex; align-items: center; }
    .completion-time { font-size: 9px; color: var(--text-muted); font-family: monospace; }
    .completion-actions { margin-left: auto; flex-shrink: 0; }
    .kudos-btn { display: inline-flex; align-items: center; gap: 5px; padding: 5px 12px; border: none; border-radius: var(--radius-sm); background: #fc4c02; color: #fff; font-size: var(--text-xs); font-weight: 600; cursor: pointer; transition: opacity 0.2s; white-space: nowrap; }
    .kudos-btn:hover:not(:disabled) { opacity: 0.9; }
    .kudos-btn:disabled { opacity: 0.6; cursor: not-allowed; }
    .kudos-given { display: inline-flex; align-items: center; gap: 5px; padding: 5px 12px; font-size: var(--text-xs); font-weight: 600; color: var(--success); white-space: nowrap; }
    .spinner-sm { width: 12px; height: 12px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.6s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
  `,
})
export class FeedSessionCompletionCardComponent {
  @Input({required: true}) clubId!: string;
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;
  @Output() kudosRequested = new EventEmitter<string>();
  @Output() commentSubmitted = new EventEmitter<{eventId: string; content: string; mentionUserIds: string[]}>();
  @Output() replySubmitted = new EventEmitter<{eventId: string; parentCommentId: string; content: string; mentionUserIds: string[]}>();
  @Output() commentEdited = new EventEmitter<{eventId: string; commentId: string; content: string}>();
  @Output() commentDeleted = new EventEmitter<{eventId: string; commentId: string}>();
  @Output() commentReacted = new EventEmitter<{eventId: string; commentId: string; emoji: ReactionEmoji}>();
  @Output() reacted = new EventEmitter<{eventId: string; emoji: ReactionEmoji}>();

  expanded = false;
  kudosLoading = false;

  get displayedCompletions() {
    return (this.event.completions ?? []).slice(0, 5);
  }

  get extraCount() {
    return Math.max(0, (this.event.completions?.length ?? 0) - 5);
  }

  get hasGivenKudos(): boolean {
    return !!this.currentUserId && (this.event.kudosGivenBy ?? []).includes(this.currentUserId);
  }

  onGiveKudos(): void {
    this.kudosLoading = true;
    this.kudosRequested.emit(this.event.id);
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleString('en-US', {
      weekday: 'short', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }

  formatTime(dateStr?: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleTimeString('en-US', {hour: '2-digit', minute: '2-digit'});
  }
}

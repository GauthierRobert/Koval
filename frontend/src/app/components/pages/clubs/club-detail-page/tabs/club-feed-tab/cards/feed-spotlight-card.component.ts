import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  ClubFeedEventResponse,
  ReactionEmoji,
  SpotlightBadge,
} from '../../../../../../../services/club.service';
import {KovalImageComponent} from '../../../../../../shared/koval-image/koval-image.component';
import {KovalMentionTextComponent} from '../../../../../../shared/koval-mention-text/koval-mention-text.component';
import {FeedReactionBarComponent} from '../../../../../../shared/feed-reaction-bar/feed-reaction-bar.component';
import {FeedCommentsSectionComponent} from './feed-comments-section.component';

const BADGE_LABEL_KEY: Record<SpotlightBadge, string> = {
  MILESTONE: 'CLUB_FEED.SPOTLIGHT_BADGE_MILESTONE',
  COMEBACK: 'CLUB_FEED.SPOTLIGHT_BADGE_COMEBACK',
  NEW_MEMBER: 'CLUB_FEED.SPOTLIGHT_BADGE_NEW_MEMBER',
  PR: 'CLUB_FEED.SPOTLIGHT_BADGE_PR',
  GRIT: 'CLUB_FEED.SPOTLIGHT_BADGE_GRIT',
  CUSTOM: 'CLUB_FEED.SPOTLIGHT_BADGE_CUSTOM',
};

const BADGE_GLYPH: Record<SpotlightBadge, string> = {
  MILESTONE: '\u{1F3C6}',
  COMEBACK: '\u{1F525}',
  NEW_MEMBER: '\u{1F44B}',
  PR: '\u{1F31F}',
  GRIT: '\u{1F4AA}',
  CUSTOM: '\u{2728}',
};

@Component({
  selector: 'app-feed-spotlight-card',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    KovalImageComponent,
    KovalMentionTextComponent,
    FeedReactionBarComponent,
    FeedCommentsSectionComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-testid="feed-event" class="spotlight-card" [class.spotlight-card--pinned]="event.pinned">
      <div class="spotlight-pin-row">
        <span class="spotlight-badge" [attr.data-badge]="event.spotlightBadge">
          <span class="spotlight-badge-glyph">{{ badgeGlyph }}</span>
          {{ badgeLabel | translate }}
        </span>
        @if (canManage) {
          <button class="spotlight-action-link spotlight-action-link--danger"
                  (click)="onDelete()">
            {{ 'COMMON.DELETE' | translate }}
          </button>
        }
      </div>

      <div class="spotlight-hero">
        <div class="spotlight-avatar">
          @if (event.spotlightedProfilePicture) {
            <img [src]="event.spotlightedProfilePicture" [alt]="event.spotlightedDisplayName" />
          } @else {
            {{ event.spotlightedDisplayName?.charAt(0)?.toUpperCase() }}
          }
        </div>
        <div class="spotlight-headline">
          <span class="spotlight-name">{{ event.spotlightedDisplayName }}</span>
          <span class="spotlight-title">{{ event.spotlightTitle }}</span>
        </div>
      </div>

      @if (event.spotlightMessage) {
        <div class="spotlight-message">
          <app-koval-mention-text
            [text]="event.spotlightMessage"
            [mentions]="event.mentionRefs ?? []">
          </app-koval-mention-text>
        </div>
      }

      @if (imageAttachments.length > 0) {
        <div class="spotlight-images" [class.spotlight-images--single]="imageAttachments.length === 1">
          @for (a of imageAttachments; track a.id) {
            <a class="spotlight-image-link" [href]="a.file?.originalUrl" target="_blank" rel="noopener">
              <koval-image [media]="a.file" size="medium" [alt]="a.file?.originalFileName || ''"></koval-image>
            </a>
          }
        </div>
      }

      <div class="spotlight-meta">
        <span class="spotlight-author">
          {{ 'CLUB_FEED.SPOTLIGHTED_BY' | translate }} {{ event.authorName }}
        </span>
        @if (event.spotlightExpiresAt) {
          <span class="spotlight-expires">
            · {{ 'CLUB_FEED.SPOTLIGHT_EXPIRES' | translate }} {{ daysRemainingLabel }}
          </span>
        }
      </div>

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
    .spotlight-card {
      position: relative;
      background: linear-gradient(135deg, rgba(125, 211, 252, 0.10), rgba(245, 158, 11, 0.06));
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-md);
      padding: var(--space-md);
      overflow: hidden;
    }
    .spotlight-card--pinned { border-left: 3px solid var(--warning, #f59e0b); }
    .spotlight-pin-row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); }
    .spotlight-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 999px;
      background: var(--surface-elevated);
      border: 1px solid var(--glass-border);
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--warning, #f59e0b);
    }
    .spotlight-badge[data-badge="PR"] { color: var(--primary); }
    .spotlight-badge[data-badge="GRIT"] { color: var(--danger, #ef4444); }
    .spotlight-badge[data-badge="NEW_MEMBER"] { color: var(--success); }
    .spotlight-badge-glyph { font-size: 13px; line-height: 1; }
    .spotlight-action-link { background: none; border: none; padding: 0; font-size: 10px; color: var(--text-muted); cursor: pointer; font-weight: 500; }
    .spotlight-action-link:hover { color: var(--primary); }
    .spotlight-action-link--danger:hover { color: var(--danger, #ef4444); }
    .spotlight-hero { display: flex; align-items: center; gap: var(--space-sm); margin-top: var(--space-sm); }
    .spotlight-avatar {
      width: 56px; height: 56px; border-radius: 50%;
      background: var(--surface-elevated);
      display: flex; align-items: center; justify-content: center;
      overflow: hidden; flex-shrink: 0;
      font-size: 20px; font-weight: 700; color: var(--text-muted);
      border: 2px solid var(--warning, #f59e0b);
    }
    .spotlight-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .spotlight-headline { display: flex; flex-direction: column; flex: 1; min-width: 0; }
    .spotlight-name { font-size: var(--text-sm); font-weight: 600; color: var(--text-color); }
    .spotlight-title { font-size: var(--text-md, 16px); font-weight: 700; color: var(--text-color); line-height: 1.2; }
    .spotlight-message {
      margin-top: var(--space-sm);
      font-size: var(--text-sm);
      line-height: 1.5;
      color: var(--text-color);
    }
    .spotlight-images { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: var(--space-sm); border-radius: var(--radius-sm); overflow: hidden; }
    .spotlight-images--single { grid-template-columns: 1fr; max-width: 480px; }
    .spotlight-image-link { display: block; border-radius: var(--radius-sm); overflow: hidden; line-height: 0; }
    .spotlight-meta { margin-top: var(--space-sm); font-size: 10px; color: var(--text-muted); }
    .spotlight-author, .spotlight-expires { font-style: normal; }
  `,
})
export class FeedSpotlightCardComponent {
  @Input({required: true}) clubId!: string;
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;
  @Input() canManage = false;

  @Output() reacted = new EventEmitter<{eventId: string; emoji: ReactionEmoji}>();
  @Output() commentSubmitted = new EventEmitter<{eventId: string; content: string; mentionUserIds: string[]}>();
  @Output() replySubmitted = new EventEmitter<{eventId: string; parentCommentId: string; content: string; mentionUserIds: string[]}>();
  @Output() commentEdited = new EventEmitter<{eventId: string; commentId: string; content: string}>();
  @Output() commentDeleted = new EventEmitter<{eventId: string; commentId: string}>();
  @Output() commentReacted = new EventEmitter<{eventId: string; commentId: string; emoji: ReactionEmoji}>();
  @Output() spotlightDeleted = new EventEmitter<string>();

  private translate = inject(TranslateService);

  get badgeGlyph(): string {
    return this.event.spotlightBadge ? BADGE_GLYPH[this.event.spotlightBadge] : '\u{2728}';
  }

  get badgeLabel(): string {
    return this.event.spotlightBadge
      ? BADGE_LABEL_KEY[this.event.spotlightBadge]
      : 'CLUB_FEED.SPOTLIGHT_BADGE_CUSTOM';
  }

  get imageAttachments() {
    return (this.event.announcementAttachments ?? []).filter(
      (a) => !!a.file && (a.file.contentType?.startsWith('image/') ?? false),
    );
  }

  get daysRemainingLabel(): string {
    if (!this.event.spotlightExpiresAt) return '';
    const ms = new Date(this.event.spotlightExpiresAt).getTime() - Date.now();
    if (ms <= 0) return this.translate.instant('CLUB_FEED.SPOTLIGHT_EXPIRED');
    const days = Math.ceil(ms / 86400000);
    return days === 1 ? '1d' : `${days}d`;
  }

  onDelete(): void {
    if (!confirm(this.translate.instant('CLUB_FEED.CONFIRM_DELETE_SPOTLIGHT'))) return;
    this.spotlightDeleted.emit(this.event.id);
  }
}

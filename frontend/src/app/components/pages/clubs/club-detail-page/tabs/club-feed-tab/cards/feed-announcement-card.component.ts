import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ClubFeedEventResponse} from '../../../../../../../services/club.service';

@Component({
  selector: 'app-feed-announcement-card',
  standalone: true,
  imports: [CommonModule, TranslateModule],
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
  `,
})
export class FeedAnnouncementCardComponent {
  @Input() event!: ClubFeedEventResponse;

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

import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ClubFeedEventResponse, ReactionEmoji } from '../../../../../../../services/club.service';
import { SportIconComponent } from '../../../../../../shared/sport-icon/sport-icon.component';
import { FeedReactionBarComponent } from '../../../../../../shared/feed-reaction-bar/feed-reaction-bar.component';

@Component({
  selector: 'app-feed-next-goal-card',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent, FeedReactionBarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="feed-card feed-card--goal">
      <div class="goal-header">
        <div class="goal-flag">
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
            <line x1="4" y1="22" x2="4" y2="15" />
          </svg>
        </div>
        <span class="goal-label">{{ 'CLUB_FEED.NEXT_GOAL' | translate }}</span>
        <span class="goal-countdown">{{ daysUntil }} {{ 'CLUB_FEED.DAYS' | translate }}</span>
      </div>
      <div class="goal-info">
        <app-sport-icon [sport]="$any(event.goalSport ?? 'RUNNING')" [size]="16" />
        <span class="goal-title">{{ event.goalTitle }}</span>
      </div>
      @if (event.goalDate) {
        <span class="goal-date">{{ formatDate(event.goalDate) }}</span>
      }
      @if (event.goalLocation) {
        <span class="goal-location">{{ event.goalLocation }}</span>
      }
      @if (event.engagedAthletes && event.engagedAthletes.length > 0) {
        <div class="goal-athletes">
          <div class="avatar-stack">
            @for (a of event.engagedAthletes.slice(0, 5); track a.userId) {
              <div class="avatar-circle" [title]="a.displayName">
                @if (a.profilePicture) {
                  <img [src]="a.profilePicture" [alt]="a.displayName" />
                } @else {
                  {{ a.displayName.charAt(0).toUpperCase() }}
                }
              </div>
            }
          </div>
          <span class="athlete-count"
            >{{ event.engagedAthletes.length }} {{ 'CLUB_FEED.ATHLETES_ENGAGED' | translate }}</span
          >
        </div>
      }

      <app-feed-reaction-bar
        [reactions]="event.reactions"
        [currentUserId]="currentUserId"
        (toggled)="reacted.emit({ eventId: event.id, emoji: $event })"
      />
    </div>
  `,
  styles: `
    .feed-card--goal {
      background: var(--glass-bg);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }
    .goal-header {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
      margin-bottom: var(--space-sm);
    }
    .goal-flag {
      color: var(--dev-accent-color);
      display: flex;
    }
    .goal-label {
      font-size: 9px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--dev-accent-color);
      flex: 1;
    }
    .goal-countdown {
      font-size: var(--text-sm);
      font-weight: 700;
      color: var(--accent-color);
    }
    .goal-info {
      display: flex;
      align-items: center;
      gap: var(--space-xs);
      margin-bottom: 4px;
    }
    .goal-title {
      font-size: var(--text-sm);
      font-weight: 600;
      color: var(--text-color);
    }
    .goal-date,
    .goal-location {
      display: block;
      font-size: var(--text-xs);
      color: var(--text-muted);
    }
    .goal-athletes {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
      margin-top: var(--space-sm);
    }
    .avatar-stack {
      display: flex;
    }
    .avatar-circle {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: var(--surface-elevated);
      border: 2px solid var(--bg-color);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 9px;
      font-weight: 600;
      color: var(--text-muted);
      margin-left: -6px;
      overflow: hidden;
      flex-shrink: 0;
    }
    .avatar-circle:first-child {
      margin-left: 0;
    }
    .avatar-circle img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .athlete-count {
      font-size: var(--text-xs);
      color: var(--text-muted);
    }
  `,
})
export class FeedNextGoalCardComponent {
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;
  @Output() reacted = new EventEmitter<{ eventId: string; emoji: ReactionEmoji }>();

  get daysUntil(): number {
    if (!this.event.goalDate) return 0;
    const diff = new Date(this.event.goalDate).getTime() - Date.now();
    return Math.max(0, Math.ceil(diff / 86400000));
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  }
}

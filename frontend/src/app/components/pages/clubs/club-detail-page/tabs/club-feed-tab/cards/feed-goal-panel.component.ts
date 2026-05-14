import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ClubRaceGoalResponse } from '../../../../../../../models/club.model';
import { SportIconComponent } from '../../../../../../shared/sport-icon/sport-icon.component';
import { formatGoalDate, getDaysUntil } from '../../../../../../../utils/club-session-date';

@Component({
  selector: 'app-feed-goal-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sidebar-panel goal-panel">
      <div class="goal-header">
        <svg
          class="goal-flag"
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
        <span class="goal-label">{{ 'CLUB_FEED.NEXT_GOAL' | translate }}</span>
        <span class="goal-countdown">{{ daysUntil }}{{ 'CLUB_FEED.DAYS' | translate }}</span>
      </div>
      <div class="goal-info">
        <app-sport-icon [sport]="$any(goal.sport)" [size]="14" />
        <span class="goal-title">{{ goal.title }}</span>
      </div>
      @if (goal.location) {
        <span class="goal-meta">{{ goal.location }}</span>
      }
      <span class="goal-meta">{{ formattedDate }}</span>
      @if (goal.participants.length > 0) {
        <div class="goal-athletes">
          <div class="avatar-stack">
            @for (a of goal.participants.slice(0, 5); track a.userId) {
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
            >{{ goal.participants.length }} {{ 'CLUB_FEED.ATHLETES_ENGAGED' | translate }}</span
          >
        </div>
      }
    </div>
  `,
  styles: `
    .sidebar-panel {
      display: flex;
      flex-direction: column;
      gap: var(--space-sm);
      background: var(--glass-bg);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }
    .goal-header {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
    }
    .goal-flag {
      color: var(--dev-accent-color);
      flex-shrink: 0;
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
    }
    .goal-title {
      font-size: var(--text-sm);
      font-weight: 600;
      color: var(--text-color);
    }
    .goal-meta {
      font-size: var(--text-xs);
      color: var(--text-muted);
    }
    .goal-athletes {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
      margin-top: 2px;
    }
    .avatar-stack {
      display: flex;
    }
    .avatar-circle {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: var(--surface-elevated);
      border: 2px solid var(--bg-color);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 8px;
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
export class FeedGoalPanelComponent {
  @Input({ required: true }) goal!: ClubRaceGoalResponse;

  get daysUntil(): number {
    return getDaysUntil(this.goal.raceDate);
  }

  get formattedDate(): string {
    return formatGoalDate(this.goal.raceDate);
  }
}

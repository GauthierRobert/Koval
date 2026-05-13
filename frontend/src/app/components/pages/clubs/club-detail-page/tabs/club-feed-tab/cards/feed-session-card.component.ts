import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ClubTrainingSession } from '../../../../../../../models/club.model';
import { SportIconComponent } from '../../../../../../shared/sport-icon/sport-icon.component';
import { SessionParticipation } from '../../../../../../../services/club/club-session-participation.service';
import { formatSessionTime } from '../../../../../../../utils/club-session-date';

@Component({
  selector: 'app-feed-session-card',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="session-card"
      [class.session-card--cancelled]="session.cancelled"
      [class.session-card--selected]="selected"
      (click)="selectionToggle.emit(session.id)"
    >
      <div class="session-row">
        <app-sport-icon [sport]="$any(session.sport ?? 'CYCLING')" [size]="14" />
        <div class="session-info">
          <span class="session-title">{{ session.title }}</span>
          <span class="session-time">{{ formattedTime }}</span>
        </div>
        <div class="session-meta">
          <span class="participant-count"
            >{{ session.participantIds.length }}
            @if (session.maxParticipants) {
              /{{ session.maxParticipants }}
            }
          </span>
          @if (participation.joined) {
            <span class="joined-pill">{{ 'CLUB_FEED.JOINED' | translate }}</span>
          } @else if (participation.onWaitingList) {
            <span class="waiting-pill">{{
              'CLUB_SESSIONS.WAITING_LIST_POSITION'
                | translate: { position: participation.waitingListPosition }
            }}</span>
          } @else if (participation.full) {
            <span class="full-pill">{{ 'CLUB_SESSIONS.BADGE_FULL' | translate }}</span>
          }
        </div>
      </div>

      @if (selected) {
        <div class="session-detail">
          @if (session.location) {
            <span class="detail-row">{{ session.location }}</span>
          }
          @if (session.description) {
            <p class="detail-desc">{{ session.description }}</p>
          }
          @if (session.durationMinutes) {
            <span class="detail-row">{{ session.durationMinutes }} min</span>
          }
          <div class="detail-actions">
            @if (!session.cancelled) {
              @if (!participation.joined && !participation.onWaitingList) {
                <button
                  class="btn-primary small"
                  (click)="join.emit(session.id); $event.stopPropagation()"
                >
                  {{
                    participation.full
                      ? ('CLUB_SESSIONS.BTN_JOIN_WAITING_LIST' | translate)
                      : ('CLUB_FEED.JOIN' | translate)
                  }}
                </button>
              } @else if (participation.joined) {
                <button
                  class="btn-ghost small"
                  (click)="cancelRequest.emit(session.id); $event.stopPropagation()"
                >
                  {{ 'CLUB_FEED.CANCEL' | translate }}
                </button>
              } @else if (participation.onWaitingList) {
                <button
                  class="btn-ghost small"
                  (click)="cancelRequest.emit(session.id); $event.stopPropagation()"
                >
                  {{ 'CLUB_SESSIONS.BTN_LEAVE_WAITING_LIST' | translate }}
                </button>
              }
            } @else {
              <span class="cancelled-label">{{ 'CLUB_FEED.CANCELLED' | translate }}</span>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .session-card {
      background: var(--surface-elevated);
      border: none;
      border-radius: var(--radius-sm);
      padding: 10px;
      cursor: pointer;
      transition: border-color 0.15s;
    }
    .session-card--cancelled {
      opacity: 0.5;
    }
    .session-card--selected {
      background: var(--accent-subtle);
    }
    .session-row {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
    }
    .session-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .session-title {
      font-size: 11px;
      font-weight: 600;
      color: var(--text-color);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .session-time {
      font-size: 9px;
      color: var(--text-muted);
      font-family: monospace;
    }
    .session-meta {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 2px;
    }
    .participant-count {
      font-size: 9px;
      color: var(--text-muted);
      font-family: monospace;
    }
    .joined-pill {
      font-size: 8px;
      font-weight: 700;
      text-transform: uppercase;
      padding: 1px 5px;
      border-radius: 3px;
      background: var(--success-subtle);
      color: var(--success-color);
    }
    .waiting-pill {
      font-size: 8px;
      font-weight: 700;
      padding: 1px 5px;
      border-radius: 3px;
      background: rgba(255, 157, 0, 0.15);
      color: var(--accent-color);
    }
    .full-pill {
      font-size: 8px;
      font-weight: 700;
      text-transform: uppercase;
      padding: 1px 5px;
      border-radius: 3px;
      background: rgba(239, 68, 68, 0.15);
      color: var(--danger);
    }
    .session-detail {
      margin-top: var(--space-sm);
      padding-top: var(--space-sm);
      border-top: 1px solid var(--glass-border);
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .detail-row {
      font-size: var(--text-xs);
      color: var(--text-muted);
    }
    .detail-desc {
      font-size: var(--text-xs);
      color: var(--text-color);
      margin: 0;
      line-height: 1.4;
    }
    .detail-actions {
      display: flex;
      gap: var(--space-sm);
      margin-top: var(--space-xs);
    }
    .cancelled-label {
      font-size: 10px;
      font-weight: 600;
      color: var(--danger);
    }
  `,
})
export class FeedSessionCardComponent {
  @Input({ required: true }) session!: ClubTrainingSession;
  @Input({ required: true }) participation!: SessionParticipation;
  @Input() selected = false;

  @Output() selectionToggle = new EventEmitter<string>();
  @Output() join = new EventEmitter<string>();
  @Output() cancelRequest = new EventEmitter<string>();

  get formattedTime(): string {
    return formatSessionTime(this.session.scheduledAt);
  }
}

import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  ClubDetail,
  ClubRaceGoalResponse,
  ClubTrainingSession,
} from '../../../../../../../models/club.model';
import {
  ClubSessionParticipationService,
  SessionParticipation,
} from '../../../../../../../services/club/club-session-participation.service';
import { FeedSessionCardComponent } from './feed-session-card.component';
import { FeedGoalPanelComponent } from './feed-goal-panel.component';

interface SessionView {
  session: ClubTrainingSession;
  participation: SessionParticipation;
}

interface FeedSessionsView {
  nextGoal: ClubRaceGoalResponse | null;
  recurring: SessionView[];
  open: SessionView[];
}

@Component({
  selector: 'app-feed-sessions-upcoming',
  standalone: true,
  imports: [CommonModule, TranslateModule, FeedSessionCardComponent, FeedGoalPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (vm.nextGoal) {
      <app-feed-goal-panel [goal]="vm.nextGoal" />
    }

    <div class="sidebar-panel">
      <div class="panel-header">
        <span class="panel-title">{{ 'CLUB_FEED.RECURRING_SESSIONS' | translate }}</span>
        <div class="week-nav">
          <button class="nav-btn" (click)="weekChange.emit(-1)">
            <svg
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
          <span class="week-label">{{ weekLabel }}</span>
          <button class="nav-btn" (click)="weekChange.emit(1)">
            <svg
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </div>
      </div>

      @if (vm.recurring.length === 0) {
        <div class="empty-sessions">
          <span class="empty-text">{{ 'CLUB_FEED.NO_SESSIONS' | translate }}</span>
        </div>
      }

      @for (view of vm.recurring; track view.session.id) {
        <app-feed-session-card
          [session]="view.session"
          [participation]="view.participation"
          [selected]="selectedSessionId === view.session.id"
          (selectionToggle)="toggleSession($event)"
          (join)="joinClicked.emit($event)"
          (cancelRequest)="cancelClicked.emit($event)"
        />
      }
    </div>

    @if (vm.open.length > 0) {
      <div class="sidebar-panel">
        <div class="panel-header">
          <span class="panel-title">{{ 'CLUB_FEED.OPEN_SESSIONS' | translate }}</span>
        </div>

        @for (view of vm.open; track view.session.id) {
          <app-feed-session-card
            [session]="view.session"
            [participation]="view.participation"
            [selected]="selectedSessionId === view.session.id"
            (selectionToggle)="toggleSession($event)"
            (join)="joinClicked.emit($event)"
            (cancelRequest)="cancelClicked.emit($event)"
          />
        }
      </div>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-md);
    }
    .sidebar-panel {
      display: flex;
      flex-direction: column;
      gap: var(--space-sm);
      background: var(--glass-bg);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }
    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .panel-title {
      font-size: var(--text-xs);
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--text-muted);
    }
    .week-nav {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .nav-btn {
      background: none;
      border: none;
      color: var(--text-muted);
      cursor: pointer;
      padding: 2px;
      border-radius: 4px;
      display: flex;
    }
    .nav-btn:hover {
      background: var(--surface-elevated);
      color: var(--text-color);
    }
    .week-label {
      font-size: 10px;
      font-weight: 600;
      color: var(--text-muted);
      min-width: 80px;
      text-align: center;
    }
    .empty-sessions {
      padding: var(--space-md) 0;
      text-align: center;
    }
    .empty-text {
      font-size: var(--text-xs);
      color: var(--text-muted);
    }
  `,
})
export class FeedSessionsUpcomingComponent {
  private participation = inject(ClubSessionParticipationService);

  @Input() set sessions(value: ClubTrainingSession[]) {
    this._sessions = value ?? [];
    this.rebuildView();
  }
  get sessions(): ClubTrainingSession[] {
    return this._sessions;
  }

  @Input() club!: ClubDetail;

  @Input() set currentUserId(value: string | null) {
    this._currentUserId = value;
    this.rebuildView();
  }
  get currentUserId(): string | null {
    return this._currentUserId;
  }

  @Input() weekLabel = '';

  @Input() set raceGoals(value: ClubRaceGoalResponse[]) {
    this._raceGoals = value ?? [];
    this.rebuildView();
  }
  get raceGoals(): ClubRaceGoalResponse[] {
    return this._raceGoals;
  }

  @Output() joinClicked = new EventEmitter<string>();
  @Output() cancelClicked = new EventEmitter<string>();
  @Output() weekChange = new EventEmitter<number>();

  selectedSessionId: string | null = null;
  vm: FeedSessionsView = { nextGoal: null, recurring: [], open: [] };

  private _sessions: ClubTrainingSession[] = [];
  private _currentUserId: string | null = null;
  private _raceGoals: ClubRaceGoalResponse[] = [];

  toggleSession(sessionId: string): void {
    this.selectedSessionId = this.selectedSessionId === sessionId ? null : sessionId;
  }

  private rebuildView(): void {
    const recurring: SessionView[] = [];
    const open: SessionView[] = [];
    for (const session of this._sessions) {
      const view: SessionView = {
        session,
        participation: this.participation.getParticipation(session, this._currentUserId),
      };
      if (session.recurringTemplateId) recurring.push(view);
      else open.push(view);
    }
    this.vm = { nextGoal: this.computeNextGoal(), recurring, open };
  }

  private computeNextGoal(): ClubRaceGoalResponse | null {
    const now = Date.now();
    const future = this._raceGoals
      .filter((g) => !!g.raceDate && new Date(g.raceDate).getTime() > now)
      .sort((a, b) => new Date(a.raceDate!).getTime() - new Date(b.raceDate!).getTime());
    return future[0] ?? null;
  }
}

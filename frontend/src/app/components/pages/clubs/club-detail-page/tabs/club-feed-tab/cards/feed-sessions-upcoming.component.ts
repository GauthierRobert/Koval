import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ClubDetail, ClubRaceGoalResponse, ClubTrainingSession} from '../../../../../../../services/club.service';
import {SportIconComponent} from '../../../../../../shared/sport-icon/sport-icon.component';

@Component({
  selector: 'app-feed-sessions-upcoming',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Next Goal -->
    @if (nextGoal) {
      <div class="sidebar-panel goal-panel">
        <div class="goal-header">
          <svg class="goal-flag" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/>
            <line x1="4" y1="22" x2="4" y2="15"/>
          </svg>
          <span class="goal-label">{{ 'CLUB_FEED.NEXT_GOAL' | translate }}</span>
          <span class="goal-countdown">{{ getDaysUntil(nextGoal) }}{{ 'CLUB_FEED.DAYS' | translate }}</span>
        </div>
        <div class="goal-info">
          <app-sport-icon [sport]="toSport(nextGoal.sport)" [size]="14"></app-sport-icon>
          <span class="goal-title">{{ nextGoal.title }}</span>
        </div>
        @if (nextGoal.location) {
          <span class="goal-meta">{{ nextGoal.location }}</span>
        }
        <span class="goal-meta">{{ formatGoalDate(nextGoal.raceDate) }}</span>
        @if (nextGoal.participants.length > 0) {
          <div class="goal-athletes">
            <div class="avatar-stack">
              @for (a of nextGoal.participants.slice(0, 5); track a.userId) {
                <div class="avatar-circle" [title]="a.displayName">
                  @if (a.profilePicture) {
                    <img [src]="a.profilePicture" [alt]="a.displayName" />
                  } @else {
                    {{ a.displayName.charAt(0).toUpperCase() }}
                  }
                </div>
              }
            </div>
            <span class="athlete-count">{{ nextGoal.participants.length }} {{ 'CLUB_FEED.ATHLETES_ENGAGED' | translate }}</span>
          </div>
        }
      </div>
    }

    <!-- Recurring Sessions -->
    <div class="sidebar-panel">
      <div class="panel-header">
        <span class="panel-title">{{ 'CLUB_FEED.RECURRING_SESSIONS' | translate }}</span>
        <div class="week-nav">
          <button class="nav-btn" (click)="prevWeek()">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="week-label">{{ weekLabel }}</span>
          <button class="nav-btn" (click)="nextWeek()">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        </div>
      </div>

      @if (recurringSessions.length === 0) {
        <div class="empty-sessions">
          <span class="empty-text">{{ 'CLUB_FEED.NO_SESSIONS' | translate }}</span>
        </div>
      }

      @for (session of recurringSessions; track session.id) {
        <div class="session-card" [class.session-card--cancelled]="session.cancelled"
             [class.session-card--selected]="selectedSessionId === session.id"
             (click)="toggleSession(session)">
          <div class="session-row">
            <app-sport-icon [sport]="$any(session.sport ?? 'CYCLING')" [size]="14"></app-sport-icon>
            <div class="session-info">
              <span class="session-title">{{ session.title }}</span>
              <span class="session-time">{{ formatTime(session.scheduledAt) }}</span>
            </div>
            <div class="session-meta">
              <span class="participant-count">{{ session.participantIds.length }}@if (session.maxParticipants) {/{{ session.maxParticipants }}}</span>
              @if (isJoined(session)) {
                <span class="joined-pill">{{ 'CLUB_FEED.JOINED' | translate }}</span>
              } @else if (isOnWaitingList(session)) {
                <span class="waiting-pill">{{ 'CLUB_SESSIONS.WAITING_LIST_POSITION' | translate: { position: getWaitingListPosition(session) } }}</span>
              } @else if (isFull(session)) {
                <span class="full-pill">{{ 'CLUB_SESSIONS.BADGE_FULL' | translate }}</span>
              }
            </div>
          </div>

          @if (selectedSessionId === session.id) {
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
                  @if (!isJoined(session) && !isOnWaitingList(session)) {
                    <button class="btn-primary small" (click)="joinClicked.emit(session.id); $event.stopPropagation()">
                      {{ isFull(session) ? ('CLUB_SESSIONS.BTN_JOIN_WAITING_LIST' | translate) : ('CLUB_FEED.JOIN' | translate) }}
                    </button>
                  } @else if (isJoined(session)) {
                    <button class="btn-ghost small" (click)="cancelClicked.emit(session.id); $event.stopPropagation()">
                      {{ 'CLUB_FEED.CANCEL' | translate }}
                    </button>
                  } @else if (isOnWaitingList(session)) {
                    <button class="btn-ghost small" (click)="cancelClicked.emit(session.id); $event.stopPropagation()">
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
      }
    </div>

    <!-- Open Sessions -->
    @if (openSessions.length > 0) {
      <div class="sidebar-panel">
        <div class="panel-header">
          <span class="panel-title">{{ 'CLUB_FEED.OPEN_SESSIONS' | translate }}</span>
        </div>

        @for (session of openSessions; track session.id) {
          <div class="session-card" [class.session-card--cancelled]="session.cancelled"
               [class.session-card--selected]="selectedSessionId === session.id"
               (click)="toggleSession(session)">
            <div class="session-row">
              <app-sport-icon [sport]="$any(session.sport ?? 'CYCLING')" [size]="14"></app-sport-icon>
              <div class="session-info">
                <span class="session-title">{{ session.title }}</span>
                <span class="session-time">{{ formatTime(session.scheduledAt) }}</span>
              </div>
              <div class="session-meta">
                <span class="participant-count">{{ session.participantIds.length }}@if (session.maxParticipants) {/{{ session.maxParticipants }}}</span>
                @if (isJoined(session)) {
                  <span class="joined-pill">{{ 'CLUB_FEED.JOINED' | translate }}</span>
                } @else if (isOnWaitingList(session)) {
                  <span class="waiting-pill">{{ 'CLUB_SESSIONS.WAITING_LIST_POSITION' | translate: { position: getWaitingListPosition(session) } }}</span>
                } @else if (isFull(session)) {
                  <span class="full-pill">{{ 'CLUB_SESSIONS.BADGE_FULL' | translate }}</span>
                }
              </div>
            </div>

            @if (selectedSessionId === session.id) {
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
                    @if (!isJoined(session) && !isOnWaitingList(session)) {
                      <button class="btn-primary small" (click)="joinClicked.emit(session.id); $event.stopPropagation()">
                        {{ isFull(session) ? ('CLUB_SESSIONS.BTN_JOIN_WAITING_LIST' | translate) : ('CLUB_FEED.JOIN' | translate) }}
                      </button>
                    } @else if (isJoined(session)) {
                      <button class="btn-ghost small" (click)="cancelClicked.emit(session.id); $event.stopPropagation()">
                        {{ 'CLUB_FEED.CANCEL' | translate }}
                      </button>
                    } @else if (isOnWaitingList(session)) {
                      <button class="btn-ghost small" (click)="cancelClicked.emit(session.id); $event.stopPropagation()">
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
        }
      </div>
    }
  `,
  styles: `
    /* --- Sidebar panels --- */
    .sidebar-panel {
      display: flex;
      flex-direction: column;
      gap: var(--space-sm);
      background: var(--glass-bg);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }

    /* --- Goal panel --- */
    .goal-panel { border-left: 3px solid var(--warning, #f59e0b); }
    .goal-header { display: flex; align-items: center; gap: var(--space-sm); }
    .goal-flag { color: var(--warning, #f59e0b); flex-shrink: 0; }
    .goal-label { font-size: 9px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: var(--warning, #f59e0b); flex: 1; }
    .goal-countdown { font-size: var(--text-sm); font-weight: 700; color: var(--accent-color); }
    .goal-info { display: flex; align-items: center; gap: var(--space-xs); }
    .goal-title { font-size: var(--text-sm); font-weight: 600; color: var(--text-color); }
    .goal-meta { font-size: var(--text-xs); color: var(--text-muted); }
    .goal-athletes { display: flex; align-items: center; gap: var(--space-sm); margin-top: 2px; }
    .avatar-stack { display: flex; }
    .avatar-circle { width: 22px; height: 22px; border-radius: 50%; background: var(--surface-elevated); border: 2px solid var(--bg-color); display: flex; align-items: center; justify-content: center; font-size: 8px; font-weight: 600; color: var(--text-muted); margin-left: -6px; overflow: hidden; flex-shrink: 0; }
    .avatar-circle:first-child { margin-left: 0; }
    .avatar-circle img { width: 100%; height: 100%; object-fit: cover; }
    .athlete-count { font-size: var(--text-xs); color: var(--text-muted); }

    /* --- Panel header --- */
    .panel-header { display: flex; align-items: center; justify-content: space-between; }
    .panel-title { font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text-muted); }
    .week-nav { display: flex; align-items: center; gap: 4px; }
    .nav-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 2px; border-radius: 4px; display: flex; }
    .nav-btn:hover { background: var(--surface-elevated); color: var(--text-color); }
    .week-label { font-size: 10px; font-weight: 600; color: var(--text-muted); min-width: 80px; text-align: center; }

    /* --- Sessions --- */
    .empty-sessions { padding: var(--space-md) 0; text-align: center; }
    .empty-text { font-size: var(--text-xs); color: var(--text-muted); }
    .session-card { background: var(--surface-elevated); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 10px; cursor: pointer; transition: border-color 0.15s; }
    .session-card:hover { border-color: var(--primary); }
    .session-card--cancelled { opacity: 0.5; }
    .session-card--selected { border-color: var(--primary); background: rgba(255,157,0,0.05); }
    .session-row { display: flex; align-items: center; gap: var(--space-sm); }
    .session-info { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .session-title { font-size: 11px; font-weight: 600; color: var(--text-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .session-time { font-size: 9px; color: var(--text-muted); font-family: monospace; }
    .session-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
    .participant-count { font-size: 9px; color: var(--text-muted); font-family: monospace; }
    .joined-pill { font-size: 8px; font-weight: 700; text-transform: uppercase; padding: 1px 5px; border-radius: 3px; background: rgba(34,197,94,0.15); color: var(--success); }
    .waiting-pill { font-size: 8px; font-weight: 700; padding: 1px 5px; border-radius: 3px; background: rgba(255,157,0,0.15); color: var(--accent-color); }
    .full-pill { font-size: 8px; font-weight: 700; text-transform: uppercase; padding: 1px 5px; border-radius: 3px; background: rgba(239,68,68,0.15); color: var(--danger); }
    .session-detail { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--glass-border); display: flex; flex-direction: column; gap: 4px; }
    .detail-row { font-size: var(--text-xs); color: var(--text-muted); }
    .detail-desc { font-size: var(--text-xs); color: var(--text-color); margin: 0; line-height: 1.4; }
    .detail-actions { display: flex; gap: var(--space-sm); margin-top: var(--space-xs); }
    .cancelled-label { font-size: 10px; font-weight: 600; color: var(--danger); }
  `,
})
export class FeedSessionsUpcomingComponent {
  @Input() sessions: ClubTrainingSession[] = [];
  @Input() club!: ClubDetail;
  @Input() currentUserId: string | null = null;
  @Input() weekLabel = '';
  @Input() raceGoals: ClubRaceGoalResponse[] = [];
  @Output() joinClicked = new EventEmitter<string>();
  @Output() cancelClicked = new EventEmitter<string>();
  @Output() weekChange = new EventEmitter<number>();

  selectedSessionId: string | null = null;

  get nextGoal(): ClubRaceGoalResponse | null {
    const now = Date.now();
    const future = this.raceGoals
      .filter((g) => new Date(g.raceDate).getTime() > now)
      .sort((a, b) => new Date(a.raceDate).getTime() - new Date(b.raceDate).getTime());
    return future.length > 0 ? future[0] : null;
  }

  get recurringSessions(): ClubTrainingSession[] {
    return this.sessions.filter((s) => !!s.recurringTemplateId);
  }

  get openSessions(): ClubTrainingSession[] {
    return this.sessions.filter((s) => !s.recurringTemplateId);
  }

  toggleSession(session: ClubTrainingSession): void {
    this.selectedSessionId = this.selectedSessionId === session.id ? null : session.id;
  }

  isJoined(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && session.participantIds.includes(this.currentUserId);
  }

  isOnWaitingList(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && !!session.waitingList?.some((e) => e.userId === this.currentUserId);
  }

  getWaitingListPosition(session: ClubTrainingSession): number {
    if (!this.currentUserId || !session.waitingList) return 0;
    const idx = session.waitingList.findIndex((e) => e.userId === this.currentUserId);
    return idx >= 0 ? idx + 1 : 0;
  }

  isFull(session: ClubTrainingSession): boolean {
    return session.maxParticipants != null && session.participantIds.length >= session.maxParticipants;
  }

  prevWeek(): void {
    this.weekChange.emit(-1);
  }

  nextWeek(): void {
    this.weekChange.emit(1);
  }

  toSport(sport?: string): 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | 'GYM' {
    return (sport as any) || 'RUNNING';
  }

  getDaysUntil(goal: ClubRaceGoalResponse): number {
    const diff = new Date(goal.raceDate).getTime() - Date.now();
    return Math.max(0, Math.ceil(diff / 86400000));
  }

  formatGoalDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', {
      weekday: 'short', month: 'short', day: 'numeric',
    });
  }

  formatTime(dateStr?: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const day = d.toLocaleDateString('en-US', {weekday: 'short', month: 'short', day: 'numeric'});
    const time = d.toLocaleTimeString('en-US', {hour: '2-digit', minute: '2-digit'});
    return `${day} · ${time}`;
  }
}

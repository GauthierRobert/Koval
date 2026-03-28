import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ClubDetail, ClubTrainingSession} from '../../../../../../../services/club.service';
import {SportIconComponent} from '../../../../../../shared/sport-icon/sport-icon.component';

@Component({
  selector: 'app-feed-sessions-upcoming',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="upcoming-panel">
      <div class="panel-header">
        <span class="panel-title">{{ 'CLUB_FEED.UPCOMING_SESSIONS' | translate }}</span>
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

      @if (sessions.length === 0) {
        <div class="empty-sessions">
          <span class="empty-text">{{ 'CLUB_FEED.NO_SESSIONS' | translate }}</span>
        </div>
      }

      @for (session of sessions; track session.id) {
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
                  @if (!isJoined(session)) {
                    <button class="btn-join" (click)="joinClicked.emit(session.id); $event.stopPropagation()">
                      {{ 'CLUB_FEED.JOIN' | translate }}
                    </button>
                  } @else {
                    <button class="btn-leave" (click)="cancelClicked.emit(session.id); $event.stopPropagation()">
                      {{ 'CLUB_FEED.CANCEL' | translate }}
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
  `,
  styles: `
    .upcoming-panel { display: flex; flex-direction: column; gap: var(--space-sm); }
    .panel-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-xs); }
    .panel-title { font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text-muted); }
    .week-nav { display: flex; align-items: center; gap: 4px; }
    .nav-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 2px; border-radius: 4px; display: flex; }
    .nav-btn:hover { background: var(--surface-elevated); color: var(--text-color); }
    .week-label { font-size: 10px; font-weight: 600; color: var(--text-muted); min-width: 80px; text-align: center; }
    .empty-sessions { padding: var(--space-lg) var(--space-md); text-align: center; }
    .empty-text { font-size: var(--text-xs); color: var(--text-muted); }
    .session-card { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 10px; cursor: pointer; transition: border-color 0.15s; }
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
    .session-detail { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--glass-border); display: flex; flex-direction: column; gap: 4px; }
    .detail-row { font-size: var(--text-xs); color: var(--text-muted); }
    .detail-desc { font-size: var(--text-xs); color: var(--text-color); margin: 0; line-height: 1.4; }
    .detail-actions { display: flex; gap: var(--space-sm); margin-top: var(--space-xs); }
    .btn-join { padding: 4px 12px; border: none; border-radius: var(--radius-sm); background: var(--primary); color: #000; font-size: 10px; font-weight: 700; cursor: pointer; }
    .btn-leave { padding: 4px 12px; border: 1px solid var(--glass-border); border-radius: var(--radius-sm); background: transparent; color: var(--text-muted); font-size: 10px; font-weight: 600; cursor: pointer; }
    .btn-leave:hover { border-color: var(--danger); color: var(--danger); }
    .cancelled-label { font-size: 10px; font-weight: 600; color: var(--danger); }
  `,
})
export class FeedSessionsUpcomingComponent {
  @Input() sessions: ClubTrainingSession[] = [];
  @Input() club!: ClubDetail;
  @Input() currentUserId: string | null = null;
  @Input() weekLabel = '';
  @Output() joinClicked = new EventEmitter<string>();
  @Output() cancelClicked = new EventEmitter<string>();
  @Output() weekChange = new EventEmitter<number>(); // -1 or +1

  selectedSessionId: string | null = null;

  toggleSession(session: ClubTrainingSession): void {
    this.selectedSessionId = this.selectedSessionId === session.id ? null : session.id;
  }

  isJoined(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && session.participantIds.includes(this.currentUserId);
  }

  prevWeek(): void {
    this.weekChange.emit(-1);
  }

  nextWeek(): void {
    this.weekChange.emit(1);
  }

  formatTime(dateStr?: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const day = d.toLocaleDateString('en-US', {weekday: 'short', month: 'short', day: 'numeric'});
    const time = d.toLocaleTimeString('en-US', {hour: '2-digit', minute: '2-digit'});
    return `${day} · ${time}`;
  }
}

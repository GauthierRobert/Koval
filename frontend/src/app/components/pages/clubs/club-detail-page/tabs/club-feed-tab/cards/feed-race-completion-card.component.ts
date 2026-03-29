import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ClubFeedEventResponse} from '../../../../../../../services/club.service';

@Component({
  selector: 'app-feed-race-completion-card',
  standalone: true,
  imports: [CommonModule, TranslateModule],
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
  `,
})
export class FeedRaceCompletionCardComponent {
  @Input() event!: ClubFeedEventResponse;
  @Input() currentUserId: string | null = null;
  @Output() kudosRequested = new EventEmitter<string>();

  kudosLoading = false;

  get hasGivenKudos(): boolean {
    return !!this.currentUserId && (this.event.kudosGivenBy ?? []).includes(this.currentUserId);
  }

  onGiveKudos(): void {
    this.kudosLoading = true;
    this.kudosRequested.emit(this.event.id);
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-US', {month: 'long', day: 'numeric', year: 'numeric'});
  }
}

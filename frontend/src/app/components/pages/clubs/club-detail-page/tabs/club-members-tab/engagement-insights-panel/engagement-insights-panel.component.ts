import {ChangeDetectionStrategy, Component, inject, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {ClubFeedService} from '../../../../../../../services/club-feed.service';
import {EngagementInsightsResponse, MemberEngagement} from '../../../../../../../models/club.model';

type SortColumn = 'name' | 'comments' | 'reactions' | 'sessions' | 'lastActive';

@Component({
  selector: 'app-engagement-insights-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="insights-panel" data-testid="engagement-insights-panel">
      <header class="insights-header">
        <div class="insights-title">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="20" x2="12" y2="10"/>
            <line x1="18" y1="20" x2="18" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="16"/>
          </svg>
          <h3>{{ 'CLUB_MEMBERS.ENGAGEMENT_INSIGHTS' | translate }}</h3>
        </div>
        <div class="insights-controls">
          <select class="insights-select" [(ngModel)]="days" (change)="reload()">
            <option [value]="7">7d</option>
            <option [value]="30">30d</option>
            <option [value]="90">90d</option>
          </select>
        </div>
      </header>

      @if (insights$ | async; as insights) {
        <table class="insights-table">
          <thead>
            <tr>
              <th class="sortable" (click)="setSort('name')">
                {{ 'CLUB_MEMBERS.MEMBER' | translate }}{{ sortIndicator('name') }}
              </th>
              <th class="sortable num" (click)="setSort('comments')">
                {{ 'CLUB_MEMBERS.COMMENTS' | translate }}{{ sortIndicator('comments') }}
              </th>
              <th class="sortable num" (click)="setSort('reactions')">
                {{ 'CLUB_MEMBERS.REACTIONS' | translate }}{{ sortIndicator('reactions') }}
              </th>
              <th class="sortable num" (click)="setSort('sessions')">
                {{ 'CLUB_MEMBERS.SESSIONS' | translate }}{{ sortIndicator('sessions') }}
              </th>
              <th class="sortable" (click)="setSort('lastActive')">
                {{ 'CLUB_MEMBERS.LAST_ACTIVE' | translate }}{{ sortIndicator('lastActive') }}
              </th>
            </tr>
          </thead>
          <tbody>
            @for (m of sortedRows(insights); track m.userId) {
              <tr data-testid="insights-row" [class.dormant]="isDormant(m)">
                <td class="member-cell">
                  <span class="member-avatar">
                    @if (m.profilePicture) {
                      <img [src]="m.profilePicture" [alt]="m.displayName" />
                    } @else {
                      {{ m.displayName.charAt(0).toUpperCase() }}
                    }
                  </span>
                  <span class="member-name">{{ m.displayName }}</span>
                  @if (m.role !== 'MEMBER') {
                    <span class="role-pill" [attr.data-role]="m.role">{{ m.role }}</span>
                  }
                </td>
                <td class="num">{{ m.commentsPosted }}</td>
                <td class="num">{{ m.reactionsGiven }}</td>
                <td class="num">{{ m.sessionsCompleted }}</td>
                <td>{{ relativeTime(m.lastActiveAt) }}</td>
              </tr>
            }
          </tbody>
        </table>
        @if (!insights.members.length) {
          <p class="insights-empty">{{ 'CLUB_MEMBERS.NO_INSIGHTS' | translate }}</p>
        }
      } @else {
        <p class="insights-empty">{{ 'COMMON.LOADING' | translate }}â€¦</p>
      }
    </section>
  `,
  styles: `
    .insights-panel { background: var(--glass-bg); border: none; border-radius: var(--radius-md); padding: var(--space-md); margin-bottom: var(--space-md); }
    .insights-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-sm); }
    .insights-title { display: flex; align-items: center; gap: 8px; color: var(--text-color); }
    .insights-title h3 { font-size: var(--text-sm); font-weight: 700; margin: 0; text-transform: uppercase; letter-spacing: 0.06em; }
    .insights-select { background: var(--surface-elevated); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); padding: 4px 8px; font-size: var(--text-xs); color: var(--text-color); }
    .insights-table { width: 100%; border-collapse: collapse; font-size: var(--text-xs); }
    .insights-table th { text-align: left; padding: 6px 8px; font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; color: var(--text-muted); border-bottom: 1px solid var(--glass-border); user-select: none; }
    .insights-table th.num, .insights-table td.num { text-align: right; font-variant-numeric: tabular-nums; }
    .insights-table th.sortable { cursor: pointer; }
    .insights-table th.sortable:hover { color: var(--text-color); }
    .insights-table td { padding: 8px; border-bottom: 1px solid var(--glass-border); color: var(--text-color); }
    .insights-table tr.dormant td { color: var(--text-muted); }
    .member-cell { display: flex; align-items: center; gap: 8px; }
    .member-avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--surface-elevated); display: inline-flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 600; color: var(--text-muted); overflow: hidden; flex-shrink: 0; }
    .member-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .member-name { font-weight: 500; }
    .role-pill { font-size: 8px; font-weight: 700; padding: 2px 6px; border-radius: 999px; background: var(--surface-elevated); color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
    .role-pill[data-role="OWNER"] { background: var(--accent-subtle); color: var(--dev-accent-color); }
    .role-pill[data-role="ADMIN"] { background: var(--accent-subtle); color: var(--accent-color); }
    .role-pill[data-role="COACH"] { background: var(--success-subtle); color: var(--success-color); }
    .insights-empty { font-size: var(--text-xs); color: var(--text-muted); padding: var(--space-sm); margin: 0; }
  `,
})
export class EngagementInsightsPanelComponent implements OnInit {
  @Input({required: true}) clubId!: string;

  private feedService = inject(ClubFeedService);

  insights$ = this.feedService.engagementInsights$;
  days = 30;
  sortBy: SortColumn = 'comments';
  sortDir: 'asc' | 'desc' = 'desc';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.feedService.loadEngagementInsights(this.clubId, this.days);
  }

  setSort(col: SortColumn): void {
    if (this.sortBy === col) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortBy = col;
      this.sortDir = col === 'name' ? 'asc' : 'desc';
    }
  }

  sortIndicator(col: SortColumn): string {
    if (this.sortBy !== col) return '';
    return this.sortDir === 'asc' ? ' â–²' : ' â–¼';
  }

  sortedRows(insights: EngagementInsightsResponse): MemberEngagement[] {
    const rows = [...(insights.members ?? [])];
    const dir = this.sortDir === 'asc' ? 1 : -1;
    rows.sort((a, b) => {
      switch (this.sortBy) {
        case 'name':
          return dir * a.displayName.localeCompare(b.displayName);
        case 'comments':
          return dir * (a.commentsPosted - b.commentsPosted);
        case 'reactions':
          return dir * (a.reactionsGiven - b.reactionsGiven);
        case 'sessions':
          return dir * (a.sessionsCompleted - b.sessionsCompleted);
        case 'lastActive': {
          const ta = a.lastActiveAt ? new Date(a.lastActiveAt).getTime() : 0;
          const tb = b.lastActiveAt ? new Date(b.lastActiveAt).getTime() : 0;
          return dir * (ta - tb);
        }
      }
    });
    return rows;
  }

  isDormant(m: MemberEngagement): boolean {
    return m.commentsPosted === 0 && m.reactionsGiven === 0 && m.sessionsCompleted === 0;
  }

  relativeTime(dateStr: string | undefined): string {
    if (!dateStr) return 'â€”';
    const diff = Date.now() - new Date(dateStr).getTime();
    const days = Math.floor(diff / 86400000);
    if (days === 0) return 'today';
    if (days === 1) return '1d ago';
    if (days < 30) return `${days}d ago`;
    return `${Math.floor(days / 30)}mo ago`;
  }
}

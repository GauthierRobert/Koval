import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ClubService, ClubExtendedStats} from '../../../../../../services/club.service';
import {SportIconComponent} from '../../../../../shared/sport-icon/sport-icon.component';

@Component({
  selector: 'app-club-stats-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  templateUrl: './club-stats-tab.component.html',
  styleUrl: './club-stats-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubStatsTabComponent {
  private clubService = inject(ClubService);
  extendedStats$ = this.clubService.extendedStats$;

  formatHours(h: number): string {
    if (h < 1) return `${Math.round(h * 60)}m`;
    return `${Math.floor(h)}h${Math.round((h % 1) * 60) > 0 ? Math.round((h % 1) * 60) + 'm' : ''}`;
  }

  formatDate(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, {weekday: 'short', day: 'numeric', month: 'short'});
  }

  getAttendanceColor(rate: number): string {
    if (rate >= 0.75) return 'var(--success-color, #34d399)';
    if (rate >= 0.5) return 'var(--accent-color, #ff9d00)';
    return 'var(--danger-color, #ef4444)';
  }

  getMaxTrend(trends: ClubExtendedStats['weeklyTrends'], key: 'totalTss' | 'totalHours' | 'sessionCount' | 'attendanceRate'): number {
    if (!trends || trends.length === 0) return 1;
    const max = Math.max(...trends.map(t => t[key]));
    return max > 0 ? max : 1;
  }

  sportEntries(dist: Record<string, number>): {sport: string; pct: number}[] {
    if (!dist) return [];
    return Object.entries(dist).map(([sport, pct]) => ({sport, pct}));
  }
}

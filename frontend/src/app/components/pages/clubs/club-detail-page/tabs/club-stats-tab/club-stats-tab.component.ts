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
  expandedTemplates = new Set<string>();

  toggleExpand(templateId: string): void {
    if (this.expandedTemplates.has(templateId)) {
      this.expandedTemplates.delete(templateId);
    } else {
      this.expandedTemplates.add(templateId);
    }
  }

  isExpanded(templateId: string): boolean {
    return this.expandedTemplates.has(templateId);
  }

  formatHours(h: number): string {
    if (h < 1) return `${Math.round(h * 60)}m`;
    return `${Math.floor(h)}h${Math.round((h % 1) * 60) > 0 ? Math.round((h % 1) * 60) + 'm' : ''}`;
  }

  getAttendanceColor(rate: number): string {
    if (rate >= 0.75) return 'var(--success-color, #34d399)';
    if (rate >= 0.5) return 'var(--accent-color, #ff9d00)';
    return 'var(--danger-color, #ef4444)';
  }

  getFillColor(pct: number): string {
    return this.getAttendanceColor(pct / 100);
  }

  getMaxTrend(trends: ClubExtendedStats['weeklyTrends'], key: 'totalTss' | 'totalHours' | 'sessionCount' | 'attendanceRate'): number {
    if (!trends || trends.length === 0) return 1;
    const max = Math.max(...trends.map(t => t[key]));
    return max > 0 ? max : 1;
  }

  sportEntries(dist: Record<string, number>): {sport: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | 'GYM'; pct: number}[] {
    if (!dist) return [];
    return Object.entries(dist).map(([sport, pct]) => ({sport: sport as 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK' | 'GYM', pct}));
  }

  formatDay(day: string): string {
    if (!day) return '';
    return day.charAt(0) + day.slice(1).toLowerCase();
  }

  formatTime(time: string): string {
    if (!time) return '';
    return time.substring(0, 5);
  }
}

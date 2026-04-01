import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {formatTrainingDuration} from '../../../shared/format/format.utils';
import {SportStats, WeekMetrics} from '../dashboard.component';

@Component({
  selector: 'app-dashboard-sport-cards',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './dashboard-sport-cards.component.html',
  styleUrl: './dashboard-sport-cards.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardSportCardsComponent {
  @Input() weekMetrics: WeekMetrics | null = null;

  formatDuration(s: number): string {
    return formatTrainingDuration(s);
  }

  formatDistance(meters: number, sport: string): string {
    if (meters <= 0) return '';
    if (sport === 'SWIMMING') return `${Math.round(meters)}m`;
    return `${(meters / 1000).toFixed(1)} km`;
  }

  getTrend(curr: SportStats, prev: SportStats[]): '▲' | '▼' | '–' {
    const prevStats = prev.find((p) => p.sport === curr.sport);
    if (!prevStats || prevStats.tss === 0) return '–';
    if (curr.tss > prevStats.tss) return '▲';
    if (curr.tss < prevStats.tss) return '▼';
    return '–';
  }

  getTrendClass(curr: SportStats, prev: SportStats[]): string {
    const trend = this.getTrend(curr, prev);
    if (trend === '▲') return 'trend-up';
    if (trend === '▼') return 'trend-down';
    return '';
  }

  totalWeekTss(metrics: WeekMetrics): number {
    return Math.round(metrics.current.reduce((a, s) => a + s.tss, 0));
  }

  totalPrevTss(metrics: WeekMetrics): number {
    return Math.round(metrics.previous.reduce((a, s) => a + s.tss, 0));
  }

  hasAnyActivity(metrics: WeekMetrics): boolean {
    return metrics.current.some((s) => s.sessionCount > 0);
  }

  trackBySport(stat: SportStats): string {
    return stat.sport;
  }
}

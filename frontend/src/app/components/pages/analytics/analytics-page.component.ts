import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AnalyticsService, DURATION_LABELS } from '../../../services/analytics.service';
import { PowerCurveChartComponent } from '../session-analysis/power-curve-chart/power-curve-chart.component';
import { DashboardVolumeChartComponent } from '../dashboard/dashboard-volume-chart/dashboard-volume-chart.component';

type Tab = 'power-curve' | 'volume' | 'records';
type VolumeMetric = 'time' | 'tss' | 'distance';

@Component({
  selector: 'app-analytics-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, PowerCurveChartComponent, DashboardVolumeChartComponent],
  templateUrl: './analytics-page.component.html',
  styleUrl: './analytics-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsPageComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  powerCurve$ = this.analyticsService.powerCurve$;
  volume$ = this.analyticsService.volume$;
  personalRecords$ = this.analyticsService.personalRecords$;
  loading$ = this.analyticsService.loading$;

  activeTab: Tab = 'power-curve';
  dateFrom = '';
  dateTo = '';
  volumeGroupBy: 'week' | 'month' = 'week';
  volumeMetric: VolumeMetric = 'time';

  readonly DURATION_LABELS = DURATION_LABELS;

  ngOnInit(): void {
    // Default: last 90 days
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - 90);
    this.dateTo = to.toISOString().split('T')[0];
    this.dateFrom = from.toISOString().split('T')[0];

    this.loadData();
    this.analyticsService.loadPersonalRecords();
  }

  setTab(tab: Tab): void {
    this.activeTab = tab;
  }

  loadData(): void {
    this.analyticsService.loadPowerCurve(this.dateFrom, this.dateTo);
    this.analyticsService.loadVolume(this.dateFrom, this.dateTo, this.volumeGroupBy);
  }

  // Power curve helpers
  powerCurveEntries(data: Record<number, number>): { duration: number; label: string; power: number }[] {
    return Object.entries(data)
      .map(([dur, power]) => ({
        duration: Number(dur),
        label: DURATION_LABELS[Number(dur)] || `${dur}s`,
        power: Number(power),
      }))
      .sort((a, b) => a.duration - b.duration);
  }

  maxPower(data: Record<number, number>): number {
    const vals = Object.values(data);
    return vals.length > 0 ? Math.max(...vals.map(Number)) : 1;
  }

  barHeight(power: number, max: number): number {
    return max > 0 ? (power / max) * 100 : 0;
  }

  formatDuration(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  }

  formatDistance(meters: number): string {
    if (meters >= 1000) return `${(meters / 1000).toFixed(1)} km`;
    return `${Math.round(meters)} m`;
  }

  // Records helpers
  recordEntries(data: Record<number, number>): { duration: number; label: string; power: number }[] {
    return this.powerCurveEntries(data);
  }

  // FRI helpers
  computeFri(
    data: Record<number, number>,
  ): { ratio: number; power5min: number; power60min: number; level: string; color: string } | null {
    const p5 = data[300];
    const p60 = data[3600];
    if (!p5 || !p60 || p5 <= 0 || p60 <= 0) return null;
    // Variability guard: reject flat curves (Z2-only riding)
    if (p5 / p60 < 1.2) return null;
    const ratio = Math.round((p60 / p5) * 1000) / 1000;
    let level: string;
    let color: string;
    if (ratio >= 0.8) {
      level = 'excellent';
      color = 'var(--success-color)';
    } else if (ratio >= 0.75) {
      level = 'good';
      color = 'var(--success-color)';
    } else if (ratio >= 0.7) {
      level = 'moderate';
      color = 'oklch(0.75 0.16 75)';
    } else {
      level = 'developing';
      color = 'var(--danger-color)';
    }
    return { ratio, power5min: Math.round(p5), power60min: Math.round(p60), level, color };
  }
}

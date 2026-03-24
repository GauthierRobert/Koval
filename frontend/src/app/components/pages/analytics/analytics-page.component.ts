import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AnalyticsService, DURATION_LABELS, VolumeEntry } from '../../../services/analytics.service';

type Tab = 'power-curve' | 'volume' | 'records';

@Component({
  selector: 'app-analytics-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
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

  // Volume helpers
  maxVolumeTss(entries: VolumeEntry[]): number {
    return entries.length > 0 ? Math.max(...entries.map((e) => e.totalTss)) : 1;
  }

  volumeBarHeight(tss: number, max: number): number {
    return max > 0 ? (tss / max) * 100 : 0;
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
}

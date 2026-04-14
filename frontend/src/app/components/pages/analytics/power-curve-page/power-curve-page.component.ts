import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AnalyticsService, DURATION_LABELS } from '../../../../services/analytics.service';
import { PowerCurveChartComponent } from '../../session-analysis/power-curve-chart/power-curve-chart.component';

@Component({
  selector: 'app-power-curve-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, PowerCurveChartComponent],
  templateUrl: './power-curve-page.component.html',
  styleUrl: './power-curve-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PowerCurvePageComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  powerCurve$ = this.analyticsService.powerCurve$;
  loading$ = this.analyticsService.loading$;

  dateFrom = '';
  dateTo = '';

  readonly DURATION_LABELS = DURATION_LABELS;

  ngOnInit(): void {
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - 90);
    this.dateTo = to.toISOString().split('T')[0];
    this.dateFrom = from.toISOString().split('T')[0];
    this.loadData();
  }

  loadData(): void {
    this.analyticsService.loadPowerCurve(this.dateFrom, this.dateTo);
  }

  powerCurveEntries(data: Record<number, number>): { duration: number; label: string; power: number }[] {
    return Object.entries(data)
      .map(([dur, power]) => ({
        duration: Number(dur),
        label: DURATION_LABELS[Number(dur)] || `${dur}s`,
        power: Math.round(Number(power)),
      }))
      .sort((a, b) => a.duration - b.duration);
  }

  computeFri(
    data: Record<number, number>,
  ): { ratio: number; power5min: number; power60min: number; level: string; color: string } | null {
    const p5 = data[300];
    const p60 = data[3600];
    if (!p5 || !p60 || p5 <= 0 || p60 <= 0) return null;
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

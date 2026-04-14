import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AnalyticsService } from '../../../../services/analytics.service';
import { DashboardVolumeChartComponent } from '../../dashboard/dashboard-volume-chart/dashboard-volume-chart.component';

type VolumeMetric = 'time' | 'tss' | 'distance';

@Component({
  selector: 'app-volume-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, DashboardVolumeChartComponent],
  templateUrl: './volume-page.component.html',
  styleUrl: './volume-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VolumePageComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  volume$ = this.analyticsService.volume$;
  loading$ = this.analyticsService.loading$;

  dateFrom = '';
  dateTo = '';
  volumeGroupBy: 'week' | 'month' = 'week';
  volumeMetric: VolumeMetric = 'time';

  ngOnInit(): void {
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - 90);
    this.dateTo = to.toISOString().split('T')[0];
    this.dateFrom = from.toISOString().split('T')[0];
    this.loadData();
  }

  loadData(): void {
    this.analyticsService.loadVolume(this.dateFrom, this.dateTo, this.volumeGroupBy);
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
}

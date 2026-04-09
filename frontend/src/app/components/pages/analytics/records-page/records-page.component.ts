import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AnalyticsService, DURATION_LABELS } from '../../../../services/analytics.service';

@Component({
  selector: 'app-records-page',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './records-page.component.html',
  styleUrl: './records-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecordsPageComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  personalRecords$ = this.analyticsService.personalRecords$;
  loading$ = this.analyticsService.loading$;

  ngOnInit(): void {
    this.analyticsService.loadPersonalRecords();
  }

  recordEntries(data: Record<number, number>): { duration: number; label: string; power: number }[] {
    return Object.entries(data)
      .map(([dur, power]) => ({
        duration: Number(dur),
        label: DURATION_LABELS[Number(dur)] || `${dur}s`,
        power: Math.round(Number(power)),
      }))
      .sort((a, b) => a.duration - b.duration);
  }
}

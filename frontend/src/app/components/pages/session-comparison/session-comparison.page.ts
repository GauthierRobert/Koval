import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay, switchMap } from 'rxjs/operators';
import {
  ComparisonReport,
  SessionComparisonService,
} from '../../../services/session-comparison.service';
import { SessionPickerModalComponent } from '../../shared/session-picker-modal/session-picker-modal.component';
import { SavedSession } from '../../../services/history.service';
import { ComparisonMetricsTableComponent } from './comparison-metrics-table/comparison-metrics-table.component';
import { ComparisonPowerCurveComponent } from './comparison-power-curve/comparison-power-curve.component';
import { ComparisonZoneSidebysideComponent } from './comparison-zone-sidebyside/comparison-zone-sidebyside.component';
import { ComparisonBlockAlignmentComponent } from './comparison-block-alignment/comparison-block-alignment.component';

interface PageState {
  loading: boolean;
  error: boolean;
  report: ComparisonReport | null;
}

/** Standalone route at `/sessions/compare?ids=a,b,c[,d]`. First id is the reference. */
@Component({
  selector: 'app-session-comparison-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    TranslateModule,
    SessionPickerModalComponent,
    ComparisonMetricsTableComponent,
    ComparisonPowerCurveComponent,
    ComparisonZoneSidebysideComponent,
    ComparisonBlockAlignmentComponent,
  ],
  templateUrl: './session-comparison.page.html',
  styleUrl: './session-comparison.page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionComparisonPageComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(SessionComparisonService);

  /** Anchor used by the "Add" picker — built lazily from the first session of the report. */
  pickerAnchor: SavedSession | null = null;

  state$: Observable<PageState> = this.route.queryParamMap.pipe(
    map((params) => (params.get('ids') ?? '').split(',').filter(Boolean)),
    switchMap((ids) => {
      if (ids.length < 2) return of<PageState>({ loading: false, error: true, report: null });
      return this.service.compare(ids).pipe(
        map((report) => ({ loading: false, error: false, report })),
        catchError(() => of<PageState>({ loading: false, error: true, report: null })),
      );
    }),
    shareReplay(1),
  );

  private static readonly PALETTE = ['#22c55e', '#3b82f6', '#f59e0b', '#ec4899'];

  colorsArray(report: ComparisonReport): string[] {
    return report.sessions.map(
      (_, i) =>
        SessionComparisonPageComponent.PALETTE[i % SessionComparisonPageComponent.PALETTE.length],
    );
  }

  openPicker(report: ComparisonReport): void {
    const first = report.sessions[0];
    this.pickerAnchor = {
      id: first.id,
      title: first.title,
      date: new Date(first.completedAt),
      sportType: report.sportType as SavedSession['sportType'],
      totalDuration: first.totalDurationSeconds,
      avgPower: first.avgPower ?? 0,
      avgHR: first.avgHR ?? 0,
      avgCadence: first.avgCadence ?? 0,
      avgSpeed: first.avgSpeed ?? 0,
      blockSummaries: [],
      history: [],
      syncedToStrava: false,
      syncedToGarmin: false,
    };
  }

  onPickerApply(currentReport: ComparisonReport, picked: string[]): void {
    const currentIds = currentReport.sessions.map((s) => s.id);
    const merged = Array.from(new Set([...currentIds, ...picked])).slice(0, 4);
    this.pickerAnchor = null;
    this.router.navigate(['/sessions/compare'], { queryParams: { ids: merged.join(',') } });
  }

  closePicker(): void {
    this.pickerAnchor = null;
  }
}

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, combineLatest, of } from 'rxjs';
import { catchError, map, shareReplay, switchMap } from 'rxjs/operators';
import {
  ComparisonReport,
  SessionComparisonService,
} from '../../../services/session-comparison.service';
import { SessionPickerModalComponent } from '../../shared/session-picker-modal/session-picker-modal.component';
import { SavedSession } from '../../../services/history.service';
import { ComparisonPowerCurveComponent } from './comparison-power-curve/comparison-power-curve.component';
import { ComparisonMetricsRadarComponent } from './comparison-metrics-radar/comparison-metrics-radar.component';
import { ComparisonMetricsTableComponent } from './comparison-metrics-table/comparison-metrics-table.component';
import { ComparisonEfficiencyScatterComponent } from './comparison-efficiency-scatter/comparison-efficiency-scatter.component';
import { ComparisonBlockAlignmentComponent } from './comparison-block-alignment/comparison-block-alignment.component';
import {
  ColumnMetrics,
  ComparisonColumnComponent,
} from './comparison-column/comparison-column.component';
import {
  ZoneChip,
  ZoneFilterChipsComponent,
} from '../../shared/zone-filter-chips/zone-filter-chips.component';
import {
  MetricToggleKey,
  MetricTogglesComponent,
} from '../session-analysis/metric-toggles/metric-toggles.component';
import { AuthService } from '../../../services/auth.service';
import { ZoneService } from '../../../services/zone.service';
import { ZoneClassificationService } from '../../../services/zone-classification.service';
import { SportType, ZoneSystem } from '../../../services/zone';

interface PageState {
  loading: boolean;
  error: boolean;
  report: ComparisonReport | null;
}

@Component({
  selector: 'app-session-comparison-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    TranslateModule,
    SessionPickerModalComponent,
    ComparisonPowerCurveComponent,
    ComparisonMetricsRadarComponent,
    ComparisonMetricsTableComponent,
    ComparisonEfficiencyScatterComponent,
    ComparisonBlockAlignmentComponent,
    ComparisonColumnComponent,
    ZoneFilterChipsComponent,
    MetricTogglesComponent,
  ],
  templateUrl: './session-comparison.page.html',
  styleUrl: './session-comparison.page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionComparisonPageComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(SessionComparisonService);
  private authService = inject(AuthService);
  private zoneService = inject(ZoneService);
  private zoneCls = inject(ZoneClassificationService);

  pickerAnchor: SavedSession | null = null;
  readonly emptyFilters: Set<string> = new Set();

  /** Shared zone-filter state across all columns. */
  zoneFilters$ = new BehaviorSubject<Set<string>>(new Set());

  /** Shared chart-display controls across all columns. */
  showPrimary$ = new BehaviorSubject<boolean>(true);
  showSpeed$ = new BehaviorSubject<boolean>(true);
  showHR$ = new BehaviorSubject<boolean>(true);
  showCadence$ = new BehaviorSubject<boolean>(false);
  blockView$ = new BehaviorSubject<'planned' | 'interpolated'>('interpolated');
  smoothFactor$ = new BehaviorSubject<number>(10);

  togglePrimary(): void {
    this.showPrimary$.next(!this.showPrimary$.value);
  }
  toggleSpeed(): void {
    this.showSpeed$.next(!this.showSpeed$.value);
  }
  toggleHR(): void {
    this.showHR$.next(!this.showHR$.value);
  }
  toggleCadence(): void {
    this.showCadence$.next(!this.showCadence$.value);
  }
  /** Map the shared metric-toggle strip's intents onto the comparison display state. */
  toggleMetric(key: MetricToggleKey): void {
    switch (key) {
      case 'showPrimary':
        this.togglePrimary();
        break;
      case 'showSpeed':
        this.toggleSpeed();
        break;
      case 'showHR':
        this.toggleHR();
        break;
      case 'showCadence':
        this.toggleCadence();
        break;
      case 'showDrift':
        break; // cardiac drift isn't shown in comparison columns
    }
  }
  setBlockView(v: 'planned' | 'interpolated'): void {
    this.blockView$.next(v);
  }
  setSmooth(v: number): void {
    this.smoothFactor$.next(v);
  }

  state$: Observable<PageState> = this.route.queryParamMap.pipe(
    map((params) => (params.get('ids') ?? '').split(',').filter(Boolean)),
    switchMap((ids) => {
      if (ids.length < 2) return of<PageState>({ loading: false, error: true, report: null });
      this.zoneFilters$.next(new Set());
      this.columnMetrics$.next(new Map());
      return this.service.compare(ids).pipe(
        map((report) => ({ loading: false, error: false, report })),
        catchError(() => of<PageState>({ loading: false, error: true, report: null })),
      );
    }),
    shareReplay(1),
  );

  /** Per-session scoped metrics fed back from each comparison-column. The bar
   * comparison panel reads from this so its values follow the active brush
   * selection and zone filter on each column. */
  private columnMetrics$ = new BehaviorSubject<Map<string, ColumnMetrics>>(new Map());

  metricsOverrides$: Observable<(ColumnMetrics | null)[]> = combineLatest([
    this.state$,
    this.columnMetrics$,
  ]).pipe(
    map(([state, metrics]) => {
      if (!state.report) return [];
      return state.report.sessions.map((s) => metrics.get(s.id) ?? null);
    }),
    shareReplay(1),
  );

  onColumnMetrics(sessionId: string, metrics: ColumnMetrics): void {
    const next = new Map(this.columnMetrics$.value);
    next.set(sessionId, metrics);
    this.columnMetrics$.next(next);
  }

  /** Zone chips for the active sport — derived from the user's zones for the report sport. */
  zoneChips$: Observable<ZoneChip[]> = this.state$.pipe(
    switchMap((state) => {
      if (!state.report?.sportType) return of<ZoneChip[]>([]);
      const sport = state.report.sportType as SportType;
      return combineLatest([
        this.authService.user$,
        this.zoneService.getMyZoneSystems().pipe(catchError(() => of<ZoneSystem[]>([]))),
      ]).pipe(
        map(([user, systems]) => {
          const filtered = systems.filter((s) => s.sportType === sport);
          const resolved = this.zoneCls.resolveZonesAndReference(sport, user, null, filtered);
          if (!resolved) return [];
          return resolved.zones.map((z, i) => ({
            label: z.label,
            color: this.zoneCls.getZoneColor(i, resolved.zones, sport),
          }));
        }),
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

  toggleZone(label: string): void {
    const current = new Set(this.zoneFilters$.value);
    if (current.has(label)) current.delete(label);
    else current.add(label);
    this.zoneFilters$.next(current);
  }

  clearZones(): void {
    this.zoneFilters$.next(new Set());
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

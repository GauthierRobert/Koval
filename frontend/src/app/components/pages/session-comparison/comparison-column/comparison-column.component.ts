import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, Observable, asyncScheduler, combineLatest, from, of } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  observeOn,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import { FitRecord, MetricsService } from '../../../../services/metrics.service';
import { AuthService } from '../../../../services/auth.service';
import { ZoneService } from '../../../../services/zone.service';
import { ZoneSystem, SportType, ZoneBlock, Zone } from '../../../../services/zone';
import { ZoneClassificationService } from '../../../../services/zone-classification.service';
import { ZoneInterpolationService } from '../../../../services/zone-interpolation.service';
import { FitTimeseriesChartComponent } from '../../session-analysis/fit-timeseries-chart/fit-timeseries-chart.component';
import { BlockSummary } from '../../../../services/workout-execution.service';
import { ComparisonSessionEntry } from '../../../../services/session-comparison.service';
import { stripPauses } from '../../session-analysis/session-analysis.utils';

export interface ColumnMetrics {
  durationSec: number;
  avgPower: number;
  normalizedPower: number;
  intensityFactor: number;
  tss: number;
  avgHR: number;
  avgCadence: number;
  avgSpeedKmh: number;
  distanceMeters: number;
  scope: 'full' | 'selection' | 'zone' | 'zone-selection';
  scopeLabel: string;
}

interface LoadState {
  loading: boolean;
  error: boolean;
  records: FitRecord[];
}

@Component({
  selector: 'app-comparison-column',
  standalone: true,
  imports: [CommonModule, TranslateModule, FitTimeseriesChartComponent],
  templateUrl: './comparison-column.component.html',
  styleUrl: './comparison-column.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonColumnComponent implements OnChanges {
  @Input({ required: true }) session!: ComparisonSessionEntry;
  @Input({ required: true }) sportType!: string;
  @Input() color = 'var(--accent-color, #ff9d00)';
  @Input() set zoneFilters(v: Set<string>) {
    this.zoneFilters$.next(v ?? new Set());
  }
  @Input() set blockMode(v: 'raw' | 'laps' | 'interpolated') {
    this.blockMode$.next(v ?? 'interpolated');
  }
  @Input() set smoothFactor(v: number) {
    this.smoothFactor$.next(v ?? 10);
  }
  @Input() showPrimary = true;
  @Input() showSpeed = true;
  @Input() showHR = true;
  @Input() showCadence = false;
  @Input() showDrift = false;

  /** Stored lap/planned blocks for this session, used by the Laps overlay mode. */
  displayBlocks: BlockSummary[] = [];

  @Output() metricsChange = new EventEmitter<ColumnMetrics>();

  readonly emptyFilters: Set<string> = new Set();

  private metricsService = inject(MetricsService);
  private authService = inject(AuthService);
  private zoneService = inject(ZoneService);
  private zoneCls = inject(ZoneClassificationService);
  private zoneInterp = inject(ZoneInterpolationService);

  private sessionId$ = new BehaviorSubject<string>('');
  zoneFilters$ = new BehaviorSubject<Set<string>>(new Set());
  selection$ = new BehaviorSubject<{ startIdx: number; endIdx: number } | null>(null);
  smoothFactor$ = new BehaviorSubject<number>(10);
  blockMode$ = new BehaviorSubject<'raw' | 'laps' | 'interpolated'>('interpolated');

  fitState$: Observable<LoadState> = this.sessionId$.pipe(
    distinctUntilChanged(),
    switchMap((id) => {
      if (!id) return of<LoadState>({ loading: false, error: false, records: [] });
      return this.metricsService.downloadStoredFit(id).pipe(
        switchMap((buffer) => from(this.metricsService.parseFitFile(buffer))),
        map(
          (result): LoadState => ({
            loading: false,
            error: false,
            records: stripPauses(result.records, result.timerEvents).records,
          }),
        ),
        catchError(() => of<LoadState>({ loading: false, error: true, records: [] })),
      );
    }),
    shareReplay(1),
  );

  private userZoneSystems$: Observable<ZoneSystem[]> = this.zoneService.getMyZoneSystems().pipe(
    map((systems) => systems.filter((s) => s.sportType === this.sportType)),
    catchError(() => of<ZoneSystem[]>([])),
    shareReplay(1),
  );

  private resolvedZones$: Observable<{ zones: Zone[]; referenceValue: number } | null> =
    combineLatest([this.authService.user$, this.userZoneSystems$]).pipe(
      map(([user, systems]) =>
        this.zoneCls.resolveZonesAndReference(this.sportType as SportType, user, null, systems),
      ),
      shareReplay(1),
    );

  zoneBlocks$: Observable<ZoneBlock[]> = combineLatest([
    this.fitState$,
    this.resolvedZones$,
    this.smoothFactor$,
  ]).pipe(
    map(([fit, resolved, smoothFactor]) => {
      if (fit.loading || fit.error || !fit.records.length || !resolved) return [];
      if (this.sportType === 'SWIMMING') return [];
      return this.zoneInterp.computeZoneBlocks(
        fit.records,
        resolved.zones,
        resolved.referenceValue,
        this.sportType as SportType,
        smoothFactor,
      );
    }),
    shareReplay(1),
  );

  metrics$: Observable<ColumnMetrics> = combineLatest([
    this.fitState$,
    this.selection$,
    this.zoneFilters$,
    this.resolvedZones$,
  ]).pipe(
    map(([fit, selection, filters, resolved]) =>
      this.computeMetrics(fit.records, selection, filters, resolved),
    ),
    shareReplay(1),
  );

  constructor() {
    this.metrics$
      .pipe(observeOn(asyncScheduler), takeUntilDestroyed())
      .subscribe((m) => this.metricsChange.emit(m));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['session']) {
      this.selection$.next(null);
      this.sessionId$.next(this.session.id);
      // ComparisonBlockSummary widens distanceMeters to number | null; normalize to
      // the chart's BlockSummary shape (number | undefined) for the Laps overlay.
      this.displayBlocks = (this.session.blockSummaries ?? []).map((b) => ({
        ...b,
        distanceMeters: b.distanceMeters ?? undefined,
      }));
    }
  }

  onSelectionChange(sel: { startIdx: number; endIdx: number } | null): void {
    this.selection$.next(sel);
  }

  private computeMetrics(
    records: FitRecord[],
    selection: { startIdx: number; endIdx: number } | null,
    filters: Set<string>,
    resolved: { zones: Zone[]; referenceValue: number } | null,
  ): ColumnMetrics {
    const filterActive = filters.size > 0 && !!resolved;
    const hasSelection = selection !== null;
    const fallback: ColumnMetrics = {
      durationSec: this.session.totalDurationSeconds,
      avgPower: this.session.avgPower ?? 0,
      normalizedPower: this.session.normalizedPower ?? this.session.avgPower ?? 0,
      intensityFactor: this.session.intensityFactor ?? 0,
      tss: this.session.tss ?? 0,
      avgHR: this.session.avgHR ?? 0,
      avgCadence: this.session.avgCadence ?? 0,
      avgSpeedKmh: (this.session.avgSpeed ?? 0) * 3.6,
      distanceMeters: this.session.totalDistance ?? 0,
      scope: 'full',
      scopeLabel: '',
    };
    if (!records.length || (!filterActive && !hasSelection)) return fallback;

    const lo = hasSelection ? selection!.startIdx : 0;
    const hi = hasSelection ? selection!.endIdx : records.length - 1;

    let dur = 0;
    let pSum = 0,
      pN = 0;
    let hrSum = 0,
      hrN = 0;
    let cadSum = 0,
      cadN = 0;
    let spSum = 0,
      spN = 0;
    let dist = 0;
    const powers: number[] = [];

    for (let i = lo; i <= hi; i++) {
      const r = records[i];
      if (filterActive) {
        const zi = this.zoneCls.classifyRecord(
          r.power,
          r.speed,
          this.sportType as SportType,
          resolved!.referenceValue,
          resolved!.zones,
        );
        const label =
          zi === this.zoneCls.WALKING_ZONE_INDEX
            ? this.zoneCls.WALKING_LABEL
            : this.zoneCls.getZoneLabel(zi, resolved!.zones);
        if (!filters.has(label)) continue;
      }
      const dt = i + 1 < records.length ? Math.min(records[i + 1].timestamp - r.timestamp, 30) : 1;
      if (dt <= 0) continue;
      dur += dt;
      if (r.power > 0) {
        pSum += r.power * dt;
        pN += dt;
        powers.push(r.power);
      }
      if (r.heartRate > 0) {
        hrSum += r.heartRate * dt;
        hrN += dt;
      }
      if (r.cadence > 0) {
        cadSum += r.cadence * dt;
        cadN += dt;
      }
      if (r.speed > 0) {
        spSum += r.speed * dt;
        spN += dt;
        dist += r.speed * dt;
      }
    }

    const scope: ColumnMetrics['scope'] =
      hasSelection && filterActive ? 'zone-selection' : hasSelection ? 'selection' : 'zone';
    const scopeLabel = filterActive
      ? Array.from(filters).join(', ')
      : hasSelection
        ? 'selection'
        : '';

    if (dur <= 0) {
      return { ...fallback, scope, scopeLabel };
    }

    let np = 0;
    if (powers.length >= 30) {
      const win = 30;
      let s = 0;
      const ma: number[] = [];
      for (let i = 0; i < powers.length; i++) {
        s += powers[i];
        if (i >= win) s -= powers[i - win];
        if (i >= win - 1) ma.push(s / win);
      }
      const sum4 = ma.reduce((acc, v) => acc + Math.pow(v, 4), 0);
      np = Math.pow(sum4 / ma.length, 0.25);
    }

    const ftp =
      this.session.intensityFactor && this.session.normalizedPower
        ? this.session.normalizedPower / this.session.intensityFactor
        : 0;
    const scopedIf = ftp > 0 && np > 0 ? np / ftp : 0;
    const scopedTss = scopedIf > 0 ? (dur / 3600) * scopedIf * scopedIf * 100 : 0;

    return {
      durationSec: dur,
      avgPower: pN > 0 ? pSum / pN : 0,
      normalizedPower: np,
      intensityFactor: scopedIf,
      tss: scopedTss,
      avgHR: hrN > 0 ? hrSum / hrN : 0,
      avgCadence: cadN > 0 ? cadSum / cadN : 0,
      avgSpeedKmh: spN > 0 ? (spSum / spN) * 3.6 : 0,
      distanceMeters: dist,
      scope,
      scopeLabel,
    };
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  }
}

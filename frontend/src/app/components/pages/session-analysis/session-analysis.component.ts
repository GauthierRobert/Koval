import {ChangeDetectionStrategy, Component, inject, Input, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest, from, Observable, of, Subject} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged, map, shareReplay, startWith, switchMap} from 'rxjs/operators';
import {HistoryService, SavedSession} from '../../../services/history.service';
import {AuthService} from '../../../services/auth.service';
import {FitLap, FitRecord, FitTimerEvent, MetricsService} from '../../../services/metrics.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneBlock, ZoneSystem, SportType} from '../../../services/zone';
import {ZoneClassificationService} from '../../../services/zone-classification.service';
import {ZoneInterpolationService} from '../../../services/zone-interpolation.service';
import {BlockSummary} from '../../../services/workout-execution.service';
import {formatTimeHMS} from '../../shared/format/format.utils';
import {FitTimeseriesChartComponent} from './fit-timeseries-chart/fit-timeseries-chart.component';
import {SessionStatsHeaderComponent} from './session-stats-header/session-stats-header.component';
import {ZoneDistributionPanelComponent, ZoneDistEntry} from './zone-distribution-panel/zone-distribution-panel.component';
import {BlockBreakdownTableComponent} from './block-breakdown-table/block-breakdown-table.component';
import {PowerCurveChartComponent} from './power-curve-chart/power-curve-chart.component';
import {DecouplingGaugeComponent} from './decoupling-gauge/decoupling-gauge.component';
import {CadenceDistributionPanelComponent} from './cadence-distribution-panel/cadence-distribution-panel.component';
import {ClimbsPanelComponent} from './climbs-panel/climbs-panel.component';
import {WPrimeBalanceChartComponent} from './wprime-balance-chart/wprime-balance-chart.component';
import {ChartPanelSkeletonComponent} from '../../shared/skeleton/chart-panel-skeleton/chart-panel-skeleton.component';
import {
    formatBlockDistance,
    formatLongDate,
    formatSpeed,
    formatZoneDistance,
    formatZoneDuration,
    lapsToBlockSummaries,
    stripPauses,
    syntheticBarHeightPct,
    syntheticBarWidthPct,
    syntheticMaxPower,
    syntheticTotalDuration,
} from './session-analysis.utils';

interface FitState {
    loading: boolean;
    error: boolean;
    records: FitRecord[];
    laps: FitLap[];
    movingTime: number;
}

@Component({
    selector: 'app-session-analysis',
    standalone: true,
    imports: [CommonModule, TranslateModule, FitTimeseriesChartComponent, SessionStatsHeaderComponent, ZoneDistributionPanelComponent, BlockBreakdownTableComponent, PowerCurveChartComponent, DecouplingGaugeComponent, CadenceDistributionPanelComponent, ClimbsPanelComponent, WPrimeBalanceChartComponent, ChartPanelSkeletonComponent],
    templateUrl: './session-analysis.component.html',
    styleUrl: './session-analysis.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionAnalysisComponent implements OnDestroy {
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private historyService = inject(HistoryService);
    private zoneService = inject(ZoneService);
    private zoneCls = inject(ZoneClassificationService);
    private zoneInterp = inject(ZoneInterpolationService);

    private sessionSubject = new BehaviorSubject<SavedSession | null>(null);
    private rpeUpdate$ = new Subject<{id: string; rpe: number}>();
    selectedZoneSystemId$ = new BehaviorSubject<string | null>(null);

    constructor() {
        this.rpeUpdate$.pipe(
            debounceTime(400),
            switchMap(({id, rpe}) => this.historyService.updateSession(id, {rpe}))
        ).subscribe();
    }

    ngOnDestroy(): void {
        this.rpeUpdate$.complete();
    }

    @Input() set session(s: SavedSession | null) {
        this.sessionSubject.next(s ?? null);
        this.selectedZoneSystemId$.next(null);
        if (s?.sportType === 'SWIMMING') {
            this.blockView = 'planned';
        }
    }

    session$ = this.sessionSubject.asObservable();
    ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? null));
    criticalPower$ = this.authService.user$.pipe(map((u) => u?.criticalPower ?? null));
    wPrimeJ$ = this.authService.user$.pipe(map((u) => u?.wPrimeJ ?? null));

    fitState$: Observable<FitState> = this.sessionSubject.pipe(
        distinctUntilChanged((a, b) => a?.fitFileId === b?.fitFileId),
        switchMap((session) => {
            if (!session?.fitFileId) {
                return of({loading: false, error: false, records: [] as FitRecord[], laps: [] as FitLap[], movingTime: 0});
            }
            return this.metricsService.downloadStoredFit(session.id).pipe(
                switchMap((buffer) => from(this.metricsService.parseFitFile(buffer))),
                map((result) => {
                    const stripped = stripPauses(result.records, result.timerEvents);
                    // Prefer session total_timer_time from FIT; fallback to stripped calculation
                    const movingTime = result.totalTimerTime > 0 ? result.totalTimerTime : stripped.movingTime;
                    return {loading: false, error: false, records: stripped.records, laps: result.laps, movingTime};
                }),
                catchError(() => of({loading: false, error: true, records: [] as FitRecord[], laps: [] as FitLap[], movingTime: 0})),
                startWith({loading: true, error: false, records: [] as FitRecord[], laps: [] as FitLap[], movingTime: 0}),
            );
        }),
        shareReplay(1),
    );

    movingTime$: Observable<number | null> = combineLatest([
        this.fitState$,
        this.sessionSubject,
    ]).pipe(
        map(([fit, session]) => {
            if (!fit || fit.loading || fit.error || fit.records.length === 0 || !session) return null;
            if (Math.abs(fit.movingTime - session.totalDuration) < 5) return null;
            return fit.movingTime;
        }),
    );

    userZoneSystems$: Observable<ZoneSystem[]> = combineLatest([
        this.zoneService.getMyZoneSystems().pipe(catchError(() => of([] as ZoneSystem[]))),
        this.sessionSubject,
    ]).pipe(
        map(([systems, session]) => {
            if (!session?.sportType) return [];
            return systems.filter((s) => s.sportType === session.sportType);
        }),
        shareReplay(1),
    );

    blockView: 'planned' | 'interpolated' = 'interpolated';
    smoothFactor$ = new BehaviorSubject<number>(10);
    zoneFilters$ = new BehaviorSubject<Set<string>>(new Set());

    // ── Zone distribution ────────────────────────────────────────────────

    zoneDistribution$: Observable<ZoneDistEntry[]> = combineLatest([
        this.fitState$,
        this.authService.user$,
        this.sessionSubject,
        this.selectedZoneSystemId$,
        this.userZoneSystems$,
    ]).pipe(
        map(([fit, user, session, selectedId, userSystems]) => {
            if (!fit || fit.loading || fit.error || fit.records.length === 0 || !session?.sportType) return [];

            const sport = session.sportType as SportType;
            const resolved = this.zoneCls.resolveZonesAndReference(sport, user, selectedId, userSystems);
            if (!resolved) return [];
            const {zones, referenceValue} = resolved;

            const zoneSeconds = new Array(zones.length).fill(0);
            let walkingSeconds = 0;
            let totalSeconds = 0;
            const records = fit.records;

            for (let i = 0; i < records.length; i++) {
                const record = records[i];
                if (sport !== 'CYCLING' && record.speed <= 0) continue;

                // Time this record represents: gap to next record, or 1s for the last
                const dt = i + 1 < records.length
                    ? Math.min(records[i + 1].timestamp - record.timestamp, 30)
                    : 1;
                if (dt <= 0) continue;

                const zi = this.zoneCls.classifyRecord(record.power, record.speed, sport, referenceValue, zones);
                if (zi === this.zoneCls.WALKING_ZONE_INDEX) {
                    walkingSeconds += dt;
                } else {
                    zoneSeconds[zi] += dt;
                }
                totalSeconds += dt;
            }

            if (totalSeconds === 0) return [];

            const result: ZoneDistEntry[] = zones.map((z, i) => ({
                label: z.label,
                description: z.description ?? '',
                color: this.zoneCls.getZoneColor(i, zones, sport),
                seconds: Math.round(zoneSeconds[i]),
                percentage: Math.round((zoneSeconds[i] / totalSeconds) * 100),
            }));

            if (walkingSeconds > 0) {
                result.unshift({
                    label: this.zoneCls.WALKING_LABEL,
                    description: this.zoneCls.WALKING_DESCRIPTION,
                    color: this.zoneCls.WALKING_COLOR,
                    seconds: Math.round(walkingSeconds),
                    percentage: Math.round((walkingSeconds / totalSeconds) * 100),
                });
            }

            return result;
        }),
    );

    // ── Zone blocks (interpolated) ───────────────────────────────────────

    zoneBlocks$: Observable<ZoneBlock[]> = combineLatest([
        this.fitState$,
        this.authService.user$,
        this.sessionSubject,
        this.selectedZoneSystemId$,
        this.userZoneSystems$,
        this.smoothFactor$,
    ]).pipe(
        map(([fit, user, session, selectedId, userSystems, smoothFactor]) => {
            if (!fit || fit.loading || fit.error || !fit.records.length || !session?.sportType) return [];
            // Pool swim FIT records have no meaningful speed — interpolated blocks are noise.
            if (session.sportType === 'SWIMMING') return [];
            const sport = session.sportType as SportType;
            const resolved = this.zoneCls.resolveZonesAndReference(sport, user, selectedId, userSystems);
            if (!resolved) return [];
            return this.zoneInterp.computeZoneBlocks(fit.records, resolved.zones, resolved.referenceValue, sport, smoothFactor);
        }),
    );

    // ── Display blocks ───────────────────────────────────────────────────
    // For pool swim, derive blocks from FIT laps (0m lap = REST, >0m = INTERVAL).
    // For other sports, use the stored blockSummaries.
    displayBlocks$: Observable<BlockSummary[]> = combineLatest([
        this.sessionSubject,
        this.fitState$,
    ]).pipe(
        map(([session, fit]) => {
            if (!session) return [];
            if (session.sportType === 'SWIMMING' && fit.laps.length > 0) {
                return lapsToBlockSummaries(fit.laps);
            }
            return session.blockSummaries ?? [];
        }),
        shareReplay(1),
    );

    // ── Planned blocks with zone overlay ─────────────────────────────────

    plannedBlocksWithZones$: Observable<(BlockSummary & {zoneLabel?: string; zoneColor?: string; actualSpeedKmh?: number})[]> = combineLatest([
        this.displayBlocks$,
        this.sessionSubject,
        this.authService.user$,
        this.selectedZoneSystemId$,
        this.userZoneSystems$,
    ]).pipe(
        map(([blocks, session, user, selectedId, userSystems]) => {
            if (!blocks.length || !session) return [];
            const sport = session.sportType as SportType;
            const resolved = this.zoneCls.resolveZonesAndReference(sport, user, selectedId, userSystems);
            return blocks.map(block => {
                const isCycling = sport === 'CYCLING';
                const speedMs = (!isCycling && block.distanceMeters && block.durationSeconds > 0)
                    ? block.distanceMeters / block.durationSeconds : 0;
                const actualSpeedKmh = speedMs * 3.6;
                const val = isCycling ? block.actualPower : speedMs;
                if (!val || !resolved) return {...block, actualSpeedKmh: isCycling ? undefined : actualSpeedKmh};
                const pct = (val / resolved.referenceValue) * 100;
                const zi = this.zoneCls.classifyZone(pct, resolved.zones);
                return {
                    ...block,
                    zoneLabel: this.zoneCls.getZoneLabel(zi, resolved.zones),
                    zoneColor: this.zoneCls.getZoneColor(zi, resolved.zones, sport),
                    actualSpeedKmh: isCycling ? undefined : actualSpeedKmh,
                };
            });
        }),
    );

    blockColors$: Observable<string[]> = this.plannedBlocksWithZones$.pipe(
        map(blocks => blocks.map(b => b.zoneColor ?? '')),
    );

    // ── Zone filter chips ────────────────────────────────────────────────

    availableZoneChips$: Observable<{label: string; color: string}[]> = this.zoneDistribution$.pipe(
        map(zones => zones.filter(z => z.seconds > 0).map(z => ({label: z.label, color: z.color}))),
    );

    filteredZoneBlocks$: Observable<ZoneBlock[]> = combineLatest([this.zoneBlocks$, this.zoneFilters$]).pipe(
        map(([blocks, filters]) => filters.size === 0 ? blocks : blocks.filter(b => filters.has(b.zoneLabel))),
    );

    filteredPlannedBlocks$: Observable<(BlockSummary & {zoneLabel?: string; zoneColor?: string; actualSpeedKmh?: number})[]> =
        combineLatest([this.plannedBlocksWithZones$, this.zoneFilters$]).pipe(
            map(([blocks, filters]) => filters.size === 0 ? blocks : blocks.filter(b => b.zoneLabel != null && filters.has(b.zoneLabel))),
        );

    toggleZoneFilter(label: string): void {
        const current = new Set(this.zoneFilters$.value);
        if (current.has(label)) {
            current.delete(label);
        } else {
            current.add(label);
        }
        this.zoneFilters$.next(current);
    }

    isZoneActive(label: string): boolean {
        const filters = this.zoneFilters$.value;
        return filters.size === 0 || filters.has(label);
    }

    clearZoneFilters(): void {
        this.zoneFilters$.next(new Set());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    onZoneSystemChange(id: string | null): void {
        this.selectedZoneSystemId$.next(id || null);
    }

    formatZoneDuration = formatZoneDuration;
    formatZoneDistance = formatZoneDistance;

    getTss(session: SavedSession, ftp: number | null): number | null {
        if (session.tss != null && session.tss > 0) return Math.round(session.tss);
        if (session.rpe != null && session.rpe > 0) {
            return Math.round(this.metricsService.computeTssFromRpe(session.totalDuration, session.rpe));
        }
        if (!ftp) return null;
        return Math.round(this.metricsService.computeTss(session.totalDuration, session.avgPower, ftp));
    }

    rpeValues = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

    selectRpe(session: SavedSession, val: number) {
        const updated = {...session, rpe: val};
        this.sessionSubject.next(updated);
        this.rpeUpdate$.next({id: session.id, rpe: val});
    }

    getIF(session: SavedSession, ftp: number | null): number | null {
        if (session.intensityFactor != null) return session.intensityFactor;
        if (!ftp) return null;
        return this.metricsService.computeIF(session.avgPower, ftp);
    }

    formatTime(seconds: number): string {
        return formatTimeHMS(seconds);
    }

    formatSpeed = formatSpeed;
    formatDistance = formatBlockDistance;
    formatDate = formatLongDate;
    syntheticMaxPower = syntheticMaxPower;
    syntheticTotalDuration = syntheticTotalDuration;
    syntheticBarWidthPct = syntheticBarWidthPct;
    syntheticBarHeightPct = syntheticBarHeightPct;

    syntheticBarColor(block: BlockSummary, ftp: number | null): string {
        const v = block.actualPower || block.targetPower || 0;
        if (!ftp || ftp <= 0) return 'var(--accent-color, #ff9d00)';
        const sport = (this.sessionSubject.value?.sportType as SportType | undefined) ?? 'CYCLING';
        return this.zoneCls.intensityToColor((v / ftp) * 100, sport);
    }
}

import {Component, inject, Input, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest, from, Observable, of, Subject} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged, map, shareReplay, startWith, switchMap} from 'rxjs/operators';
import {HistoryService, SavedSession} from '../../../services/history.service';
import {AuthService} from '../../../services/auth.service';
import {FitRecord, FitTimerEvent, MetricsService} from '../../../services/metrics.service';
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

interface FitState {
    loading: boolean;
    error: boolean;
    records: FitRecord[];
    movingTime: number;
}

@Component({
    selector: 'app-session-analysis',
    standalone: true,
    imports: [CommonModule, TranslateModule, FitTimeseriesChartComponent, SessionStatsHeaderComponent, ZoneDistributionPanelComponent, BlockBreakdownTableComponent, PowerCurveChartComponent, DecouplingGaugeComponent],
    templateUrl: './session-analysis.component.html',
    styleUrl: './session-analysis.component.css',
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
    }

    session$ = this.sessionSubject.asObservable();
    ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? null));

    fitState$: Observable<FitState> = this.sessionSubject.pipe(
        distinctUntilChanged((a, b) => a?.fitFileId === b?.fitFileId),
        switchMap((session) => {
            if (!session?.fitFileId) {
                return of({loading: false, error: false, records: [] as FitRecord[], movingTime: 0});
            }
            return this.metricsService.downloadStoredFit(session.id).pipe(
                switchMap((buffer) => from(this.metricsService.parseFitFile(buffer))),
                map((result) => {
                    const stripped = this.stripPauses(result.records, result.timerEvents);
                    // Prefer session total_timer_time from FIT; fallback to stripped calculation
                    const movingTime = result.totalTimerTime > 0 ? result.totalTimerTime : stripped.movingTime;
                    return {loading: false, error: false, records: stripped.records, movingTime};
                }),
                catchError(() => of({loading: false, error: true, records: [] as FitRecord[], movingTime: 0})),
                startWith({loading: true, error: false, records: [] as FitRecord[], movingTime: 0}),
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
                color: this.zoneCls.getZoneColor(i),
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
            const sport = session.sportType as SportType;
            const resolved = this.zoneCls.resolveZonesAndReference(sport, user, selectedId, userSystems);
            if (!resolved) return [];
            return this.zoneInterp.computeZoneBlocks(fit.records, resolved.zones, resolved.referenceValue, sport, smoothFactor);
        }),
    );

    // ── Planned blocks with zone overlay ─────────────────────────────────

    plannedBlocksWithZones$: Observable<(BlockSummary & {zoneLabel?: string; zoneColor?: string; actualSpeedKmh?: number})[]> = combineLatest([
        this.sessionSubject,
        this.authService.user$,
        this.selectedZoneSystemId$,
        this.userZoneSystems$,
    ]).pipe(
        map(([session, user, selectedId, userSystems]) => {
            if (!session?.blockSummaries?.length) return [];
            const sport = session.sportType as SportType;
            const resolved = this.zoneCls.resolveZonesAndReference(sport, user, selectedId, userSystems);
            return session.blockSummaries.map(block => {
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
                    zoneColor: this.zoneCls.getZoneColor(zi),
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

    /**
     * Strip paused periods from records using FIT timer events (stop/start pairs).
     * Falls back to gap-based detection if no timer events are available.
     */
    private stripPauses(records: FitRecord[], timerEvents: FitTimerEvent[]): {records: FitRecord[]; movingTime: number} {
        if (records.length < 2) return {records, movingTime: 0};

        // Build pause ranges from timer stop→start pairs
        const pauseRanges: {start: number; end: number}[] = [];
        for (let i = 0; i < timerEvents.length; i++) {
            if (timerEvents[i].type === 'stop') {
                // Find the next 'start' event
                for (let j = i + 1; j < timerEvents.length; j++) {
                    if (timerEvents[j].type === 'start') {
                        pauseRanges.push({start: timerEvents[i].timestamp, end: timerEvents[j].timestamp});
                        i = j; // skip to after the start event
                        break;
                    }
                }
            }
        }

        if (pauseRanges.length === 0) {
            // No timer events — fall back to gap-based detection
            return this.stripPausesByGap(records);
        }

        // Pre-compute cumulative pause durations for efficient lookup
        const cumulativePause = new Array<number>(pauseRanges.length);
        let cumPause = 0;
        for (let i = 0; i < pauseRanges.length; i++) {
            cumPause += pauseRanges[i].end - pauseRanges[i].start;
            cumulativePause[i] = cumPause;
        }

        // Filter out records within pause ranges & adjust timestamps
        const result: FitRecord[] = [];
        let pauseIdx = 0;

        for (const record of records) {
            // Advance past pause ranges that ended before this record
            while (pauseIdx < pauseRanges.length && pauseRanges[pauseIdx].end <= record.timestamp) {
                pauseIdx++;
            }

            // Check if this record falls within a pause range
            if (pauseIdx < pauseRanges.length && record.timestamp >= pauseRanges[pauseIdx].start && record.timestamp < pauseRanges[pauseIdx].end) {
                continue; // Skip records during pauses
            }

            // Total pause time before this record = sum of all completed pause ranges
            const pauseBefore = pauseIdx > 0 ? cumulativePause[pauseIdx - 1] : 0;
            result.push({...record, timestamp: record.timestamp - pauseBefore});
        }

        const movingTime = result.length >= 2
            ? result[result.length - 1].timestamp - result[0].timestamp
            : 0;
        return {records: result, movingTime};
    }

    private stripPausesByGap(records: FitRecord[]): {records: FitRecord[]; movingTime: number} {
        const PAUSE_THRESHOLD = 20;
        const result: FitRecord[] = [records[0]];
        let adjustedTime = records[0].timestamp;
        for (let i = 1; i < records.length; i++) {
            const gap = records[i].timestamp - records[i - 1].timestamp;
            adjustedTime += gap > PAUSE_THRESHOLD ? 1 : gap;
            result.push({...records[i], timestamp: adjustedTime});
        }
        const movingTime = result[result.length - 1].timestamp - result[0].timestamp;
        return {records: result, movingTime};
    }

    onZoneSystemChange(id: string | null): void {
        this.selectedZoneSystemId$.next(id || null);
    }

    formatZoneDuration(seconds: number): string {
        if (seconds >= 3600) {
            const h = Math.floor(seconds / 3600);
            const m = Math.floor((seconds % 3600) / 60);
            return `${h}h ${m}m`;
        }
        if (seconds >= 60) {
            const m = Math.floor(seconds / 60);
            const s = seconds % 60;
            return `${m}m ${s}s`;
        }
        return `${seconds}s`;
    }

    formatZoneDistance(meters: number): string {
        if (meters == null || meters <= 0) return '—';
        if (meters >= 1000) return (meters / 1000).toFixed(2) + ' km';
        return Math.round(meters) + ' m';
    }

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

    formatSpeed(speedMs: number, sportType: string): string {
        if (!speedMs || speedMs <= 0) return '—';
        if (sportType === 'SWIMMING') {
            const secPer100 = 100 / speedMs;
            const m = Math.floor(secPer100 / 60);
            const s = Math.round(secPer100 % 60);
            return `${m}:${String(s).padStart(2, '0')} /100m`;
        }
        const secPerKm = 1000 / speedMs;
        const m = Math.floor(secPerKm / 60);
        const s = Math.round(secPerKm % 60);
        return `${m}:${String(s).padStart(2, '0')} /km`;
    }

    formatDistance(block: BlockSummary): string {
        const m = block.distanceMeters;
        if (m == null || m <= 0) return '—';
        if (m >= 1000) return (m / 1000).toFixed(2) + ' km';
        return Math.round(m) + ' m';
    }

    formatDate(date: Date): string {
        return new Date(date).toLocaleDateString('en-US', {
            weekday: 'long',
            month: 'long',
            day: 'numeric',
            year: 'numeric',
        });
    }
}

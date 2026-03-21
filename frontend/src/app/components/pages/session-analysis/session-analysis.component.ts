import {Component, inject, Input, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject, combineLatest, from, Observable, of, Subject} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged, map, shareReplay, startWith, switchMap} from 'rxjs/operators';
import {HistoryService, SavedSession} from '../../../services/history.service';
import {AuthService, User} from '../../../services/auth.service';
import {FitRecord, MetricsService} from '../../../services/metrics.service';
import {ZoneService} from '../../../services/zone.service';
import {Zone, ZoneSystem, SportType} from '../../../services/zone';
import {BlockSummary} from '../../../services/workout-execution.service';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {formatTimeHMS} from '../../shared/format/format.utils';
import {FitTimeseriesChartComponent} from './fit-timeseries-chart/fit-timeseries-chart.component';

interface FitState {
    loading: boolean;
    error: boolean;
    records: FitRecord[];
    movingTime: number; // seconds with pauses stripped
}

interface ZoneDistEntry {
    label: string;
    description: string;
    color: string;
    seconds: number;
    percentage: number;
}

export interface ZoneBlock {
    zoneIndex: number;
    zoneLabel: string;
    zoneDescription: string;
    color: string;
    startIndex: number;
    endIndex: number;
    durationSeconds: number;
    avgPower: number;
    maxPower: number;
    avgSpeed: number;
    maxSpeed: number;
    avgHR: number;
    avgCadence: number;
    avgPercent: number;
}

@Component({
    selector: 'app-session-analysis',
    standalone: true,
    imports: [CommonModule, SportIconComponent, FitTimeseriesChartComponent],
    templateUrl: './session-analysis.component.html',
    styleUrl: './session-analysis.component.css',
})
export class SessionAnalysisComponent implements OnDestroy {
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private historyService = inject(HistoryService);
    private zoneService = inject(ZoneService);

    private sessionSubject = new BehaviorSubject<SavedSession | null>(null);
    private rpeUpdate$ = new Subject<{ id: string; rpe: number }>();
    selectedZoneSystemId$ = new BehaviorSubject<string | null>(null);

    private readonly ZONE_COLORS = ['#b2bec3', '#3498db', '#2ecc71', '#f1c40f', '#e67e22', '#e74c3c', '#c0392b'];

    private readonly defaultZonesBySport: Record<SportType, Zone[]> = {
        CYCLING: [
            { label: 'Z1', low: 0, high: 55, description: 'Active Recovery' },
            { label: 'Z2', low: 55, high: 75, description: 'Endurance' },
            { label: 'Z3', low: 75, high: 90, description: 'Tempo' },
            { label: 'Z4', low: 90, high: 105, description: 'Threshold' },
            { label: 'Z5', low: 105, high: 120, description: 'VO2max' },
            { label: 'Z6', low: 120, high: 150, description: 'Anaerobic' },
            { label: 'Z7', low: 150, high: 300, description: 'Neuromuscular' },
        ],
        RUNNING: [
            { label: 'Z1', low: 0, high: 75, description: 'Easy' },
            { label: 'Z2', low: 75, high: 85, description: 'Aerobic' },
            { label: 'Z3', low: 85, high: 95, description: 'Tempo' },
            { label: 'Z4', low: 95, high: 105, description: 'Threshold' },
            { label: 'Z5', low: 105, high: 120, description: 'VO2max' },
        ],
        SWIMMING: [
            { label: 'Z1', low: 0, high: 80, description: 'Recovery' },
            { label: 'Z2', low: 80, high: 90, description: 'Endurance' },
            { label: 'Z3', low: 90, high: 100, description: 'Threshold' },
            { label: 'Z4', low: 100, high: 110, description: 'VO2max' },
            { label: 'Z5', low: 110, high: 130, description: 'Sprint' },
        ],
    };

    constructor() {
        this.rpeUpdate$.pipe(
            debounceTime(400),
            switchMap(({ id, rpe }) => this.historyService.updateSession(id, { rpe }))
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
                return of({ loading: false, error: false, records: [] as FitRecord[], movingTime: 0 });
            }
            return this.metricsService.downloadStoredFit(session.id).pipe(
                switchMap((buffer) => from(this.metricsService.parseFitTimeSeries(buffer))),
                map((records) => {
                    const stripped = this.stripPauses(records);
                    return { loading: false, error: false, records: stripped.records, movingTime: stripped.movingTime };
                }),
                catchError(() => of({ loading: false, error: true, records: [] as FitRecord[], movingTime: 0 })),
                startWith({ loading: true, error: false, records: [] as FitRecord[], movingTime: 0 }),
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
            // Only show if meaningfully different from total duration (>5s difference = pauses exist)
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

    blockView: 'planned' | 'zones' = 'zones';
    smoothFactor$ = new BehaviorSubject<number>(10);

    private resolveZonesAndReference(
        sport: SportType, user: User | null, selectedId: string | null, userSystems: ZoneSystem[],
    ): { zones: Zone[]; referenceValue: number } | null {
        let zones: Zone[];
        if (selectedId) {
            const custom = userSystems.find((s) => s.id === selectedId);
            zones = custom ? custom.zones : this.defaultZonesBySport[sport];
        } else {
            zones = this.defaultZonesBySport[sport];
        }

        let referenceValue: number | null = null;
        if (sport === 'CYCLING') {
            referenceValue = user?.ftp ?? null;
        } else if (sport === 'RUNNING') {
            const ftpPace = user?.functionalThresholdPace;
            referenceValue = ftpPace ? 1000 / ftpPace : null;
        } else if (sport === 'SWIMMING') {
            const css = user?.criticalSwimSpeed;
            referenceValue = css ? 100 / css : null;
        }

        if (!referenceValue || referenceValue <= 0) return null;
        return { zones, referenceValue };
    }

    private stripPauses(records: FitRecord[]): { records: FitRecord[]; movingTime: number } {
        if (records.length < 2) return { records, movingTime: 0 };
        const PAUSE_THRESHOLD = 3; // gap >3s between records = pause
        const result: FitRecord[] = [records[0]];
        let adjustedTime = records[0].timestamp;
        for (let i = 1; i < records.length; i++) {
            const gap = records[i].timestamp - records[i - 1].timestamp;
            adjustedTime += gap > PAUSE_THRESHOLD ? 1 : gap;
            result.push({ ...records[i], timestamp: adjustedTime });
        }
        const movingTime = result[result.length - 1].timestamp - result[0].timestamp;
        return { records: result, movingTime };
    }

    private recordPercent(record: FitRecord, sport: SportType, referenceValue: number): number {
        if (sport === 'CYCLING') return (record.power / referenceValue) * 100;
        return (record.speed / referenceValue) * 100;
    }

    private classifyZone(percent: number, zones: Zone[]): number {
        for (let i = 0; i < zones.length; i++) {
            if (percent >= zones[i].low && percent <= zones[i].high) return i;
        }
        if (percent > zones[zones.length - 1].high) return zones.length - 1;
        // Value fell in a gap between zones — find nearest boundary
        let bestIdx = 0;
        let bestDist = Infinity;
        for (let i = 0; i < zones.length; i++) {
            const dist = Math.min(Math.abs(percent - zones[i].low), Math.abs(percent - zones[i].high));
            if (dist < bestDist) { bestDist = dist; bestIdx = i; }
        }
        return bestIdx;
    }

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
            const resolved = this.resolveZonesAndReference(sport, user, selectedId, userSystems);
            if (!resolved) return [];
            const { zones, referenceValue } = resolved;

            const zoneCounts = new Array(zones.length).fill(0);
            let totalCounted = 0;

            for (const record of fit.records) {
                if (sport !== 'CYCLING' && record.speed <= 0) continue;
                const percent = this.recordPercent(record, sport, referenceValue);
                zoneCounts[this.classifyZone(percent, zones)]++;
                totalCounted++;
            }

            if (totalCounted === 0) return [];

            return zones.map((z, i) => ({
                label: z.label,
                description: z.description ?? '',
                color: this.ZONE_COLORS[i] ?? this.ZONE_COLORS[this.ZONE_COLORS.length - 1],
                seconds: zoneCounts[i],
                percentage: Math.round((zoneCounts[i] / totalCounted) * 100),
            }));
        }),
    );

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
            const resolved = this.resolveZonesAndReference(sport, user, selectedId, userSystems);
            if (!resolved) return [];
            return this.computeZoneBlocks(fit.records, resolved.zones, resolved.referenceValue, sport, smoothFactor);
        }),
    );

    private computeZoneBlocks(records: FitRecord[], zones: Zone[], referenceValue: number, sport: SportType, smoothFactor: number): ZoneBlock[] {
        const n = records.length;
        if (n === 0) return [];

        // Pass 1 — raw classification
        const rawZones = new Array<number>(n);
        const percents = new Array<number>(n);
        for (let i = 0; i < n; i++) {
            const pct = this.recordPercent(records[i], sport, referenceValue);
            percents[i] = pct;
            rawZones[i] = this.classifyZone(pct, zones);
        }

        // Pass 2 — median filter (window = 2 * smoothFactor + 1)
        const half = smoothFactor;
        const smoothed = new Array<number>(n);
        const buf: number[] = [];
        for (let i = 0; i < n; i++) {
            const lo = Math.max(0, i - half);
            const hi = Math.min(n - 1, i + half);
            buf.length = 0;
            for (let j = lo; j <= hi; j++) buf.push(rawZones[j]);
            buf.sort((a, b) => a - b);
            smoothed[i] = buf[buf.length >> 1];
        }

        // Pass 3 — group consecutive same-zone runs
        const minBlockSec = Math.max(5, smoothFactor * 2);
        let blocks = this.groupZoneRuns(smoothed, records, percents, zones);

        // Pass 4 — merge short blocks into largest neighbor, repeat until stable
        let changed = true;
        while (changed) {
            changed = false;
            const next: ZoneBlock[] = [];
            for (let i = 0; i < blocks.length; i++) {
                const b = blocks[i];
                if (b.durationSeconds < minBlockSec && blocks.length > 1) {
                    // Pick neighbor with longest duration
                    const prev = next.length > 0 ? next[next.length - 1] : null;
                    const nxt = i + 1 < blocks.length ? blocks[i + 1] : null;
                    const target = !prev ? nxt : !nxt ? prev :
                        (prev.durationSeconds >= nxt.durationSeconds ? prev : nxt);
                    if (target && target === prev) {
                        this.mergeBlockInto(prev, b, records, percents);
                        changed = true;
                        continue;
                    } else if (target && target === nxt) {
                        this.mergeBlockInto(nxt, b, records, percents);
                        changed = true;
                        next.push(nxt);
                        i++; // skip nxt since we already consumed it
                        continue;
                    }
                }
                next.push(b);
            }
            blocks = next;
            // Also merge any consecutive same-zone blocks created by merges
            blocks = this.collapseAdjacentSameZone(blocks, records, percents, zones);
        }

        // Pass 5 — reclassify each block based on its average percent (= avg power / reference)
        for (const b of blocks) {
            const newZi = this.classifyZone(b.avgPercent, zones);
            if (newZi !== b.zoneIndex) {
                b.zoneIndex = newZi;
                const zone = zones[newZi];
                b.zoneLabel = zone?.label ?? `Z${newZi + 1}`;
                b.zoneDescription = zone?.description ?? '';
                b.color = this.ZONE_COLORS[newZi] ?? this.ZONE_COLORS[this.ZONE_COLORS.length - 1];
            }
        }

        // Pass 6 — final collapse of adjacent same-zone blocks after reclassification
        blocks = this.collapseAdjacentSameZone(blocks, records, percents, zones);

        return blocks;
    }

    private groupZoneRuns(smoothed: number[], records: FitRecord[], percents: number[], zones: Zone[]): ZoneBlock[] {
        const blocks: ZoneBlock[] = [];
        const n = smoothed.length;
        let start = 0;
        for (let i = 1; i <= n; i++) {
            if (i < n && smoothed[i] === smoothed[start]) continue;
            blocks.push(this.buildBlock(smoothed[start], start, i - 1, records, percents, zones));
            start = i;
        }
        return blocks;
    }

    private collapseAdjacentSameZone(blocks: ZoneBlock[], records: FitRecord[], percents: number[], zones: Zone[]): ZoneBlock[] {
        if (blocks.length < 2) return blocks;
        const out: ZoneBlock[] = [blocks[0]];
        for (let i = 1; i < blocks.length; i++) {
            const prev = out[out.length - 1];
            if (blocks[i].zoneIndex === prev.zoneIndex) {
                this.mergeBlockInto(prev, blocks[i], records, percents);
            } else {
                out.push(blocks[i]);
            }
        }
        return out;
    }

    private buildBlock(zi: number, start: number, end: number, records: FitRecord[], percents: number[], zones: Zone[]): ZoneBlock {
        const zone = zones[zi];
        let sumPower = 0, sumSpeed = 0, sumHR = 0, sumCad = 0, sumPct = 0;
        let maxPower = 0, maxSpeed = 0;
        for (let j = start; j <= end; j++) {
            sumPower += records[j].power;
            sumSpeed += records[j].speed;
            sumHR += records[j].heartRate;
            sumCad += records[j].cadence;
            sumPct += percents[j];
            if (records[j].power > maxPower) maxPower = records[j].power;
            if (records[j].speed > maxSpeed) maxSpeed = records[j].speed;
        }
        const count = end - start + 1;
        return {
            zoneIndex: zi,
            zoneLabel: zone?.label ?? `Z${zi + 1}`,
            zoneDescription: zone?.description ?? '',
            color: this.ZONE_COLORS[zi] ?? this.ZONE_COLORS[this.ZONE_COLORS.length - 1],
            startIndex: start,
            endIndex: end,
            durationSeconds: records[end].timestamp - records[start].timestamp,
            avgPower: Math.round(sumPower / count),
            maxPower: Math.round(maxPower),
            avgSpeed: Math.round((sumSpeed / count) * 36) / 10,
            maxSpeed: Math.round(maxSpeed * 36) / 10,
            avgHR: Math.round(sumHR / count),
            avgCadence: Math.round(sumCad / count),
            avgPercent: Math.round(sumPct / count),
        };
    }

    private mergeBlockInto(target: ZoneBlock, source: ZoneBlock, records: FitRecord[], percents: number[]): void {
        const newStart = Math.min(target.startIndex, source.startIndex);
        const newEnd = Math.max(target.endIndex, source.endIndex);
        const count = newEnd - newStart + 1;
        // Recompute all aggregates from raw records for accuracy
        let sumPower = 0, sumSpeed = 0, sumHR = 0, sumCad = 0, sumPct = 0;
        let maxPower = 0, maxSpeed = 0;
        for (let j = newStart; j <= newEnd; j++) {
            sumPower += records[j].power;
            sumSpeed += records[j].speed;
            sumHR += records[j].heartRate;
            sumCad += records[j].cadence;
            sumPct += percents[j];
            if (records[j].power > maxPower) maxPower = records[j].power;
            if (records[j].speed > maxSpeed) maxSpeed = records[j].speed;
        }
        target.avgPower = Math.round(sumPower / count);
        target.maxPower = Math.round(maxPower);
        target.avgSpeed = Math.round((sumSpeed / count) * 36) / 10;
        target.maxSpeed = Math.round(maxSpeed * 36) / 10;
        target.avgHR = Math.round(sumHR / count);
        target.avgCadence = Math.round(sumCad / count);
        target.avgPercent = Math.round(sumPct / count);
        target.startIndex = newStart;
        target.endIndex = newEnd;
        target.durationSeconds = records[newEnd].timestamp - records[newStart].timestamp;
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
        const updated = { ...session, rpe: val };
        this.sessionSubject.next(updated);
        this.rpeUpdate$.next({ id: session.id, rpe: val });
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
            // m/s → min:sec /100m
            const secPer100 = 100 / speedMs;
            const m = Math.floor(secPer100 / 60);
            const s = Math.round(secPer100 % 60);
            return `${m}:${String(s).padStart(2, '0')} /100m`;
        }
        // Running → min:sec /km
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

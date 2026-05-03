import {ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, OnInit, ViewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, Router} from '@angular/router';
import {BehaviorSubject, combineLatest} from 'rxjs';
import {debounceTime, distinctUntilChanged, map, take} from 'rxjs/operators';
import {ResponsiveService} from '../../../services/responsive.service';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {SessionAnalysisComponent} from '../session-analysis/session-analysis.component';
import {FilterPillsComponent} from '../../shared/filter-pills/filter-pills.component';
import {ModalShellComponent} from '../../shared/modal-shell/modal-shell.component';
import {SkeletonComponent} from '../../shared/skeleton/skeleton.component';
import {HistoryService, SavedSession, SessionFilters} from '../../../services/history.service';
import {formatTimeText} from '../../shared/format/format.utils';

import {BlockSummary, SessionSummary} from '../../../services/workout-execution.service';
import {FitExportService} from '../../../services/fit-export.service';
import {CsvExportService} from '../../../services/csv-export.service';
import {AuthService} from '../../../services/auth.service';
import {MetricsService} from '../../../services/metrics.service';
import {StravaSyncService} from '../../../services/strava-sync.service';

// @ts-ignore
import FitParser from 'fit-file-parser';

type SportFilter = string | null;

type WeekHeaderRow = { kind: 'week'; weekKey: string; start: Date; end: Date; count: number };
type SessionRow = { kind: 'session'; weekKey: string; session: SavedSession };
type InactivityRow = { kind: 'inactivity'; rowKey: string; start: Date; end: Date; weeks: number };
export type HistoryRow = WeekHeaderRow | SessionRow | InactivityRow;

@Component({
    selector: 'app-workout-history',
    standalone: true,
    imports: [CommonModule, FormsModule, TranslateModule, SportIconComponent, SessionAnalysisComponent, FilterPillsComponent, ModalShellComponent, SkeletonComponent],
    templateUrl: './workout-history.component.html',
    styleUrl: './workout-history.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutHistoryComponent implements OnInit {
    @ViewChild('fitInput') fitInputRef!: ElementRef<HTMLInputElement>;

    historyService = inject(HistoryService);
    private fitExport = inject(FitExportService);
    private csvExport = inject(CsvExportService);
    private translate = inject(TranslateService);
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private destroyRef = inject(DestroyRef);
    private responsive = inject(ResponsiveService);
    stravaSyncService = inject(StravaSyncService);

    sessions$ = this.historyService.historySessions$;
    historyState$ = this.historyService.historyState$;
    sidebarCollapsed = false;

    /**
     * Detail mode is driven entirely by the URL:
     *   /history             → list, no detail
     *   /history/:sessionId  → detail of that session
     *
     * The native back button works for free; mobile flow is pure CSS.
     */
    sessionIdParam$ = this.route.paramMap.pipe(map((p) => p.get('sessionId')));
    isListView$ = this.sessionIdParam$.pipe(map((id) => !id));

    toggleSidebar(): void {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    }

    ngOnInit(): void {
        // Sync the service-level selectedSession with the route param so any
        // downstream consumer that reads `historyService.selectedSession$`
        // sees the current focus.
        combineLatest([this.sessionIdParam$, this.sessions$])
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(([id, sessions]) => {
                if (!id) {
                    this.historyService.selectSession(null);
                    return;
                }
                if (sessions.length === 0) return;
                const match = sessions.find((s) => s.id === id);
                this.historyService.selectSession(match ?? null);
            });

        // On desktop, when the user lands on bare /history, jump them to the
        // first session so the detail panel isn't empty.
        combineLatest([
            this.sessions$,
            this.responsive.isMobile$,
            this.sessionIdParam$,
        ])
            .pipe(take(1), takeUntilDestroyed(this.destroyRef))
            .subscribe(([sessions, mobile, id]) => {
                if (!mobile && !id && sessions.length > 0) {
                    this.router.navigate(['/history', sessions[0].id], { replaceUrl: true });
                }
            });

        // Push filter changes server-side; debounce so each digit typed in
        // numeric inputs doesn't issue its own request.
        combineLatest([
            this.sportFilterSubject,
            this.dateFromSubject,
            this.dateToSubject,
            this.durationMinSubject,
            this.durationMaxSubject,
            this.tssMinSubject,
            this.tssMaxSubject,
        ]).pipe(
            // Skip the initial emission — the service has already loaded the
            // unfiltered first window in its constructor.
            debounceTime(300),
            map(([sport, from, to, durMin, durMax, tssMin, tssMax]): SessionFilters => ({
                sport,
                from: from || null,
                to: to || null,
                durationMinSec: durMin != null ? durMin * 60 : null,
                durationMaxSec: durMax != null ? durMax * 60 : null,
                tssMin,
                tssMax,
            })),
            distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe((filters) => {
            if (this.hasAnyFilter(filters)) {
                this.historyService.setHistoryFilters(filters);
            } else if (this.lastAppliedHadFilters) {
                // Filters were cleared — reload unfiltered first window.
                this.historyService.setHistoryFilters({});
            }
            this.lastAppliedHadFilters = this.hasAnyFilter(filters);
        });
    }

    private lastAppliedHadFilters = false;

    private hasAnyFilter(f: SessionFilters): boolean {
        return !!(f.sport || f.from || f.to
            || f.durationMinSec != null || f.durationMaxSec != null
            || f.tssMin != null || f.tssMax != null);
    }

    loadOlder(): void {
        this.historyService.loadOlderHistory();
    }

    // Filters — labels translated via instant (language known at component init)
    get sportOptions() {
        return [
            { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_ALL'), value: null },
            { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_BIKE'), value: 'CYCLING' },
            { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_RUN'), value: 'RUNNING' },
            { label: this.translate.instant('WORKOUT_HISTORY.SPORT_FILTER_SWIM'), value: 'SWIMMING' },
        ];
    }

    private sportFilterSubject = new BehaviorSubject<SportFilter>(null);
    private dateFromSubject = new BehaviorSubject<string>('');
    private dateToSubject = new BehaviorSubject<string>('');
    private durationMinSubject = new BehaviorSubject<number | null>(null);
    private durationMaxSubject = new BehaviorSubject<number | null>(null);
    private tssMinSubject = new BehaviorSubject<number | null>(null);
    private tssMaxSubject = new BehaviorSubject<number | null>(null);

    activeSportFilter: SportFilter = null;
    dateFrom = '';
    dateTo = '';
    durationMin: number | null = null;
    durationMax: number | null = null;
    tssMin: number | null = null;
    tssMax: number | null = null;

    filtersOpen = false;

    get activeAdvancedFilterCount(): number {
        let n = 0;
        if (this.dateFrom) n++;
        if (this.dateTo) n++;
        if (this.durationMin != null) n++;
        if (this.durationMax != null) n++;
        if (this.tssMin != null) n++;
        if (this.tssMax != null) n++;
        return n;
    }

    openFilters(): void {
        this.filtersOpen = true;
    }

    closeFilters(): void {
        this.filtersOpen = false;
    }

    resetAdvancedFilters(): void {
        this.onDateFromChange('');
        this.onDateToChange('');
        this.onDurationMinChange(null);
        this.onDurationMaxChange(null);
        this.onTssMinChange(null);
        this.onTssMaxChange(null);
    }

    ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? null));

    // Week grouping state — tracks which week keys the user has expanded.
    // Seeded once on the first non-empty emission so the most recent active
    // week starts open; subsequent toggles are fully manual.
    expandedWeeks = new Set<string>();
    private expandedSeeded = false;
    private toggleSubject = new BehaviorSubject<void>(undefined);

    groupedRows$ = combineLatest([this.sessions$, this.toggleSubject]).pipe(
        map(([sessions]) => {
            if (!this.expandedSeeded && sessions.length > 0) {
                const firstKey = this.weekKeyOf(new Date(sessions[0].date));
                this.expandedWeeks.add(firstKey);
                this.expandedSeeded = true;
            }
            return this.buildRows(sessions);
        }),
    );

    toggleWeek(weekKey: string): void {
        if (this.expandedWeeks.has(weekKey)) {
            this.expandedWeeks.delete(weekKey);
        } else {
            this.expandedWeeks.add(weekKey);
        }
        this.toggleSubject.next();
    }

    isExpanded(weekKey: string): boolean {
        return this.expandedWeeks.has(weekKey);
    }

    /** Monday 00:00 of the week containing `d` (local time). */
    private weekStartOf(d: Date): Date {
        const out = new Date(d);
        out.setHours(0, 0, 0, 0);
        const day = out.getDay(); // 0=Sun..6=Sat
        const offsetToMonday = day === 0 ? -6 : 1 - day;
        out.setDate(out.getDate() + offsetToMonday);
        return out;
    }

    /** Stable per-week key (Monday's local YYYY-MM-DD). */
    private weekKeyOf(d: Date): string {
        const monday = this.weekStartOf(d);
        const y = monday.getFullYear();
        const m = String(monday.getMonth() + 1).padStart(2, '0');
        const day = String(monday.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    }

    /**
     * Build the flat row sequence the template iterates: alternating week
     * headers and (when expanded) their sessions, with a single inactivity
     * row between two active weeks separated by ≥ 1 fully-skipped week.
     * Sessions arrive sorted newest→oldest from `historyService.loadSessions()`.
     */
    private buildRows(sessions: SavedSession[]): HistoryRow[] {
        if (sessions.length === 0) return [];

        // Group preserving the input order so each week's session list stays
        // newest-first within the week.
        const order: string[] = [];
        const buckets = new Map<string, { start: Date; end: Date; sessions: SavedSession[] }>();
        for (const s of sessions) {
            const start = this.weekStartOf(new Date(s.date));
            const key = this.weekKeyOf(new Date(s.date));
            let bucket = buckets.get(key);
            if (!bucket) {
                const end = new Date(start);
                end.setDate(end.getDate() + 6);
                end.setHours(23, 59, 59, 999);
                bucket = { start, end, sessions: [] };
                buckets.set(key, bucket);
                order.push(key);
            }
            bucket.sessions.push(s);
        }

        const ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;
        const rows: HistoryRow[] = [];
        let prev: { start: Date; end: Date } | null = null;

        for (const key of order) {
            const bucket = buckets.get(key)!;

            if (prev) {
                // Gap between newer (prev) and older (bucket) — measured in
                // skipped Mondays.
                const gapWeeks = Math.round(
                    (prev.start.getTime() - bucket.end.getTime()) / ONE_WEEK_MS,
                );
                if (gapWeeks >= 1) {
                    const gapStart = new Date(bucket.end);
                    gapStart.setDate(gapStart.getDate() + 1);
                    gapStart.setHours(0, 0, 0, 0);
                    const gapEnd = new Date(prev.start);
                    gapEnd.setDate(gapEnd.getDate() - 1);
                    gapEnd.setHours(23, 59, 59, 999);
                    rows.push({
                        kind: 'inactivity',
                        rowKey: `gap-${this.weekKeyOf(gapStart)}-${this.weekKeyOf(gapEnd)}`,
                        start: gapStart,
                        end: gapEnd,
                        weeks: gapWeeks,
                    });
                }
            }

            rows.push({
                kind: 'week',
                weekKey: key,
                start: bucket.start,
                end: bucket.end,
                count: bucket.sessions.length,
            });

            if (this.expandedWeeks.has(key)) {
                for (const s of bucket.sessions) {
                    rows.push({ kind: 'session', weekKey: key, session: s });
                }
            }

            prev = { start: bucket.start, end: bucket.end };
        }

        return rows;
    }

    formatWeekRange(start: Date, end: Date): string {
        const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
        return `${start.toLocaleDateString('en-US', opts)} – ${end.toLocaleDateString('en-US', opts)}`;
    }

    trackRow(_index: number, row: HistoryRow): string {
        if (row.kind === 'session') return `s-${row.session.id}`;
        if (row.kind === 'week') return `w-${row.weekKey}`;
        return row.rowKey;
    }

    setSportFilter(value: SportFilter): void {
        this.activeSportFilter = value;
        this.sportFilterSubject.next(value);
    }

    onDateFromChange(value: string): void {
        this.dateFrom = value;
        this.dateFromSubject.next(value);
    }

    onDateToChange(value: string): void {
        this.dateTo = value;
        this.dateToSubject.next(value);
    }

    onDurationMinChange(value: number | null): void {
        this.durationMin = value;
        this.durationMinSubject.next(value != null && !isNaN(value) ? value : null);
    }

    onDurationMaxChange(value: number | null): void {
        this.durationMax = value;
        this.durationMaxSubject.next(value != null && !isNaN(value) ? value : null);
    }

    onTssMinChange(value: number | null): void {
        this.tssMin = value;
        this.tssMinSubject.next(value != null && !isNaN(value) ? value : null);
    }

    onTssMaxChange(value: number | null): void {
        this.tssMax = value;
        this.tssMaxSubject.next(value != null && !isNaN(value) ? value : null);
    }

    syncing$ = this.stravaSyncService.syncing$;
    syncResult$ = this.stravaSyncService.lastResult$;

    importing = false;
    importError = false;

    getTss(session: SavedSession, ftp: number | null): number | null {
        if (session.tss != null) return Math.round(session.tss);
        if (!ftp) return null;
        return Math.round(this.metricsService.computeTss(session.totalDuration, session.avgPower, ftp));
    }

    getIF(session: SavedSession, ftp: number | null): number | null {
        if (session.intensityFactor != null) return session.intensityFactor;
        if (!ftp) return null;
        return this.metricsService.computeIF(session.avgPower, ftp);
    }

    importStravaHistory(): void {
        this.stravaSyncService.importHistory().subscribe();
    }

    exportCsv(): void {
        combineLatest([this.sessions$, this.ftp$]).pipe(take(1)).subscribe(([sessions, ftp]) => {
            this.csvExport.exportSessions(sessions, ftp);
        });
    }

    onSelect(session: SavedSession): void {
        this.router.navigate(['/history', session.id]);
    }

    downloadFit(event: Event, session: SavedSession) {
        event.stopPropagation();
        if (session.stravaActivityId && !session.fitFileId) {
            // Strava session without FIT — build it from streams first, then download
            this.stravaSyncService.buildFit(session.id).subscribe({
                next: () => this.fitExport.exportSession(session, session.date),
            });
        } else {
            this.fitExport.exportSession(session, session.date);
        }
    }

    triggerUpload() {
        this.fitInputRef.nativeElement.click();
    }

    async onFileSelected(event: Event) {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        input.value = '';

        this.importing = true;
        this.importError = false;

        try {
            const buffer = await file.arrayBuffer();
            const session = await this.parseFit(file.name, buffer);
            this.historyService.saveSession(session, buffer);
        } catch (e) {
            console.error('Failed to import FIT file', e);
            this.importError = true;
            setTimeout(() => (this.importError = false), 4000);
        } finally {
            this.importing = false;
        }
    }

    private parseFit(fileName: string, buffer: ArrayBuffer): Promise<SessionSummary> {
        return new Promise((resolve, reject) => {
            const parser = new FitParser({ force: true, mode: 'list' });

            parser.parse(buffer, (error: any, data: any) => {
                if (error) {
                    reject(new Error(error));
                    return;
                }

                const session = data.sessions?.[0];
                if (!session) {
                    reject(new Error('No session found in FIT file'));
                    return;
                }

                const sportMap: Record<string, SessionSummary['sportType']> = {
                    cycling: 'CYCLING',
                    running: 'RUNNING',
                    swimming: 'SWIMMING',
                };

                const blockSummaries: BlockSummary[] = (data.laps || []).map(
                    (lap: any, i: number) => ({
                        label: `Lap ${i + 1}`,
                        durationSeconds: Math.round(lap.total_elapsed_time || 0),
                        targetPower: 0,
                        actualPower: Math.round(lap.avg_power || 0),
                        actualCadence: Math.round(lap.avg_cadence || 0),
                        actualHR: Math.round(lap.avg_heart_rate || 0),
                        type: 'STEADY',
                    }),
                );

                const name = fileName
                    .replace(/\.fit$/i, '')
                    .replace(/[_-]+/g, ' ')
                    .trim();

                resolve({
                    title: name || 'Uploaded Session',
                    totalDuration: Math.round(
                        session.total_elapsed_time || session.total_timer_time || 0,
                    ),
                    avgPower: Math.round(session.avg_power || 0),
                    avgHR: Math.round(session.avg_heart_rate || 0),
                    avgCadence: Math.round(session.avg_cadence || 0),
                    avgSpeed: session.avg_speed || 0,
                    sportType: sportMap[session.sport?.toLowerCase()] ?? 'CYCLING',
                    blockSummaries,
                    history: [],
                });
            });
        });
    }

    formatTime(seconds: number): string {
        return formatTimeText(seconds);
    }

    formatDate(date: Date): string {
        return new Date(date).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    }

    getSportUnit(session: SavedSession): string {
        if (session.sportType === 'RUNNING') return '/km';
        if (session.sportType === 'SWIMMING') return '/100m';
        return 'W';
    }
}

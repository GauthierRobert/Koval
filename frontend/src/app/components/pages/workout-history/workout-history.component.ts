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

import {FitExportService} from '../../../services/fit-export.service';
import {CsvExportService} from '../../../services/csv-export.service';
import {AuthService} from '../../../services/auth.service';
import {MetricsService} from '../../../services/metrics.service';
import {StravaSyncService} from '../../../services/strava-sync.service';
import {
    buildHistoryRows,
    formatWeekRange,
    HistoryRow,
    weekKeyOf,
} from './workout-history-grouping';
import {parseFitToSession} from './workout-history-fit-parser';

export type {HistoryRow} from './workout-history-grouping';

type SportFilter = string | null;

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
                this.expandedWeeks.add(weekKeyOf(new Date(sessions[0].date)));
                this.expandedSeeded = true;
            }
            return buildHistoryRows(sessions, this.expandedWeeks);
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

    formatWeekRange = formatWeekRange;

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
            const session = await parseFitToSession(file.name, buffer);
            this.historyService.saveSession(session, buffer);
        } catch (e) {
            console.error('Failed to import FIT file', e);
            this.importError = true;
            setTimeout(() => (this.importError = false), 4000);
        } finally {
            this.importing = false;
        }
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

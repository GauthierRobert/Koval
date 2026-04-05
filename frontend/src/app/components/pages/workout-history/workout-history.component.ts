import {ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, OnInit, ViewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ActivatedRoute} from '@angular/router';
import {BehaviorSubject, combineLatest} from 'rxjs';
import {filter, map, take} from 'rxjs/operators';
import {SportIconComponent} from '../../shared/sport-icon/sport-icon.component';
import {SessionAnalysisComponent} from '../session-analysis/session-analysis.component';
import {FilterPillsComponent} from '../../shared/filter-pills/filter-pills.component';
import {HistoryService, SavedSession} from '../../../services/history.service';
import {formatTimeText} from '../../shared/format/format.utils';

import {BlockSummary, SessionSummary} from '../../../services/workout-execution.service';
import {FitExportService} from '../../../services/fit-export.service';
import {AuthService} from '../../../services/auth.service';
import {MetricsService} from '../../../services/metrics.service';
import {StravaSyncService} from '../../../services/strava-sync.service';

// @ts-ignore
import FitParser from 'fit-file-parser';

type SportFilter = string | null;

@Component({
    selector: 'app-workout-history',
    standalone: true,
    imports: [CommonModule, FormsModule, TranslateModule, SportIconComponent, SessionAnalysisComponent, FilterPillsComponent],
    templateUrl: './workout-history.component.html',
    styleUrl: './workout-history.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutHistoryComponent implements OnInit {
    @ViewChild('fitInput') fitInputRef!: ElementRef<HTMLInputElement>;

    historyService = inject(HistoryService);
    private fitExport = inject(FitExportService);
    private translate = inject(TranslateService);
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private route = inject(ActivatedRoute);
    private destroyRef = inject(DestroyRef);
    stravaSyncService = inject(StravaSyncService);

    sessions$ = this.historyService.sessions$;
    mobileListOpen = true;
    sidebarCollapsed = false;

    toggleSidebar(): void {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    }

    toggleMobileList(): void {
      this.mobileListOpen = !this.mobileListOpen;
    }

    ngOnInit(): void {
        const sessionId = this.route.snapshot.paramMap.get('sessionId');
        this.historyService.sessions$.pipe(
            filter(sessions => sessions.length > 0),
            take(1),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(sessions => {
            if (sessionId) {
                const match = sessions.find(s => s.id === sessionId);
                if (match) this.historyService.selectSession(match);
            } else {
                this.historyService.selectedSession$.pipe(take(1)).subscribe(current => {
                    if (!current) this.historyService.selectSession(sessions[0]);
                });
            }
        });
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

    activeSportFilter: SportFilter = null;
    dateFrom = '';
    dateTo = '';

    filteredSessions$ = combineLatest([
        this.historyService.sessions$,
        this.sportFilterSubject,
        this.dateFromSubject,
        this.dateToSubject,
    ]).pipe(
        map(([sessions, sport, from, to]) => {
            let filtered = sessions;
            if (sport) {
                filtered = filtered.filter(s => s.sportType === sport);
            }
            if (from) {
                const fromDate = new Date(from + 'T00:00:00');
                filtered = filtered.filter(s => new Date(s.date) >= fromDate);
            }
            if (to) {
                const toDate = new Date(to + 'T23:59:59');
                filtered = filtered.filter(s => new Date(s.date) <= toDate);
            }
            return filtered;
        })
    );

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

    ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? null));

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

    onSelect(session: SavedSession): void {
        this.historyService.selectSession(session);
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

    trackSessionById(session: SavedSession): string {
        return session.id;
    }
}

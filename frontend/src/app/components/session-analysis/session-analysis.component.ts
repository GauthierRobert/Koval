import { Component, inject, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject, from, Observable, of } from 'rxjs';
import { catchError, distinctUntilChanged, map, startWith, switchMap } from 'rxjs/operators';
import { HistoryService, SavedSession } from '../../services/history.service';
import { AuthService } from '../../services/auth.service';
import { MetricsService, FitRecord } from '../../services/metrics.service';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { FitTimeseriesChartComponent } from '../fit-timeseries-chart/fit-timeseries-chart.component';

interface FitState {
    loading: boolean;
    error: boolean;
    records: FitRecord[];
}

@Component({
    selector: 'app-session-analysis',
    standalone: true,
    imports: [CommonModule, SportIconComponent, FitTimeseriesChartComponent],
    templateUrl: './session-analysis.component.html',
    styleUrl: './session-analysis.component.css',
})
export class SessionAnalysisComponent {
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private historyService = inject(HistoryService);


    private sessionSubject = new BehaviorSubject<SavedSession | null>(null);

    @Input() set session(s: SavedSession | null) {
        this.sessionSubject.next(s ?? null);
    }

    session$ = this.sessionSubject.asObservable();
    ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? 250));

    fitState$: Observable<FitState> = this.sessionSubject.pipe(
        distinctUntilChanged((a, b) => a?.fitFileId === b?.fitFileId),
        switchMap((session) => {
            if (!session?.fitFileId) {
                return of({ loading: false, error: false, records: [] as FitRecord[] });
            }
            return this.metricsService.downloadStoredFit(session.id).pipe(
                switchMap((buffer) => from(this.metricsService.parseFitTimeSeries(buffer))),
                map((records) => ({ loading: false, error: false, records })),
                catchError(() => of({ loading: false, error: true, records: [] as FitRecord[] })),
                startWith({ loading: true, error: false, records: [] as FitRecord[] }),
            );
        }),
    );

    getTss(session: SavedSession, ftp: number): number {
        if (session.tss != null && session.tss > 0) return Math.round(session.tss);
        if (session.rpe != null && session.rpe > 0) {
            return Math.round(this.metricsService.computeTssFromRpe(session.totalDuration, session.rpe));
        }
        return Math.round(this.metricsService.computeTss(session.totalDuration, session.avgPower, ftp));
    }

    saveRpe(session: SavedSession, event: any) {
        const val = parseInt(event.target.value, 10);
        if (isNaN(val) || val < 1 || val > 10) return;
        this.historyService.updateSession(session.id, { rpe: val });
    }

    getIF(session: SavedSession, ftp: number): number {
        if (session.intensityFactor != null) return session.intensityFactor;
        return this.metricsService.computeIF(session.avgPower, ftp);
    }

    formatTime(seconds: number): string {
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = seconds % 60;
        if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
        return `${m}:${String(s).padStart(2, '0')}`;
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

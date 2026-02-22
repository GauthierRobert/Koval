import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { combineLatest, Observable, of, switchMap } from 'rxjs';
import { map } from 'rxjs/operators';
import { HistoryService, SavedSession } from '../../services/history.service';
import { AuthService } from '../../services/auth.service';
import { MetricsService, FitRecord } from '../../services/metrics.service';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import { FitTimeseriesChartComponent } from '../fit-timeseries-chart/fit-timeseries-chart.component';

@Component({
    selector: 'app-session-analysis',
    standalone: true,
    imports: [CommonModule, RouterModule, SportIconComponent, FitTimeseriesChartComponent],
    templateUrl: './session-analysis.component.html',
    styleUrl: './session-analysis.component.css',
})
export class SessionAnalysisComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private historyService = inject(HistoryService);
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);

    session$!: Observable<SavedSession | null>;
    ftp$ = this.authService.user$.pipe(map((u) => u?.ftp ?? 250));

    fitRecords: FitRecord[] = [];
    fitLoading = false;
    fitError = false;

    ngOnInit(): void {
        this.session$ = combineLatest([
            this.route.paramMap,
            this.historyService.sessions$,
        ]).pipe(
            map(([params, sessions]) => {
                const id = params.get('id');
                return sessions.find((s) => s.id === id) ?? null;
            }),
        );

        // Load FIT data when session resolves
        this.session$.pipe(
            switchMap((session) => {
                if (!session?.fitFileId) return of(null);
                this.fitLoading = true;
                return this.metricsService.downloadStoredFit(session.id);
            }),
        ).subscribe({
            next: (buffer) => {
                this.fitLoading = false;
                if (!buffer) { this.fitRecords = []; return; }
                this.metricsService.parseFitTimeSeries(buffer).then((records) => {
                    this.fitRecords = records;
                }).catch(() => {
                    this.fitError = true;
                });
            },
            error: () => {
                this.fitLoading = false;
                this.fitError = true;
            },
        });
    }

    getTss(session: SavedSession, ftp: number): number {
        if (session.tss != null) return Math.round(session.tss);
        return Math.round(this.metricsService.computeTss(session.totalDuration, session.avgPower, ftp));
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

    goBack(): void {
        this.router.navigate(['/history']);
    }
}

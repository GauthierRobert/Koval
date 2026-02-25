import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { MetricsService, PmcDataPoint } from '../../services/metrics.service';
import { CalendarService } from '../../services/calendar.service';
import { PmcChartComponent } from '../pmc-chart/pmc-chart.component';

type Period = '1w' | '1m' | '3m' | '6m' | '1y';

@Component({
    selector: 'app-pmc-page',
    standalone: true,
    imports: [CommonModule, PmcChartComponent],
    templateUrl: './pmc-page.component.html',
    styleUrl: './pmc-page.component.css',
})
export class PmcPageComponent implements OnInit {
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private calendarService = inject(CalendarService);

    user$ = this.authService.user$;

    private pmcDataSubject = new BehaviorSubject<PmcDataPoint[]>([]);
    pmcData$ = this.pmcDataSubject.asObservable();

    // Period selector
    selectedPeriod: Period = '6m';
    readonly periods: { key: Period; label: string }[] = [
        { key: '1w', label: '1W' },
        { key: '1m', label: '1M' },
        { key: '3m', label: '3M' },
        { key: '6m', label: '6M' },
        { key: '1y', label: '1Y' },
    ];

    // Projection mode
    useScheduledProjection$ = new BehaviorSubject<boolean>(true);
    useScheduledProjection = true;

    // Scheduled workout TSS map: date → total TSS
    private scheduledTssMap$ = new BehaviorSubject<Map<string, number>>(new Map());
    scheduledWorkoutCount$ = this.scheduledTssMap$.pipe(map((m) => m.size));

    // Manual TSS projection controls
    taperTss$ = new BehaviorSubject<number>(50);
    taperDays$ = new BehaviorSubject<number>(30);
    taperTss = 50;
    taperDays = 30;

    fullPmcData$: Observable<PmcDataPoint[]> = combineLatest([
        this.pmcData$,
        this.taperTss$,
        this.taperDays$,
        this.scheduledTssMap$,
        this.useScheduledProjection$,
    ]).pipe(
        map(([real, tss, days, scheduledMap, useScheduled]) => {
            const projected = useScheduled
                ? this.metricsService.projectPmcFromSchedule(real, scheduledMap, days)
                : this.metricsService.projectPmc(real, tss, days);
            return [...real, ...projected];
        }),
    );

    peakForm$: Observable<{ date: string; tsb: number } | null> = this.fullPmcData$.pipe(
        map((data) => {
            const predicted = data.filter((d) => d.predicted);
            return predicted.length ? this.metricsService.findPeakForm(predicted) : null;
        }),
    );

    loading = false;
    error = false;

    ngOnInit(): void {
        this.loadPmc();
        this.loadScheduledWorkouts();
    }

    setPeriod(p: Period): void {
        this.selectedPeriod = p;
        this.loadPmc();
    }

    loadPmc(): void {
        this.loading = true;
        this.error = false;
        this.metricsService.getPmc(this.fromDate(), this.today()).subscribe({
            next: (data) => { this.pmcDataSubject.next(data); this.loading = false; },
            error: () => { this.loading = false; this.error = true; },
        });
    }

    loadScheduledWorkouts(): void {
        const future = this.addDays(new Date(), 120);
        this.calendarService.getMySchedule(this.today(), future).subscribe({
            next: (workouts) => {
                const map = new Map<string, number>();
                for (const w of workouts) {
                    if (w.status === 'PENDING' && w.tss) {
                        map.set(w.scheduledDate, (map.get(w.scheduledDate) ?? 0) + w.tss);
                    }
                }
                this.scheduledTssMap$.next(map);
            },
            error: () => this.scheduledTssMap$.next(new Map()),
        });
    }

    toggleProjectionMode(): void {
        this.useScheduledProjection = !this.useScheduledProjection;
        this.useScheduledProjection$.next(this.useScheduledProjection);
    }

    onTaperTssChange(val: number): void { this.taperTss$.next(val); }
    onTaperDaysChange(val: number): void { this.taperDays$.next(val); }

    formatPeakDate(dateStr: string): string {
        return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', {
            weekday: 'long', month: 'long', day: 'numeric',
        });
    }

    private fromDate(): string {
        const d = new Date();
        switch (this.selectedPeriod) {
            case '1w': d.setDate(d.getDate() - 7); break;
            case '1m': d.setMonth(d.getMonth() - 1); break;
            case '3m': d.setMonth(d.getMonth() - 3); break;
            case '6m': d.setMonth(d.getMonth() - 6); break;
            case '1y': d.setFullYear(d.getFullYear() - 1); break;
        }
        return d.toISOString().split('T')[0];
    }

    private today(): string { return new Date().toISOString().split('T')[0]; }

    private addDays(date: Date, days: number): string {
        const d = new Date(date);
        d.setDate(d.getDate() + days);
        return d.toISOString().split('T')[0];
    }
}

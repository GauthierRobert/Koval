import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { MetricsService, PmcDataPoint } from '../../services/metrics.service';
import { PmcChartComponent } from '../pmc-chart/pmc-chart.component';

@Component({
    selector: 'app-pmc-page',
    standalone: true,
    imports: [CommonModule, FormsModule, PmcChartComponent],
    templateUrl: './pmc-page.component.html',
    styleUrl: './pmc-page.component.css',
})
export class PmcPageComponent implements OnInit {
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);

    // Date range
    fromDate = this.sixMonthsAgo();
    toDate = this.today();

    // User load values from auth
    user$ = this.authService.user$;

    // PMC data from backend
    private pmcDataSubject = new BehaviorSubject<PmcDataPoint[]>([]);
    pmcData$ = this.pmcDataSubject.asObservable();

    // Taper slider
    taperTss$ = new BehaviorSubject<number>(50);
    taperDays$ = new BehaviorSubject<number>(30);

    taperTss = 50;
    taperDays = 30;

    // Full data including projection
    fullPmcData$: Observable<PmcDataPoint[]> = combineLatest([
        this.pmcData$,
        this.taperTss$,
        this.taperDays$,
    ]).pipe(
        map(([real, tss, days]) => [
            ...real,
            ...this.metricsService.projectPmc(real, tss, days),
        ]),
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
    }

    loadPmc(): void {
        this.loading = true;
        this.error = false;
        this.metricsService.getPmc(this.fromDate, this.toDate).subscribe({
            next: (data) => {
                this.pmcDataSubject.next(data);
                this.loading = false;
            },
            error: () => {
                this.loading = false;
                this.error = true;
            },
        });
    }

    onTaperTssChange(val: number): void {
        this.taperTss$.next(val);
    }

    onTaperDaysChange(val: number): void {
        this.taperDays$.next(val);
    }

    formatPeakDate(dateStr: string): string {
        return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', {
            weekday: 'long',
            month: 'long',
            day: 'numeric',
        });
    }

    private today(): string {
        return new Date().toISOString().split('T')[0];
    }

    private sixMonthsAgo(): string {
        const d = new Date();
        d.setMonth(d.getMonth() - 6);
        return d.toISOString().split('T')[0];
    }
}

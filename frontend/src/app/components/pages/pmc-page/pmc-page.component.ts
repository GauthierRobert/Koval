import {ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject, combineLatest, Observable, Subject} from 'rxjs';
import {debounceTime, distinctUntilChanged, map} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ActivatedRoute, Router} from '@angular/router';
import {AuthService} from '../../../services/auth.service';
import {MetricsService, PmcDataPoint} from '../../../services/metrics.service';
import {CalendarService} from '../../../services/calendar.service';
import {CoachService} from '../../../services/coach.service';
import {PmcChartComponent} from '../../shared/pmc-chart/pmc-chart.component';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';

@Component({
    selector: 'app-pmc-page',
    standalone: true,
    imports: [CommonModule, PmcChartComponent],
    templateUrl: './pmc-page.component.html',
    styleUrl: './pmc-page.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PmcPageComponent implements OnInit {
    private authService = inject(AuthService);
    private metricsService = inject(MetricsService);
    private calendarService = inject(CalendarService);
    private coachService = inject(CoachService);
    private raceGoalService = inject(RaceGoalService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private destroyRef = inject(DestroyRef);

    user$ = this.authService.user$;

    athleteId: string | null = null;

    private pmcDataSubject = new BehaviorSubject<PmcDataPoint[]>([]);
    pmcData$ = this.pmcDataSubject.asObservable();

    // Dynamic loading state
    private loadedFrom = '';
    private loadedTo = '';
    private viewRange$ = new Subject<{start: string, end: string}>();
    private fetchingLeft = false;
    private fetchingRight = false;
    private readonly BUFFER_DAYS = 60;
    private readonly FETCH_CHUNK_DAYS = 180;

    // Scheduled workout TSS map: date → total TSS
    private scheduledTssMap$ = new BehaviorSubject<Map<string, number>>(new Map());
    scheduledWorkoutCount$ = this.scheduledTssMap$.pipe(map((m) => m.size));

    // Projection extends dynamically as the user pans into the future
    private viewEndDate$ = new BehaviorSubject<string>(this.addDays(new Date(), 30));
    private projectionDays$: Observable<number> = this.viewEndDate$.pipe(
        map((viewEnd) => {
            const todayMs = new Date(this.today() + 'T12:00:00').getTime();
            const endMs = new Date(viewEnd + 'T12:00:00').getTime();
            const daysToEnd = Math.ceil((endMs - todayMs) / 86400000);
            return Math.max(30, daysToEnd + 30);
        }),
        distinctUntilChanged(),
    );

    fullPmcData$: Observable<PmcDataPoint[]> = combineLatest([
        this.pmcData$,
        this.scheduledTssMap$,
        this.projectionDays$,
    ]).pipe(
        map(([real, scheduledMap, days]) => [
            ...real,
            ...this.metricsService.projectPmcFromSchedule(real, scheduledMap, days),
        ]),
    );

    peakForm$: Observable<{ date: string; tsb: number } | null> = this.fullPmcData$.pipe(
        map((data) => {
            const predicted = data.filter((d) => d.predicted);
            return predicted.length ? this.metricsService.findPeakForm(predicted) : null;
        }),
    );

    private athleteGoalsSubject = new BehaviorSubject<RaceGoal[]>([]);
    athleteGoals$ = this.athleteGoalsSubject.asObservable();

    // Will be set after ngOnInit determines athleteId
    displayGoals$!: Observable<RaceGoal[]>;

    loading = false;
    error = false;

    ngOnInit(): void {
        this.athleteId = this.route.snapshot.queryParamMap.get('athleteId');
        this.displayGoals$ = this.athleteId ? this.athleteGoals$ : this.raceGoalService.goals$;
        this.loadPmc();
        if (!this.athleteId) {
            this.loadScheduledWorkouts();
            this.raceGoalService.loadGoals();
        }

        this.viewRange$.pipe(
            debounceTime(300),
            takeUntilDestroyed(this.destroyRef),
        ).subscribe(range => this.checkAndFetchData(range));
    }

    onViewRangeChange(range: {start: string, end: string}): void {
        this.viewRange$.next(range);
        this.viewEndDate$.next(range.end);
    }

    loadPmc(): void {
        this.loading = true;
        this.error = false;
        if (this.athleteId) {
            const now = new Date();
            const from = new Date(now); from.setDate(from.getDate() - 365);
            const to = new Date(now); to.setDate(to.getDate() + 90);
            const fromStr = from.toISOString().split('T')[0];
            const toStr = to.toISOString().split('T')[0];
            this.coachService.getAthletePmc(this.athleteId, fromStr, toStr).subscribe({
                next: (data) => {
                    this.pmcDataSubject.next(data);
                    this.loadedFrom = fromStr;
                    this.loadedTo = toStr;
                    this.loading = false;
                },
                error: () => { this.loading = false; this.error = true; },
            });
        } else {
            const from = new Date();
            from.setDate(from.getDate() - 365);
            const fromStr = from.toISOString().split('T')[0];
            const toStr = this.today();
            this.metricsService.getPmc(fromStr, toStr).subscribe({
                next: (data) => {
                    this.pmcDataSubject.next(data);
                    this.loadedFrom = fromStr;
                    this.loadedTo = toStr;
                    this.loading = false;
                },
                error: () => { this.loading = false; this.error = true; },
            });
        }
        if (this.athleteId) {
            this.raceGoalService.getAthleteGoals(this.athleteId).subscribe({
                next: (goals) => this.athleteGoalsSubject.next(goals),
                error: () => this.athleteGoalsSubject.next([]),
            });
        }
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

    formatPeakDate(dateStr: string): string {
        return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', {
            weekday: 'long', month: 'long', day: 'numeric',
        });
    }

    backToCoach(): void {
        this.router.navigate(['/coach']);
    }

    private checkAndFetchData(range: {start: string, end: string}): void {
        if (!this.loadedFrom || !this.loadedTo) return;
        const rStart = this.toDays(range.start);
        const lFrom = this.toDays(this.loadedFrom);
        const rEnd = this.toDays(range.end);
        const lTo = this.toDays(this.loadedTo);

        // Need older data?
        if (rStart - lFrom < this.BUFFER_DAYS && !this.fetchingLeft) {
            const newFrom = this.addDaysStr(this.loadedFrom, -this.FETCH_CHUNK_DAYS);
            this.fetchingLeft = true;
            this.fetchAndMerge(newFrom, this.loadedFrom, 'left');
        }
        // Need newer data? (cap at today — projection handles future)
        if (lTo - rEnd < this.BUFFER_DAYS && !this.fetchingRight) {
            const todayStr = this.today();
            const newTo = this.addDaysStr(this.loadedTo, this.FETCH_CHUNK_DAYS);
            const cappedTo = newTo > todayStr ? todayStr : newTo;
            if (cappedTo > this.loadedTo) {
                this.fetchingRight = true;
                this.fetchAndMerge(this.loadedTo, cappedTo, 'right');
            }
        }
    }

    private fetchAndMerge(from: string, to: string, dir: 'left' | 'right'): void {
        const fetch$ = this.athleteId
            ? this.coachService.getAthletePmc(this.athleteId, from, to)
            : this.metricsService.getPmc(from, to);

        fetch$.subscribe({
            next: (newData) => {
                const existing = this.pmcDataSubject.value;
                const map = new Map<string, PmcDataPoint>();
                for (const p of existing) map.set(p.date, p);
                for (const p of newData) map.set(p.date, p);
                const merged = Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date));
                this.pmcDataSubject.next(merged);
                if (from < this.loadedFrom) this.loadedFrom = from;
                if (to > this.loadedTo) this.loadedTo = to;
            },
            complete: () => { if (dir === 'left') this.fetchingLeft = false; else this.fetchingRight = false; },
            error: () => { if (dir === 'left') this.fetchingLeft = false; else this.fetchingRight = false; },
        });
    }

    private today(): string { return new Date().toISOString().split('T')[0]; }

    private addDays(date: Date, days: number): string {
        const d = new Date(date);
        d.setDate(d.getDate() + days);
        return d.toISOString().split('T')[0];
    }

    private toDays(date: string): number {
        return Math.round(new Date(date + 'T12:00:00').getTime() / 86400000);
    }

    private addDaysStr(date: string, n: number): string {
        const d = new Date(date + 'T12:00:00');
        d.setDate(d.getDate() + n);
        return d.toISOString().split('T')[0];
    }
}

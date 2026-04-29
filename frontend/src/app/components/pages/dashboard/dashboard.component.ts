import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, RouterModule} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest} from 'rxjs';
import {filter, map, shareReplay, startWith, switchMap} from 'rxjs/operators';
import {AuthService} from '../../../services/auth.service';
import {CalendarService} from '../../../services/calendar.service';
import {HistoryService, SavedSession} from '../../../services/history.service';
import {MetricsService, PmcDataPoint} from '../../../services/metrics.service';
import {CoachService, ScheduledWorkout} from '../../../services/coach.service';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {AnalyticsService, VolumeEntry} from '../../../services/analytics.service';
import {WorkoutDetailModalComponent} from '../../shared/workout-detail-modal/workout-detail-modal.component';
import {daysUntil, formatTrainingDuration} from '../../shared/format/format.utils';
import {DashboardFocusCardComponent} from './dashboard-focus-card/dashboard-focus-card.component';
import {DashboardClubCardsComponent} from './dashboard-club-cards/dashboard-club-cards.component';
import {DashboardVolumeChartComponent} from './dashboard-volume-chart/dashboard-volume-chart.component';
import {LastActivityCardComponent} from './last-activity-card/last-activity-card.component';

function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function getStartOfWeek(d: Date): Date {
  const date = new Date(d);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  date.setDate(date.getDate() + diff);
  date.setHours(0, 0, 0, 0);
  return date;
}

function subDays(d: Date, n: number): Date {
  const date = new Date(d);
  date.setDate(date.getDate() - n);
  return date;
}

export interface SportStats {
  sport: string;
  durationSeconds: number;
  distanceMeters: number;
  tss: number;
  sessionCount: number;
}

export interface WeekMetrics {
  current: SportStats[];
  previous: SportStats[];
}

function agg(sessions: SavedSession[], sport: string): SportStats {
  const ss = sessions.filter((s) => s.sportType === sport);
  return {
    sport,
    sessionCount: ss.length,
    durationSeconds: ss.reduce((a, s) => a + s.totalDuration, 0),
    distanceMeters: ss.reduce((a, s) => a + (s.avgSpeed > 0 ? s.avgSpeed * s.totalDuration : 0), 0),
    tss: ss.reduce((a, s) => a + (s.tss ?? 0), 0),
  };
}

function computeWeekMetrics(sessions: SavedSession[]): WeekMetrics {
  const weekStart = getStartOfWeek(new Date());
  const lastStart = subDays(weekStart, 7);
  const thisSessions = sessions.filter((s) => new Date(s.date) >= weekStart);
  const lastSessions = sessions.filter((s) => {
    const d = new Date(s.date);
    return d >= lastStart && d < weekStart;
  });
  const SPORTS = ['CYCLING', 'RUNNING', 'SWIMMING'];
  return {
    current: SPORTS.map((sport) => agg(thisSessions, sport)),
    previous: SPORTS.map((sport) => agg(lastSessions, sport)),
  };
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, WorkoutDetailModalComponent, TranslateModule, DashboardFocusCardComponent, DashboardClubCardsComponent, DashboardVolumeChartComponent, LastActivityCardComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  private authService = inject(AuthService);
  private calendarService = inject(CalendarService);
  private historyService = inject(HistoryService);
  private metricsService = inject(MetricsService);
  private raceGoalService = inject(RaceGoalService);
  private coachService = inject(CoachService);
  private analyticsService = inject(AnalyticsService);
  private router = inject(Router);
  private translate = inject(TranslateService);

  readonly todayDate = new Date();
  readonly todayKey = toDateKey(new Date());
  private readonly sevenDaysAgoKey = toDateKey(subDays(new Date(), 7));
  private readonly fourteenDaysAhead = toDateKey(subDays(new Date(), -14));

  private reload$ = new BehaviorSubject<void>(undefined);

  private schedule$ = this.authService.user$.pipe(
    filter((u) => !!u),
    switchMap(() => this.reload$),
    switchMap(() => this.calendarService.getMySchedule(this.sevenDaysAgoKey, this.fourteenDaysAhead)),
    shareReplay(1),
  );

  overdueWorkouts$ = this.schedule$.pipe(
    map((ws) =>
      ws.filter(
        (w) => w.status === 'PENDING' && w.scheduledDate >= this.sevenDaysAgoKey && w.scheduledDate < this.todayKey,
      ),
    ),
    startWith([] as ScheduledWorkout[]),
  );

  upcomingWorkouts$ = this.schedule$.pipe(
    map((ws) => ws.filter((w) => w.status === 'PENDING' && w.scheduledDate >= this.todayKey)),
    startWith([] as ScheduledWorkout[]),
  );

  completedLastWeek$ = this.schedule$.pipe(
    map((ws) => ws.filter((w) => w.status === 'COMPLETED' && w.scheduledDate >= this.sevenDaysAgoKey)),
    startWith([] as ScheduledWorkout[]),
  );

  plannedLastWeek$ = this.schedule$.pipe(
    map((ws) => ws.filter((w) => w.scheduledDate >= this.sevenDaysAgoKey && w.scheduledDate <= this.todayKey)),
    startWith([] as ScheduledWorkout[]),
  );

  weekMetrics$ = this.historyService.sessions$.pipe(map((sessions) => computeWeekMetrics(sessions)));

  latestFit$ = this.historyService.sessions$.pipe(map((ss) => ss.find((s) => !!s.fitFileId) ?? null));

  pmcData$ = this.authService.user$.pipe(
    filter((u) => !!u),
    switchMap(() => {
      const from = new Date();
      from.setMonth(from.getMonth() - 3);
      return this.metricsService.getPmc(
        from.toISOString().split('T')[0],
        new Date().toISOString().split('T')[0],
      );
    }),
    startWith([] as PmcDataPoint[]),
    shareReplay(1),
  );

  sessionReminders$ = this.authService.user$.pipe(
    filter(u => !!u && u.role === 'COACH'),
    switchMap(() => this.coachService.getSessionReminders()),
    startWith([] as any[]),
  );

  formStats$ = combineLatest([this.pmcData$, this.authService.user$]).pipe(
    map(([pmcData, user]) => {
      const today = new Date().toISOString().split('T')[0];
      const real = pmcData.filter((p) => !p.predicted);
      const point = real.find((p) => p.date === today) ?? real[real.length - 1] ?? null;
      return {
        ctl: point?.ctl ?? null,
        atl: point?.atl ?? null,
        tsb: point?.tsb ?? null,
        ftp: user?.ftp ?? null,
      };
    }),
  );

  volume$ = this.authService.user$.pipe(
    filter((u) => !!u),
    switchMap(() => {
      const to = new Date();
      const from = subDays(to, 70);
      this.analyticsService.loadVolume(toDateKey(from), toDateKey(to), 'week');
      return this.analyticsService.volume$;
    }),
    startWith([] as VolumeEntry[]),
  );

  vm$ = combineLatest({
    overdue: this.overdueWorkouts$,
    upcoming: this.upcomingWorkouts$,
    completedLastWeek: this.completedLastWeek$,
    plannedLastWeek: this.plannedLastWeek$,
    weekMetrics: this.weekMetrics$,
    latestFit: this.latestFit$,
    form: this.formStats$,
    nextGoal: this.raceGoalService.nextGoal$,
    reminders: this.sessionReminders$,
    user: this.authService.user$,
    volume: this.volume$,
    pmc: this.pmcData$,
  });

  volumeMetric: 'time' | 'tss' | 'distance' = 'time';
  selectedScheduledWorkout: ScheduledWorkout | null = null;

  formatDuration(s: number): string {
    return formatTrainingDuration(s);
  }

  navigateToLinkTraining(session: any): void {
    this.router.navigate(['/clubs', session.clubId]);
  }

  openDetail(w: ScheduledWorkout): void {
    this.selectedScheduledWorkout = w;
  }

  onStartNow(_w: ScheduledWorkout): void {
    this.router.navigate(['/active-session']);
  }

  onDetailClosed(): void { this.selectedScheduledWorkout = null; }
  onDetailStarted(): void { this.selectedScheduledWorkout = null; }
  onDetailStatusChanged(): void {
    this.selectedScheduledWorkout = null;
    this.reload$.next();
  }

  hasAnyActivity(metrics: WeekMetrics): boolean {
    return metrics.current.some((s) => s.sessionCount > 0);
  }

  totalWeekSeconds(metrics: WeekMetrics): number {
    return metrics.current.reduce((a, s) => a + s.durationSeconds, 0);
  }

  totalPrevSeconds(metrics: WeekMetrics): number {
    return metrics.previous.reduce((a, s) => a + s.durationSeconds, 0);
  }

  weekTrendDelta(metrics: WeekMetrics): number | null {
    const prev = this.totalPrevSeconds(metrics);
    const curr = this.totalWeekSeconds(metrics);
    if (prev <= 0) return curr > 0 ? 100 : null;
    return ((curr - prev) / prev) * 100;
  }

  mixPercent(s: SportStats, metrics: WeekMetrics): number {
    const total = this.totalWeekSeconds(metrics);
    if (total <= 0) return 0;
    return (s.durationSeconds / total) * 100;
  }

  sportLabel(sport: string): string {
    switch (sport) {
      case 'CYCLING': return 'DASHBOARD.MIX_BIKE';
      case 'RUNNING': return 'DASHBOARD.MIX_RUN';
      case 'SWIMMING': return 'DASHBOARD.MIX_SWIM';
      default: return sport;
    }
  }

  donutSegments(metrics: WeekMetrics): { sport: string; dashArray: string; dashOffset: number }[] {
    const total = this.totalWeekSeconds(metrics);
    if (total <= 0) return [];
    const GAP = 1.5;
    let cursor = 0;
    return metrics.current
      .filter((s) => s.durationSeconds > 0)
      .map((s) => {
        const pct = (s.durationSeconds / total) * 100;
        const len = Math.max(0, pct - GAP);
        const seg = { sport: s.sport, dashArray: `${len} ${100 - len}`, dashOffset: -cursor };
        cursor += pct;
        return seg;
      });
  }

  /* ── KPI deltas (7-day) ── */
  ctlDelta(pmc: PmcDataPoint[]): number | null {
    return this.metricDelta(pmc, 'ctl');
  }
  atlDelta(pmc: PmcDataPoint[]): number | null {
    return this.metricDelta(pmc, 'atl');
  }

  hasDelta(delta: number | null): boolean {
    return delta != null && Math.abs(delta) >= 0.05;
  }

  deltaAbs(delta: number | null): number {
    return delta == null ? 0 : Math.abs(delta);
  }

  private metricDelta(pmc: PmcDataPoint[], key: 'ctl' | 'atl'): number | null {
    if (!pmc || pmc.length === 0) return null;
    const real = pmc.filter((p) => !p.predicted);
    if (real.length < 2) return null;
    const latest = real[real.length - 1];
    const sevenAgoDate = subDays(new Date(latest.date), 7).toISOString().split('T')[0];
    const past = real.find((p) => p.date >= sevenAgoDate);
    if (!past) return null;
    return (latest[key] ?? 0) - (past[key] ?? 0);
  }

  /* CTL ramp rate (% week-over-week change of CTL) */
  rampRate(pmc: PmcDataPoint[]): number | null {
    const delta = this.ctlDelta(pmc);
    if (delta == null) return null;
    const real = pmc.filter((p) => !p.predicted);
    if (real.length === 0) return null;
    const base = real[real.length - 1].ctl;
    if (!base || base <= 0) return null;
    return (delta / base) * 100;
  }

  tsbClass(tsb: number | null | undefined): string {
    if (tsb == null) return '';
    if (tsb > 5) return 'tsb-fresh';
    if (tsb < -10) return 'tsb-stressed';
    if (tsb < 0) return 'tsb-productive';
    return 'tsb-neutral';
  }

  tsbLabel(tsb: number | null | undefined): string {
    if (tsb == null) return 'DASHBOARD.KPI_NO_DATA';
    if (tsb > 5) return 'DASHBOARD.KPI_FORM_FRESH';
    if (tsb < -10) return 'DASHBOARD.KPI_FORM_STRESSED';
    if (tsb < 0) return 'DASHBOARD.KPI_FORM_PRODUCTIVE';
    return 'DASHBOARD.KPI_FORM_NEUTRAL';
  }

  /* Compliance = completed / (completed + overdue) */
  complianceStats(vm: { completedLastWeek: ScheduledWorkout[]; overdue: ScheduledWorkout[] }): { done: number; total: number } {
    const done = vm.completedLastWeek.length;
    const total = done + vm.overdue.length;
    return { done, total: total || done };
  }

  compliancePct(vm: { completedLastWeek: ScheduledWorkout[]; overdue: ScheduledWorkout[] }): number {
    const { done, total } = this.complianceStats(vm);
    if (total <= 0) return 100;
    return (done / total) * 100;
  }

  daysUntilGoal(goal: RaceGoal): number | null {
    return goal.race?.scheduledDate ? daysUntil(goal.race.scheduledDate) : null;
  }

  get greeting(): string {
    const h = new Date().getHours();
    if (h < 12) return this.translate.instant('DASHBOARD.GREETING_MORNING');
    if (h < 18) return this.translate.instant('DASHBOARD.GREETING_AFTERNOON');
    return this.translate.instant('DASHBOARD.GREETING_EVENING');
  }
}

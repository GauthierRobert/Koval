import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { filter, switchMap, map, shareReplay, startWith } from 'rxjs/operators';
import { AuthService } from '../../../services/auth.service';
import { CalendarService } from '../../../services/calendar.service';
import { HistoryService, SavedSession } from '../../../services/history.service';
import { MetricsService, PmcDataPoint } from '../../../services/metrics.service';
import { CoachService, ScheduledWorkout } from '../../../services/coach.service';
import { RaceGoal, RaceGoalService } from '../../../services/race-goal.service';
import { SportIconComponent } from '../../shared/sport-icon/sport-icon.component';
import { WorkoutDetailModalComponent } from '../../shared/workout-detail-modal/workout-detail-modal.component';
import { formatTrainingDuration, daysUntil } from '../../shared/format/format.utils';

function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function getStartOfWeek(d: Date): Date {
  const date = new Date(d);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day; // Monday
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
  imports: [CommonModule, RouterModule, SportIconComponent, WorkoutDetailModalComponent],
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
  private router = inject(Router);

  private readonly todayKey = toDateKey(new Date());
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

  weekMetrics$ = this.historyService.sessions$.pipe(map((sessions) => computeWeekMetrics(sessions)));

  latestFit$ = this.historyService.sessions$.pipe(map((ss) => ss.find((s) => !!s.fitFileId) ?? null));

  // Fetch the same PMC data the PMC page uses (last 90 days + 30 days ahead for projection)
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

  // Derive CTL/ATL/TSB from the live PMC data — same source as the PMC page
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

  vm$ = combineLatest({
    overdue: this.overdueWorkouts$,
    upcoming: this.upcomingWorkouts$,
    weekMetrics: this.weekMetrics$,
    latestFit: this.latestFit$,
    form: this.formStats$,
    nextGoal: this.raceGoalService.nextGoal$,
    reminders: this.sessionReminders$,
    user: this.authService.user$,
  });

  selectedScheduledWorkout: ScheduledWorkout | null = null;

  formatDuration(s: number): string {
    return formatTrainingDuration(s);
  }

  formatDistance(meters: number, sport: string): string {
    if (meters <= 0) return '';
    if (sport === 'SWIMMING') return `${Math.round(meters)}m`;
    return `${(meters / 1000).toFixed(1)} km`;
  }

  getTrend(curr: SportStats, prev: SportStats[]): '▲' | '▼' | '–' {
    const prevStats = prev.find((p) => p.sport === curr.sport);
    if (!prevStats || prevStats.tss === 0) return '–';
    if (curr.tss > prevStats.tss) return '▲';
    if (curr.tss < prevStats.tss) return '▼';
    return '–';
  }

  getTrendClass(curr: SportStats, prev: SportStats[]): string {
    const trend = this.getTrend(curr, prev);
    if (trend === '▲') return 'trend-up';
    if (trend === '▼') return 'trend-down';
    return '';
  }

  navigateToLinkTraining(session: any): void {
    this.router.navigate(['/clubs', session.clubId]);
  }

  openAnalysis(session: SavedSession): void {
    this.router.navigate(['/analysis', session.id]);
  }

  openDetail(w: ScheduledWorkout): void {
    this.selectedScheduledWorkout = w;
  }

  onDetailClosed(): void {
    this.selectedScheduledWorkout = null;
  }

  onDetailStarted(): void {
    this.selectedScheduledWorkout = null;
  }

  onDetailStatusChanged(): void {
    this.selectedScheduledWorkout = null;
    this.reload$.next();
  }

  formatScheduleDate(dateStr: string): string {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
  }

  formatScheduleDuration(workout: ScheduledWorkout): string {
    if (!workout.totalDurationSeconds) return workout.duration || '';
    const totalSec = workout.totalDurationSeconds;
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  totalWeekTss(metrics: WeekMetrics): number {
    return Math.round(metrics.current.reduce((a, s) => a + s.tss, 0));
  }

  totalPrevTss(metrics: WeekMetrics): number {
    return Math.round(metrics.previous.reduce((a, s) => a + s.tss, 0));
  }

  hasAnyActivity(metrics: WeekMetrics): boolean {
    return metrics.current.some((s) => s.sessionCount > 0);
  }

  daysUntilGoal(goal: RaceGoal): number {
    return daysUntil(goal.raceDate);
  }

  getGoalPriorityColor(priority: string): string {
    return this.raceGoalService.getPriorityColor(priority);
  }

  trackWorkoutById(_index: number, workout: ScheduledWorkout): string {
    return workout.id;
  }

  trackBySport(_index: number, stat: SportStats): string {
    return stat.sport;
  }
}

import {ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest, Observable} from 'rxjs';
import {filter, map, shareReplay, startWith, switchMap} from 'rxjs/operators';
import {Router} from '@angular/router';
import {ResponsiveService} from '../../../services/responsive.service';

import {CalendarClubSession, CalendarService} from '../../../services/calendar.service';
import {AuthService} from '../../../services/auth.service';
import {ScheduledWorkout} from '../../../services/coach.service';
import {TrainingActionModalComponent} from '../../shared/training-action-modal/training-action-modal.component';
import {WorkoutDetailModalComponent} from '../../shared/workout-detail-modal/workout-detail-modal.component';
import {HistoryService, SavedSession} from '../../../services/history.service';
import {CalendarWeekViewComponent} from './week-view/calendar-week-view.component';
import {CalendarMonthViewComponent} from './month-view/calendar-month-view.component';
import {RaceGoal, RaceGoalService} from '../../../services/race-goal.service';
import {ClubGroup, ClubService, MyClubRoleEntry} from '../../../services/club.service';
import {ClubSessionService} from '../../../services/club-session.service';
import {PlanService} from '../../../services/plan.service';
import {TrainingPlan} from '../../../models/plan.model';
import {
  BannersByRow,
  buildEntriesByDay,
  buildMonth,
  buildWeek,
  CalendarDay,
  ClubCalendarPreferences,
  computeBannerSegments,
  computeVisiblePlans,
  DAYS_IN_WEEK,
  EntriesByDay,
  groupByDay,
  toDateKey,
  VisiblePlan,
  WorkoutsByDay,
} from './calendar.utils';

export {toDateKey} from './calendar.utils';
export type {
  CalendarDay,
  CalendarEntry,
  ClubSessionEntry,
  EntriesByDay,
  FusedEntry,
  PlanBannerSegment,
  BannersByRow,
  ScheduledEntry,
  StandaloneEntry,
  VisiblePlan,
  WorkoutsByDay,
} from './calendar.utils';

export type GoalsByDay = Map<string, RaceGoal[]>;

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    TrainingActionModalComponent,
    WorkoutDetailModalComponent,
    CalendarWeekViewComponent,
    CalendarMonthViewComponent,
  ],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarComponent implements OnInit {
  readonly emptyMap: WorkoutsByDay = new Map();
  readonly emptyGoalsMap: GoalsByDay = new Map();
  readonly emptyBannersMap: BannersByRow = new Map();

  weekDays: CalendarDay[] = [];
  monthDays: CalendarDay[] = [];
  viewMode: 'week' | 'month' = 'week';
  startDate!: Date;
  endDate!: Date;

  entriesByDay$!: Observable<EntriesByDay>;
  scheduleByDay$!: Observable<WorkoutsByDay>;
  goalsByDay$!: Observable<GoalsByDay>;
  visiblePlans$!: Observable<VisiblePlan[]>;
  bannersByRow$!: Observable<BannersByRow>;

  isScheduleModalOpen = false;
  selectedDate: string | null = null;
  selectedWorkout: ScheduledWorkout | null = null;

  // Club session preferences
  showPrefsDropdown = false;
  clubPrefs: ClubCalendarPreferences = { hiddenClubIds: [], hiddenGroupIds: [] };
  myClubRoles: MyClubRoleEntry[] = [];
  clubGroupsMap: Map<string, ClubGroup[]> = new Map();

  readonly reload$ = new BehaviorSubject<void>(undefined);

  private readonly calendarService = inject(CalendarService);
  private readonly authService = inject(AuthService);
  private readonly historyService = inject(HistoryService);
  private readonly raceGoalService = inject(RaceGoalService);
  private readonly clubService = inject(ClubService);
  private readonly clubSessionService = inject(ClubSessionService);
  private readonly planService = inject(PlanService);
  private readonly router = inject(Router);
  private readonly responsive = inject(ResponsiveService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  private savedWeekDate: Date | null = null;
  private savedMonthDate: Date | null = null;

  isMobile = false;

  constructor() {
    // BreakpointObserver emits synchronously on subscribe with the current state,
    // so isMobile is populated before ngOnInit runs.
    this.responsive.isMobile$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((isMobile) => {
        const wasMobile = this.isMobile;
        this.isMobile = isMobile;
        if (this.isMobile && !wasMobile && this.viewMode === 'month') {
          this.setViewMode('week');
        }
        this.cdr.markForCheck();
      });
  }

  ngOnInit(): void {
    if (this.isMobile) {
      this.viewMode = 'week';
    }
    this.setWeek(new Date());
    this.loadPreferences();

    const user$ = this.authService.user$.pipe(filter((u) => !!u));

    const trigger$ = combineLatest([user$, this.reload$]);

    const schedule$ = trigger$.pipe(
      switchMap(() => this.calendarService.getMySchedule(this.startDateKey(), this.endDateKey())),
      shareReplay(1)
    );

    const sessions$ = trigger$.pipe(
      switchMap(() => this.calendarService.getSessionsForCalendar(this.startDateKey(), this.endDateKey())),
      shareReplay(1)
    );

    const clubSessions$ = trigger$.pipe(
      switchMap(() => this.calendarService.getClubSessionsForCalendar(this.startDateKey(), this.endDateKey())),
      map(sessions => this.filterByPreferences(sessions)),
      startWith([] as CalendarClubSession[]),
      shareReplay(1)
    );

    this.scheduleByDay$ = schedule$.pipe(map(groupByDay));

    this.entriesByDay$ = combineLatest([schedule$, sessions$, clubSessions$]).pipe(
      map(([sched, sess, clubSess]) => buildEntriesByDay(sched, sess, clubSess))
    );

    this.goalsByDay$ = this.raceGoalService.goals$.pipe(
      map((goals) => {
        const byDay: GoalsByDay = new Map();
        for (const g of goals) {
          const date = g.race?.scheduledDate;
          if (!date) continue;
          const list = byDay.get(date);
          if (list) { list.push(g); } else { byDay.set(date, [g]); }
        }
        return byDay;
      })
    );
    this.visiblePlans$ = combineLatest([this.planService.plans$, this.reload$]).pipe(
      map(([plans]) => computeVisiblePlans(plans, this.startDate, this.endDate))
    );

    this.bannersByRow$ = combineLatest([this.planService.plans$, this.reload$]).pipe(
      map(([plans]) => computeBannerSegments(plans, this.monthDays))
    );

    this.raceGoalService.loadGoals();
    this.loadClubPrefsData();
  }

  goToToday(): void {
    if (this.viewMode === 'week') { this.setWeek(new Date()); } else { this.setMonth(new Date()); }
    this.reload$.next();
  }

  navigateWeek(direction: -1 | 1): void {
    const base = new Date(this.startDate);
    if (this.viewMode === 'week') {
      base.setDate(base.getDate() + direction * DAYS_IN_WEEK);
      this.setWeek(base);
    } else {
      base.setMonth(base.getMonth() + direction);
      this.setMonth(base);
    }
    this.reload$.next();
  }

  setViewMode(mode: 'week' | 'month'): void {
    if (this.viewMode === mode) return;
    if (this.viewMode === 'week') {
      this.savedWeekDate = new Date(this.startDate);
      this.viewMode = 'month';
      this.setMonth(this.savedMonthDate ?? new Date());
    } else {
      this.savedMonthDate = new Date(this.startDate);
      this.viewMode = 'week';
      this.setWeek(this.savedWeekDate ?? new Date());
    }
    this.reload$.next();
  }

  openScheduleModal(day: CalendarDay): void {
    this.selectedDate = day.key;
    this.isScheduleModalOpen = true;
  }

  onScheduled(): void {
    this.isScheduleModalOpen = false;
    this.reload$.next();
  }

  markComplete(workout: ScheduledWorkout): void {
    this.calendarService.markCompleted(workout.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  markSkipped(workout: ScheduledWorkout): void {
    this.calendarService.markSkipped(workout.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  deleteWorkout(workout: ScheduledWorkout): void {
    this.calendarService.deleteScheduledWorkout(workout.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  onReschedule(workout: ScheduledWorkout, newDate: string): void {
    this.calendarService.rescheduleWorkout(workout.id, newDate)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  selectWorkout(workout: ScheduledWorkout): void { this.selectedWorkout = workout; }

  selectSession(session: SavedSession): void {
    this.router.navigate(['/history', session.id]);
  }

  onDetailClosed(): void { this.selectedWorkout = null; }
  onDetailStarted(): void { this.selectedWorkout = null; }
  onDetailStatusChanged(): void { this.selectedWorkout = null; this.reload$.next(); }

  // --- Club session handlers ---

  joinClubSession(session: CalendarClubSession): void {
    this.clubSessionService.joinSession(session.clubId, session.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  cancelClubSession(session: CalendarClubSession): void {
    this.clubSessionService.cancelSession(session.clubId, session.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  // --- Preferences ---

  togglePrefsDropdown(): void {
    this.showPrefsDropdown = !this.showPrefsDropdown;
  }

  isClubVisible(clubId: string): boolean {
    return !this.clubPrefs.hiddenClubIds.includes(clubId);
  }

  toggleClub(clubId: string): void {
    const idx = this.clubPrefs.hiddenClubIds.indexOf(clubId);
    if (idx >= 0) {
      this.clubPrefs.hiddenClubIds.splice(idx, 1);
    } else {
      this.clubPrefs.hiddenClubIds.push(clubId);
    }
    this.savePreferences();
    this.reload$.next();
  }

  isGroupVisible(groupId: string): boolean {
    return !this.clubPrefs.hiddenGroupIds.includes(groupId);
  }

  toggleGroup(groupId: string): void {
    const idx = this.clubPrefs.hiddenGroupIds.indexOf(groupId);
    if (idx >= 0) {
      this.clubPrefs.hiddenGroupIds.splice(idx, 1);
    } else {
      this.clubPrefs.hiddenGroupIds.push(groupId);
    }
    this.savePreferences();
    this.reload$.next();
  }

  private filterByPreferences(sessions: CalendarClubSession[]): CalendarClubSession[] {
    return sessions.filter(s => {
      if (this.clubPrefs.hiddenClubIds.includes(s.clubId)) return false;
      if (s.clubGroupId && this.clubPrefs.hiddenGroupIds.includes(s.clubGroupId)) return false;
      return true;
    });
  }

  private loadPreferences(): void {
    try {
      const stored = localStorage.getItem('calendarClubPrefs');
      if (stored) {
        this.clubPrefs = JSON.parse(stored);
      }
    } catch { /* use defaults */ }
  }

  private savePreferences(): void {
    localStorage.setItem('calendarClubPrefs', JSON.stringify(this.clubPrefs));
  }

  private loadClubPrefsData(): void {
    this.clubService.myClubRoles$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(roles => {
      this.myClubRoles = roles;
      for (const r of roles) {
        this.clubService.getClubGroups(r.clubId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(groups => {
          this.clubGroupsMap.set(r.clubId, groups);
          this.cdr.markForCheck();
        });
      }
      this.cdr.markForCheck();
    });
    this.clubService.loadMyClubRoles();
  }

  private startDateKey(): string {
    return this.viewMode === 'week' ? this.weekDays[0].key : this.monthDays[0].key;
  }

  private endDateKey(): string {
    return this.viewMode === 'week' ? this.weekDays[6].key : this.monthDays[this.monthDays.length - 1].key;
  }

  private setWeek(baseDate: Date): void {
    this.weekDays = buildWeek(baseDate);
    this.startDate = this.weekDays[0].date;
    this.endDate = this.weekDays[6].date;
  }

  private setMonth(baseDate: Date): void {
    const {days, startOfMonth, endOfMonth} = buildMonth(baseDate);
    this.monthDays = days;
    this.startDate = startOfMonth;
    this.endDate = endOfMonth;
  }
}

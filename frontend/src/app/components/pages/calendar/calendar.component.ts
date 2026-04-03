import {ChangeDetectionStrategy, Component, HostListener, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest, Observable} from 'rxjs';
import {filter, map, shareReplay, startWith, switchMap, tap} from 'rxjs/operators';
import {Router} from '@angular/router';

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

export interface CalendarDay {
  date: Date;
  key: string;
  isToday: boolean;
}

export interface ScheduledEntry { kind: 'scheduled'; scheduled: ScheduledWorkout; }
export interface FusedEntry      { kind: 'fused'; scheduled: ScheduledWorkout; session: SavedSession; }
export interface StandaloneEntry { kind: 'standalone'; session: SavedSession; }
export interface ClubSessionEntry { kind: 'club-session'; clubSession: CalendarClubSession; linkedSession?: SavedSession; }
export type CalendarEntry = ScheduledEntry | FusedEntry | StandaloneEntry | ClubSessionEntry;

export interface ClubCalendarPreferences {
  hiddenClubIds: string[];
  hiddenGroupIds: string[];
}
export type EntriesByDay = Map<string, CalendarEntry[]>;
export type WorkoutsByDay = Map<string, ScheduledWorkout[]>;
export type GoalsByDay = Map<string, RaceGoal[]>;

export interface VisiblePlan {
  id: string;
  title: string;
  currentWeek: number;
  totalWeeks: number;
  weekLabel: string | null;
  sportType: string;
}

export interface PlanBannerSegment {
  planId: string;
  title: string;
  sportType: string;
  startCol: number;   // 1-7 (CSS grid 1-based)
  spanCols: number;
  row: number;        // 0-5 (which week-row in month grid)
  isStart: boolean;   // plan starts in this row → left rounded corner
  isEnd: boolean;     // plan ends in this row → right rounded corner
  weekNumber: number;
  weekLabel: string | null;
}
export type BannersByRow = Map<number, PlanBannerSegment[]>;

const DAYS_IN_WEEK = 7;

export function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function buildWeek(baseDate: Date): CalendarDay[] {
  const monday = new Date(baseDate);
  const dayOfWeek = monday.getDay();
  const offset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  monday.setDate(baseDate.getDate() + offset);
  const todayStr = new Date().toDateString();

  return Array.from({ length: DAYS_IN_WEEK }, (_, i) => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + i);
    return { date, key: toDateKey(date), isToday: date.toDateString() === todayStr };
  });
}

function groupByDay(schedule: ScheduledWorkout[]): WorkoutsByDay {
  const byDay: WorkoutsByDay = new Map();
  for (const w of schedule) {
    const list = byDay.get(w.scheduledDate);
    if (list) { list.push(w); } else { byDay.set(w.scheduledDate, [w]); }
  }
  return byDay;
}

function buildEntriesByDay(scheduled: ScheduledWorkout[], sessions: SavedSession[], clubSessions: CalendarClubSession[] = []): EntriesByDay {
  const byDay: EntriesByDay = new Map();
  const sessionById = new Map(sessions.map(s => [s.id, s]));
  const consumed = new Set<string>();

  for (const sw of scheduled) {
    if (!byDay.has(sw.scheduledDate)) byDay.set(sw.scheduledDate, []);
    if (sw.status === 'COMPLETED' && sw.sessionId && sessionById.has(sw.sessionId)) {
      consumed.add(sw.sessionId);
      byDay.get(sw.scheduledDate)!.push({ kind: 'fused', scheduled: sw, session: sessionById.get(sw.sessionId)! });
    } else {
      byDay.get(sw.scheduledDate)!.push({ kind: 'scheduled', scheduled: sw });
    }
  }

  // Build reverse map from clubSessionId to SavedSession
  const sessionByClubSessionId = new Map<string, SavedSession>();
  for (const sess of sessions) {
    if (sess.clubSessionId) {
      sessionByClubSessionId.set(sess.clubSessionId, sess);
      consumed.add(sess.id);
    }
  }

  for (const sess of sessions) {
    if (consumed.has(sess.id)) continue;
    const key = toDateKey(new Date(sess.date));
    if (!byDay.has(key)) byDay.set(key, []);
    byDay.get(key)!.push({ kind: 'standalone', session: sess });
  }

  for (const cs of clubSessions) {
    if (!cs.scheduledAt) continue;
    const key = cs.scheduledAt.split('T')[0];
    if (!byDay.has(key)) byDay.set(key, []);
    const linkedSession = sessionByClubSessionId.get(cs.id);
    byDay.get(key)!.push({ kind: 'club-session', clubSession: cs, linkedSession });
  }

  return byDay;
}

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

  private userId = '';
  private savedWeekDate: Date | null = null;
  private savedMonthDate: Date | null = null;

  isMobile = window.innerWidth <= 768;

  @HostListener('window:resize')
  onResize(): void {
    const wasMobile = this.isMobile;
    this.isMobile = window.innerWidth <= 768;
    if (this.isMobile && !wasMobile && this.viewMode === 'month') {
      this.setViewMode('week');
    }
  }

  ngOnInit(): void {
    if (this.isMobile) {
      this.viewMode = 'week';
    }
    this.setWeek(new Date());
    this.loadPreferences();

    const user$ = this.authService.user$.pipe(
      filter((u) => !!u),
      tap((user) => (this.userId = user!.id))
    );

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
          const list = byDay.get(g.raceDate);
          if (list) { list.push(g); } else { byDay.set(g.raceDate, [g]); }
        }
        return byDay;
      })
    );
    this.visiblePlans$ = combineLatest([this.planService.plans$, this.reload$]).pipe(
      map(([plans]) => this.computeVisiblePlans(plans))
    );

    this.bannersByRow$ = combineLatest([this.planService.plans$, this.reload$]).pipe(
      map(([plans]) => this.computeBannerSegments(plans, this.monthDays))
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
    this.calendarService.markCompleted(workout.id).subscribe(() => this.reload$.next());
  }

  markSkipped(workout: ScheduledWorkout): void {
    this.calendarService.markSkipped(workout.id).subscribe(() => this.reload$.next());
  }

  deleteWorkout(workout: ScheduledWorkout): void {
    this.calendarService.deleteScheduledWorkout(workout.id).subscribe(() => this.reload$.next());
  }

  onReschedule(workout: ScheduledWorkout, newDate: string): void {
    this.calendarService.rescheduleWorkout(workout.id, newDate).subscribe(() => this.reload$.next());
  }

  selectWorkout(workout: ScheduledWorkout): void { this.selectedWorkout = workout; }

  selectSession(session: SavedSession): void {
    this.historyService.setSelectedSession(session);
    this.router.navigate(['/history']);
  }

  onDetailClosed(): void { this.selectedWorkout = null; }
  onDetailStarted(): void { this.selectedWorkout = null; }
  onDetailStatusChanged(): void { this.selectedWorkout = null; this.reload$.next(); }

  // --- Club session handlers ---

  joinClubSession(session: CalendarClubSession): void {
    this.clubSessionService.joinSession(session.clubId, session.id).subscribe(() => this.reload$.next());
  }

  cancelClubSession(session: CalendarClubSession): void {
    this.clubSessionService.cancelSession(session.clubId, session.id).subscribe(() => this.reload$.next());
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
    this.clubService.myClubRoles$.subscribe(roles => {
      this.myClubRoles = roles;
      for (const r of roles) {
        this.clubService.getClubGroups(r.clubId).subscribe(groups => {
          this.clubGroupsMap.set(r.clubId, groups);
        });
      }
    });
    this.clubService.loadMyClubRoles();
  }

  private computeBannerSegments(plans: TrainingPlan[], monthDays: CalendarDay[]): BannersByRow {
    if (!monthDays.length) return new Map();

    const result: BannersByRow = new Map();
    const MS_PER_DAY = 1000 * 60 * 60 * 24;

    const eligiblePlans = plans.filter(
      (p) => (p.status === 'ACTIVE' || p.status === 'COMPLETED' || p.status === 'PAUSED') && p.startDate
    );

    for (const plan of eligiblePlans) {
      const planStart = new Date(plan.startDate);
      const planEnd = new Date(planStart);
      planEnd.setDate(planStart.getDate() + plan.durationWeeks * 7 - 1);

      for (let row = 0; row < 6; row++) {
        const rowStart = monthDays[row * 7].date;
        const rowEnd = monthDays[row * 7 + 6].date;

        if (planStart > rowEnd || planEnd < rowStart) continue;

        const segStart = planStart > rowStart ? planStart : rowStart;
        const segEnd = planEnd < rowEnd ? planEnd : rowEnd;

        const startCol = Math.round((segStart.getTime() - rowStart.getTime()) / MS_PER_DAY) + 1;
        const spanCols = Math.round((segEnd.getTime() - segStart.getTime()) / MS_PER_DAY) + 1;

        const isStart = planStart >= rowStart && planStart <= rowEnd;
        const isEnd = planEnd >= rowStart && planEnd <= rowEnd;

        const daysSincePlanStart = Math.floor((segStart.getTime() - planStart.getTime()) / MS_PER_DAY);
        const weekNumber = Math.floor(daysSincePlanStart / 7) + 1;
        const week = plan.weeks?.find((w) => w.weekNumber === weekNumber);

        const seg: PlanBannerSegment = {
          planId: plan.id,
          title: plan.title,
          sportType: plan.sportType,
          startCol,
          spanCols,
          row,
          isStart,
          isEnd,
          weekNumber,
          weekLabel: week?.label ?? null,
        };

        if (!result.has(row)) result.set(row, []);
        result.get(row)!.push(seg);
      }
    }

    return result;
  }

  private computeVisiblePlans(plans: TrainingPlan[]): VisiblePlan[] {
    const viewStart = this.startDate;
    const viewEnd = this.endDate;
    if (!viewStart || !viewEnd) return [];

    return plans
      .filter(p => p.status === 'ACTIVE' || p.status === 'COMPLETED' || p.status === 'PAUSED')
      .filter(p => {
        if (!p.startDate) return false;
        const planStart = new Date(p.startDate);
        const planEnd = new Date(planStart);
        planEnd.setDate(planEnd.getDate() + p.durationWeeks * 7 - 1);
        return planStart <= viewEnd && planEnd >= viewStart;
      })
      .map(p => {
        const planStart = new Date(p.startDate);
        const daysSinceStart = Math.floor((viewStart.getTime() - planStart.getTime()) / (1000 * 60 * 60 * 24));
        const currentWeek = Math.max(1, Math.min(p.durationWeeks, Math.floor(daysSinceStart / 7) + 1));
        const week = p.weeks?.find(w => w.weekNumber === currentWeek);
        return {
          id: p.id,
          title: p.title,
          currentWeek,
          totalWeeks: p.durationWeeks,
          weekLabel: week?.label ?? null,
          sportType: p.sportType,
        };
      });
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
    const startOfMonth = new Date(baseDate.getFullYear(), baseDate.getMonth(), 1);
    const dayOfWeek = startOfMonth.getDay();
    const offset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
    const startGrid = new Date(startOfMonth);
    startGrid.setDate(startOfMonth.getDate() + offset);

    const days: CalendarDay[] = [];
    const todayStr = new Date().toDateString();
    for (let i = 0; i < 42; i++) {
      const date = new Date(startGrid);
      date.setDate(startGrid.getDate() + i);
      days.push({ date, key: toDateKey(date), isToday: date.toDateString() === todayStr });
    }
    this.monthDays = days;
    this.startDate = startOfMonth;
    this.endDate = new Date(baseDate.getFullYear(), baseDate.getMonth() + 1, 0);
  }
}

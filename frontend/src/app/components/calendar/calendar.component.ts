import {ChangeDetectionStrategy, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject, combineLatest, Observable, of} from 'rxjs';
import {filter, map, shareReplay, switchMap, tap} from 'rxjs/operators';
import {CdkDrag, CdkDragDrop, CdkDropList} from '@angular/cdk/drag-drop';
import {Router} from '@angular/router';

import {CalendarService} from '../../services/calendar.service';
import {AuthService} from '../../services/auth.service';
import {ScheduledWorkout} from '../../services/coach.service';
import {TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType,} from '../../services/training.service';
import {ScheduleModalComponent} from '../schedule-modal/schedule-modal.component';
import {WorkoutDetailModalComponent} from '../workout-detail-modal/workout-detail-modal.component';
import {SportIconComponent} from '../sport-icon/sport-icon.component';
import {TrainingLoadChartComponent} from '../training-load-chart/training-load-chart.component';
import {HistoryService, SavedSession} from '../../services/history.service';

export interface CalendarDay {
  date: Date;
  key: string; // 'YYYY-MM-DD' for map lookup
  isToday: boolean;
}

export interface ScheduledEntry { kind: 'scheduled'; scheduled: ScheduledWorkout; }
export interface FusedEntry      { kind: 'fused'; scheduled: ScheduledWorkout; session: SavedSession; }
export interface StandaloneEntry { kind: 'standalone'; session: SavedSession; }
export type CalendarEntry = ScheduledEntry | FusedEntry | StandaloneEntry;
export type EntriesByDay = Map<string, CalendarEntry[]>;

type WorkoutsByDay = Map<string, ScheduledWorkout[]>;

const DAYS_IN_WEEK = 7;

export function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function buildWeek(baseDate: Date): CalendarDay[] {
  const monday = new Date(baseDate);
  // getDay(): 0=Sun, 1=Mon, ... 6=Sat → offset so Monday=0
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
    if (list) {
      list.push(w);
    } else {
      byDay.set(w.scheduledDate, [w]);
    }
  }
  return byDay;
}

function buildEntriesByDay(scheduled: ScheduledWorkout[], sessions: SavedSession[]): EntriesByDay {
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

  for (const sess of sessions) {
    if (consumed.has(sess.id)) continue;
    const key = toDateKey(new Date(sess.date));
    if (!byDay.has(key)) byDay.set(key, []);
    byDay.get(key)!.push({ kind: 'standalone', session: sess });
  }
  return byDay;
}

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule, CdkDropList, CdkDrag, ScheduleModalComponent, WorkoutDetailModalComponent, SportIconComponent, TrainingLoadChartComponent],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarComponent implements OnInit {
  readonly EMPTY: CalendarEntry[] = [];
  readonly emptyMap: WorkoutsByDay = new Map();

  weekDays: CalendarDay[] = [];
  monthDays: CalendarDay[] = [];
  viewMode: 'week' | 'month' = 'week';
  startDate!: Date;
  endDate!: Date;

  entriesByDay$!: Observable<EntriesByDay>;
  scheduleByDay$!: Observable<WorkoutsByDay>;
  overdueWorkouts$!: Observable<ScheduledWorkout[]>;

  isScheduleModalOpen = false;
  selectedDate: string | null = null;
  selectedWorkout: ScheduledWorkout | null = null;

  linkPickerState: { sessionId: string } | null = null;
  nearbyScheduled$: Observable<ScheduledWorkout[]> = of([]);

  private readonly calendarService = inject(CalendarService);
  private readonly authService = inject(AuthService);
  private readonly historyService = inject(HistoryService);
  private readonly router = inject(Router);

  private userId = '';
  private readonly reload$ = new BehaviorSubject<void>(undefined);
  private savedWeekDate: Date | null = null;
  private savedMonthDate: Date | null = null;

  ngOnInit(): void {
    this.setWeek(new Date());

    const user$ = this.authService.user$.pipe(
      filter((u) => !!u),
      tap((user) => (this.userId = user!.id))
    );

    const trigger$ = combineLatest([user$, this.reload$]);

    const schedule$ = trigger$.pipe(
      switchMap(() =>
        this.calendarService.getMySchedule(this.startDateKey(), this.endDateKey())
      ),
      shareReplay(1)
    );

    const sessions$ = trigger$.pipe(
      switchMap(() =>
        this.calendarService.getSessionsForCalendar(this.startDateKey(), this.endDateKey())
      ),
      shareReplay(1)
    );

    this.scheduleByDay$ = schedule$.pipe(map(groupByDay));

    this.entriesByDay$ = combineLatest([schedule$, sessions$]).pipe(
      map(([sched, sess]) => buildEntriesByDay(sched, sess))
    );

    const today = toDateKey(new Date());
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    const sevenDaysAgoKey = toDateKey(sevenDaysAgo);
    this.overdueWorkouts$ = schedule$.pipe(
      map((workouts) =>
        workouts.filter(
          (w) => w.status === 'PENDING' && w.scheduledDate >= sevenDaysAgoKey && w.scheduledDate < today
        )
      )
    );
  }

  goToToday(): void {
    if (this.viewMode === 'week') {
      this.setWeek(new Date());
    } else {
      this.setMonth(new Date());
    }
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

  markComplete(workout: ScheduledWorkout): void {
    this.calendarService.markCompleted(workout.id).subscribe(() => this.reload$.next());
  }

  markSkipped(workout: ScheduledWorkout): void {
    this.calendarService.markSkipped(workout.id).subscribe(() => this.reload$.next());
  }

  selectWorkout(workout: ScheduledWorkout): void {
    this.selectedWorkout = workout;
  }

  selectSession(session: SavedSession): void {
    this.historyService.setSelectedSession(session);
    this.router.navigate(['/history']);
  }

  openLinkPicker(session: SavedSession): void {
    this.linkPickerState = { sessionId: session.id };
    const d = new Date(session.date);
    const from = new Date(d); from.setDate(d.getDate() - 3);
    const to   = new Date(d); to.setDate(d.getDate() + 3);
    this.nearbyScheduled$ = this.calendarService
      .getMySchedule(toDateKey(from), toDateKey(to)).pipe(
        map(ws => ws.filter(w => w.status === 'PENDING' && !w.sessionId))
      );
  }

  confirmLink(scheduledWorkoutId: string): void {
    if (!this.linkPickerState) return;
    this.calendarService.linkSessionToSchedule(this.linkPickerState.sessionId, scheduledWorkoutId)
      .subscribe(() => { this.linkPickerState = null; this.reload$.next(); });
  }

  onDetailClosed(): void {
    this.selectedWorkout = null;
  }

  onDetailStarted(): void {
    this.selectedWorkout = null;
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

  private startDateKey(): string {
    return this.viewMode === 'week' ? this.weekDays[0].key : this.monthDays[0].key;
  }

  private endDateKey(): string {
    return this.viewMode === 'week' ? this.weekDays[6].key : this.monthDays[this.monthDays.length - 1].key;
  }

  onDetailStatusChanged(): void {
    this.selectedWorkout = null;
    this.reload$.next();
  }

  openScheduleModal(day: CalendarDay): void {
    this.selectedDate = day.key;
    this.isScheduleModalOpen = true;
  }

  deleteWorkout(workout: ScheduledWorkout): void {
    this.calendarService
      .deleteScheduledWorkout(workout.id)
      .subscribe(() => this.reload$.next());
  }

  onScheduled(): void {
    this.isScheduleModalOpen = false;
    this.reload$.next();
  }

  formatDuration(workout: ScheduledWorkout): string {
    if (!workout.totalDurationSeconds) return workout.duration || '-';

    const totalSec = workout.totalDurationSeconds;
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  formatSessionDuration(session: SavedSession): string {
    const totalSec = session.totalDuration;
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  trackByDay(_: number, day: CalendarDay): string {
    return day.key;
  }

  trackByEntry(_: number, e: CalendarEntry): string {
    if (e.kind === 'fused') return 'fused-' + e.scheduled.id;
    if (e.kind === 'scheduled') return 'sw-' + e.scheduled.id;
    return 'sess-' + e.session.id;
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  isFutureDate(dateKey: string): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const d = new Date(dateKey + 'T00:00:00');
    return d > today;
  }

  onDrop(event: CdkDragDrop<CalendarEntry[]>, targetDay: CalendarDay): void {
    const workout: ScheduledWorkout = event.item.data;
    if (!workout || workout.scheduledDate === targetDay.key) return;
    this.calendarService.rescheduleWorkout(workout.id, targetDay.key).subscribe(() =>
      this.reload$.next()
    );
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
    // 6 weeks to ensure we cover the whole month
    for (let i = 0; i < 42; i++) {
      const date = new Date(startGrid);
      date.setDate(startGrid.getDate() + i);
      days.push({
        date,
        key: toDateKey(date),
        isToday: date.toDateString() === todayStr
      });
    }
    this.monthDays = days;
    this.startDate = startOfMonth;
    this.endDate = new Date(baseDate.getFullYear(), baseDate.getMonth() + 1, 0);
  }
}

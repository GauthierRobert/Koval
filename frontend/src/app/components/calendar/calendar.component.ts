import {ChangeDetectionStrategy, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject, Observable} from 'rxjs';
import {filter, map, shareReplay, switchMap, tap} from 'rxjs/operators';
import {CdkDrag, CdkDragDrop, CdkDropList} from '@angular/cdk/drag-drop';

import {CalendarService} from '../../services/calendar.service';
import {AuthService} from '../../services/auth.service';
import {ScheduledWorkout} from '../../services/coach.service';
import {TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType,} from '../../services/training.service';
import {ScheduleModalComponent} from '../schedule-modal/schedule-modal.component';
import {WorkoutDetailModalComponent} from '../workout-detail-modal/workout-detail-modal.component';
import {SportIconComponent} from '../sport-icon/sport-icon.component';
import {TrainingLoadChartComponent} from '../training-load-chart/training-load-chart.component';

export interface CalendarDay {
  date: Date;
  key: string; // 'YYYY-MM-DD' for map lookup
  isToday: boolean;
}

type WorkoutsByDay = Map<string, ScheduledWorkout[]>;

const DAYS_IN_WEEK = 7;

function toDateKey(d: Date): string {
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

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule, CdkDropList, CdkDrag, ScheduleModalComponent, WorkoutDetailModalComponent, SportIconComponent, TrainingLoadChartComponent],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarComponent implements OnInit {
  readonly EMPTY: ScheduledWorkout[] = [];

  weekDays: CalendarDay[] = [];
  monthDays: CalendarDay[] = [];
  viewMode: 'week' | 'month' = 'week';
  startDate!: Date;
  endDate!: Date;

  workoutsByDay$!: Observable<WorkoutsByDay>;
  overdueWorkouts$!: Observable<ScheduledWorkout[]>;

  isScheduleModalOpen = false;
  selectedDate: string | null = null;
  selectedWorkout: ScheduledWorkout | null = null;

  private readonly calendarService = inject(CalendarService);
  private readonly authService = inject(AuthService);

  private userId = '';
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  ngOnInit(): void {
    this.setWeek(new Date());

    const schedule$ = this.authService.user$.pipe(
      filter((u) => !!u),
      tap((user) => (this.userId = user!.id)),
      switchMap(() => this.reload$),
      switchMap(() =>
        this.calendarService.getMySchedule(this.startDateKey(), this.endDateKey())
      ),
      shareReplay(1)
    );

    this.workoutsByDay$ = schedule$.pipe(map(groupByDay));

    const todayKey = toDateKey(new Date());
    this.overdueWorkouts$ = schedule$.pipe(
      map((workouts) =>
        workouts.filter((w) => w.status === 'PENDING' && w.scheduledDate < todayKey)
      )
    );
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

  onDetailClosed(): void {
    this.selectedWorkout = null;
  }

  onDetailStarted(): void {
    this.selectedWorkout = null;
  }

  setViewMode(mode: 'week' | 'month'): void {
    if (this.viewMode === mode) return;
    this.viewMode = mode;
    this.refreshView();
  }

  private refreshView(): void {
    if (this.viewMode === 'week') {
      this.setWeek(this.startDate);
    } else {
      this.setMonth(this.startDate);
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

  trackByDay(_: number, day: CalendarDay): string {
    return day.key;
  }

  trackByWorkout(_: number, workout: ScheduledWorkout): string {
    return workout.id;
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

  onDrop(event: CdkDragDrop<ScheduledWorkout[]>, targetDay: CalendarDay): void {
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

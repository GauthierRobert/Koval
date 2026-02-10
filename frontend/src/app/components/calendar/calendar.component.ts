import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, map, switchMap, tap } from 'rxjs/operators';

import { CalendarService } from '../../services/calendar.service';
import { AuthService } from '../../services/auth.service';
import { ScheduledWorkout } from '../../services/coach.service';
import { ScheduleModalComponent } from '../schedule-modal/schedule-modal.component';
import { WorkoutDetailModalComponent } from '../workout-detail-modal/workout-detail-modal.component';

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
  const sunday = new Date(baseDate);
  sunday.setDate(baseDate.getDate() - baseDate.getDay());
  const todayStr = new Date().toDateString();

  return Array.from({ length: DAYS_IN_WEEK }, (_, i) => {
    const date = new Date(sunday);
    date.setDate(sunday.getDate() + i);
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
  imports: [CommonModule, ScheduleModalComponent, WorkoutDetailModalComponent],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarComponent implements OnInit {
  readonly EMPTY: ScheduledWorkout[] = [];

  weekDays: CalendarDay[] = [];
  startDate!: Date;
  endDate!: Date;

  workoutsByDay$!: Observable<WorkoutsByDay>;

  isScheduleModalOpen = false;
  selectedDate: string | null = null;
  selectedWorkout: ScheduledWorkout | null = null;

  private readonly calendarService = inject(CalendarService);
  private readonly authService = inject(AuthService);

  private userId = '';
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  ngOnInit(): void {
    this.setWeek(new Date());

    this.workoutsByDay$ = this.authService.user$.pipe(
      filter((u) => !!u),
      tap((user) => (this.userId = user!.id)),
      switchMap(() => this.reload$),
      switchMap(() =>
        this.calendarService.getMySchedule(this.userId, this.weekDays[0].key, this.weekDays[6].key)
      ),
      map(groupByDay)
    );
  }

  navigateWeek(direction: -1 | 1): void {
    const base = new Date(this.startDate);
    base.setDate(base.getDate() + direction * DAYS_IN_WEEK);
    this.setWeek(base);
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
      .deleteScheduledWorkout(this.userId, workout.id)
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

  private setWeek(baseDate: Date): void {
    this.weekDays = buildWeek(baseDate);
    this.startDate = this.weekDays[0].date;
    this.endDate = this.weekDays[6].date;
  }
}

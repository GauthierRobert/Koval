import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { RouterModule } from '@angular/router';

import { CalendarService } from '../../../services/calendar.service';
import { ScheduledWorkout } from '../../../services/coach.service';
import { SavedSession } from '../../../services/history.service';
import { TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType } from '../../../services/training.service';
import { SportIconComponent } from '../../sport-icon/sport-icon.component';
import { TrainingLoadChartComponent } from '../../training-load-chart/training-load-chart.component';
import { CalendarDay, CalendarEntry, EntriesByDay, toDateKey } from '../calendar.component';
import { WorkoutsByDay } from '../calendar.component';

@Component({
  selector: 'app-calendar-week-view',
  standalone: true,
  imports: [CommonModule, RouterModule, CdkDropList, CdkDrag, CdkDropListGroup, SportIconComponent, TrainingLoadChartComponent],
  templateUrl: './calendar-week-view.component.html',
  styleUrl: './calendar-week-view.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarWeekViewComponent {
  readonly EMPTY: CalendarEntry[] = [];
  readonly emptyMap: WorkoutsByDay = new Map();

  @Input() days: CalendarDay[] = [];
  @Input() entriesByDay: EntriesByDay = new Map();
  @Input() workoutsByDay: WorkoutsByDay = new Map();

  @Output() addDay = new EventEmitter<CalendarDay>();
  @Output() workoutSelected = new EventEmitter<ScheduledWorkout>();
  @Output() sessionSelected = new EventEmitter<SavedSession>();
  @Output() complete = new EventEmitter<ScheduledWorkout>();
  @Output() skip = new EventEmitter<ScheduledWorkout>();
  @Output() workoutDeleted = new EventEmitter<ScheduledWorkout>();
  @Output() dropped = new EventEmitter<{ drop: CdkDragDrop<CalendarEntry[]>; day: CalendarDay }>();
  @Output() linked = new EventEmitter<void>();

  linkPickerState: { sessionId: string } | null = null;
  nearbyScheduled$: Observable<ScheduledWorkout[]> = of([]);

  private readonly calendarService = inject(CalendarService);

  openLinkPicker(session: SavedSession): void {
    this.linkPickerState = { sessionId: session.id };
    const d = new Date(session.date);
    const from = new Date(d); from.setDate(d.getDate() - 3);
    const to = new Date(d); to.setDate(d.getDate() + 3);
    this.nearbyScheduled$ = this.calendarService
      .getMySchedule(toDateKey(from), toDateKey(to)).pipe(
        map(ws => ws.filter(w => w.status === 'PENDING' && !w.sessionId))
      );
  }

  confirmLink(scheduledWorkoutId: string): void {
    if (!this.linkPickerState) return;
    this.calendarService.linkSessionToSchedule(this.linkPickerState.sessionId, scheduledWorkoutId)
      .subscribe(() => { this.linkPickerState = null; this.linked.emit(); });
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

  isFutureDate(dateKey: string): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return new Date(dateKey + 'T00:00:00') > today;
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }

  trackByDay(_: number, day: CalendarDay): string { return day.key; }

  trackByEntry(_: number, e: CalendarEntry): string {
    if (e.kind === 'fused') return 'fused-' + e.scheduled.id;
    if (e.kind === 'scheduled') return 'sw-' + e.scheduled.id;
    return 'sess-' + e.session.id;
  }
}

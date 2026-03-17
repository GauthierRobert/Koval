import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Observable, of} from 'rxjs';
import {map} from 'rxjs/operators';
import {CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup} from '@angular/cdk/drag-drop';
import {RouterModule} from '@angular/router';

import {CalendarClubSession, CalendarService} from '../../../../services/calendar.service';
import {ScheduledWorkout} from '../../../../services/coach.service';
import {SavedSession} from '../../../../services/history.service';
import {TRAINING_TYPE_COLORS, TRAINING_TYPE_LABELS, TrainingType} from '../../../../models/training.model';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';
import {TrainingLoadChartComponent} from '../../../layout/training-load-chart/training-load-chart.component';
import {CalendarDay, CalendarEntry, EntriesByDay, GoalsByDay, toDateKey, WorkoutsByDay} from '../calendar.component';
import {RaceGoalService} from '../../../../services/race-goal.service';
import {formatTimeHMS} from '../../../shared/format/format.utils';

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
  @Input() goalsByDay: GoalsByDay = new Map();

  @Output() addDay = new EventEmitter<CalendarDay>();
  @Output() workoutSelected = new EventEmitter<ScheduledWorkout>();
  @Output() sessionSelected = new EventEmitter<SavedSession>();
  @Output() complete = new EventEmitter<ScheduledWorkout>();
  @Output() skip = new EventEmitter<ScheduledWorkout>();
  @Output() workoutDeleted = new EventEmitter<ScheduledWorkout>();
  @Output() dropped = new EventEmitter<{ drop: CdkDragDrop<CalendarEntry[]>; day: CalendarDay }>();
  @Output() linked = new EventEmitter<void>();
  @Output() joinClubSession = new EventEmitter<CalendarClubSession>();
  @Output() cancelClubSession = new EventEmitter<CalendarClubSession>();

  linkPickerState: { sessionId: string } | null = null;
  linkSession: SavedSession | null = null;
  nearbyScheduled$: Observable<ScheduledWorkout[]> = of([]);

  private readonly calendarService = inject(CalendarService);
  private readonly raceGoalService = inject(RaceGoalService);

  getPriorityColor(priority: string): string {
    return this.raceGoalService.getPriorityColor(priority);
  }

  openLinkPicker(session: SavedSession): void {
    this.linkPickerState = { sessionId: session.id };
    this.linkSession = session;
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
    return formatTimeHMS(workout.totalDurationSeconds);
  }

  formatSessionDuration(session: SavedSession): string {
    return formatTimeHMS(session.totalDuration);
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

  trackByDay(day: CalendarDay): string { return day.key; }

  trackByEntry(e: CalendarEntry): string {
    if (e.kind === 'fused') return 'fused-' + e.scheduled.id;
    if (e.kind === 'scheduled') return 'sw-' + e.scheduled.id;
    if (e.kind === 'club-session') return 'club-' + e.clubSession.id;
    return 'sess-' + e.session.id;
  }

  formatClubSessionTime(scheduledAt: string): string {
    const d = new Date(scheduledAt);
    const h = d.getHours();
    const m = String(d.getMinutes()).padStart(2, '0');
    const ampm = h >= 12 ? 'PM' : 'AM';
    const hour = h % 12 || 12;
    return `${hour}:${m} ${ampm}`;
  }

  /** Group consecutive club-session entries that share the same hour into rows */
  groupEntries(entries: CalendarEntry[]): Array<{ type: 'single'; entry: CalendarEntry } | { type: 'club-row'; entries: CalendarEntry[] }> {
    const result: Array<{ type: 'single'; entry: CalendarEntry } | { type: 'club-row'; entries: CalendarEntry[] }> = [];
    let i = 0;
    while (i < entries.length) {
      const entry = entries[i];
      if (entry.kind === 'club-session') {
        const timeKey = entry.clubSession.scheduledAt?.substring(0, 13) ?? '';
        const group: CalendarEntry[] = [entry];
        let j = i + 1;
        while (j < entries.length && entries[j].kind === 'club-session') {
          const next = entries[j] as CalendarEntry & { kind: 'club-session'; clubSession: any };
          const nextKey = next.clubSession.scheduledAt?.substring(0, 13) ?? '';
          if (nextKey === timeKey) {
            group.push(entries[j]);
            j++;
          } else {
            break;
          }
        }
        if (group.length > 1) {
          result.push({ type: 'club-row', entries: group });
        } else {
          result.push({ type: 'single', entry });
        }
        i = j;
      } else {
        result.push({ type: 'single', entry });
        i++;
      }
    }
    return result;
  }
}

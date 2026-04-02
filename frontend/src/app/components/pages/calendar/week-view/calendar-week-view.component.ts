import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {Observable, of} from 'rxjs';
import {map} from 'rxjs/operators';
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
  imports: [CommonModule, RouterModule, SportIconComponent, TrainingLoadChartComponent, TranslateModule],
  templateUrl: './calendar-week-view.component.html',
  styleUrl: './calendar-week-view.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarWeekViewComponent {
  readonly emptyMap: WorkoutsByDay = new Map();
  rescheduleTarget: ScheduledWorkout | null = null;

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
  @Output() rescheduled = new EventEmitter<{ workout: ScheduledWorkout; newDate: string }>();
  @Output() linked = new EventEmitter<void>();
  @Output() joinClubSession = new EventEmitter<CalendarClubSession>();
  @Output() cancelClubSession = new EventEmitter<CalendarClubSession>();

  linkPickerState: { sessionId: string } | null = null;
  linkSession: SavedSession | null = null;
  nearbyScheduled$: Observable<ScheduledWorkout[]> = of([]);

  clubSessionLinkPickerState: { sessionId: string } | null = null;
  clubSessionLinkSession: SavedSession | null = null;
  nearbyClubSessions$: Observable<CalendarClubSession[]> = of([]);

  linkDropdownSessionId: string | null = null;

  toggleLinkDropdown(session: SavedSession, event: Event): void {
    event.stopPropagation();
    this.linkDropdownSessionId = this.linkDropdownSessionId === session.id ? null : session.id;
  }

  closeLinkDropdown(): void {
    this.linkDropdownSessionId = null;
  }

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

  openClubSessionLinkPicker(session: SavedSession): void {
    this.clubSessionLinkPickerState = { sessionId: session.id };
    this.clubSessionLinkSession = session;
    const d = new Date(session.date);
    const from = new Date(d); from.setDate(d.getDate() - 3);
    const to = new Date(d); to.setDate(d.getDate() + 3);
    this.nearbyClubSessions$ = this.calendarService
      .getClubSessionsForCalendar(toDateKey(from), toDateKey(to)).pipe(
        map(sessions => sessions.filter(s => s.joined && !s.cancelled))
      );
  }

  confirmClubSessionLink(clubSessionId: string): void {
    if (!this.clubSessionLinkPickerState) return;
    this.calendarService.linkSessionToClubSession(this.clubSessionLinkPickerState.sessionId, clubSessionId)
      .subscribe(() => {
        this.clubSessionLinkPickerState = null;
        this.clubSessionLinkSession = null;
        this.linked.emit();
      });
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

  openReschedule(workout: ScheduledWorkout, event: Event): void {
    event.stopPropagation();
    this.rescheduleTarget = workout;
  }

  confirmReschedule(newDate: string): void {
    if (!this.rescheduleTarget || !newDate) return;
    this.rescheduled.emit({ workout: this.rescheduleTarget, newDate });
    this.rescheduleTarget = null;
  }

  cancelReschedule(): void {
    this.rescheduleTarget = null;
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

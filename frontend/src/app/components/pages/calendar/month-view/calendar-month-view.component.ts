import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ScheduledWorkout} from '../../../../services/coach.service';
import {SavedSession} from '../../../../services/history.service';
import {TRAINING_TYPE_COLORS, TrainingType} from '../../../../models/training.model';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';
import {CalendarDay, CalendarEntry, EntriesByDay, GoalsByDay} from '../calendar.component';
import {RaceGoalService} from '../../../../services/race-goal.service';
import {CalendarClubSession} from '../../../../services/calendar.service';

@Component({
  selector: 'app-calendar-month-view',
  standalone: true,
  imports: [CommonModule, SportIconComponent, TranslateModule],
  templateUrl: './calendar-month-view.component.html',
  styleUrl: './calendar-month-view.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarMonthViewComponent {
  rescheduleTarget: ScheduledWorkout | null = null;

  @Input() days: CalendarDay[] = [];
  @Input() entriesByDay: EntriesByDay = new Map();
  @Input() startDate!: Date;
  @Input() goalsByDay: GoalsByDay = new Map();

  private raceGoalService = inject(RaceGoalService);

  getPriorityColor(priority: string): string {
    return this.raceGoalService.getPriorityColor(priority);
  }

  @Output() addDay = new EventEmitter<CalendarDay>();
  @Output() workoutSelected = new EventEmitter<ScheduledWorkout>();
  @Output() sessionSelected = new EventEmitter<SavedSession>();
  @Output() rescheduled = new EventEmitter<{ workout: ScheduledWorkout; newDate: string }>();
  @Output() joinClubSession = new EventEmitter<CalendarClubSession>();
  @Output() cancelClubSession = new EventEmitter<CalendarClubSession>();

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

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  trackByDay(day: CalendarDay): string { return day.key; }

  trackByEntry(e: CalendarEntry): string {
    if (e.kind === 'fused') return 'fused-' + e.scheduled.id;
    if (e.kind === 'scheduled') return 'sw-' + e.scheduled.id;
    if (e.kind === 'club-session') return 'club-' + e.clubSession.id;
    return 'sess-' + e.session.id;
  }
}

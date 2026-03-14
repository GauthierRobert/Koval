import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';

import { ScheduledWorkout } from '../../../../services/coach.service';
import { SavedSession } from '../../../../services/history.service';
import { TRAINING_TYPE_COLORS, TrainingType } from '../../../../models/training.model';
import { SportIconComponent } from '../../../shared/sport-icon/sport-icon.component';
import { CalendarDay, CalendarEntry, EntriesByDay, GoalsByDay } from '../calendar.component';
import { RaceGoalService } from '../../../../services/race-goal.service';
import { CalendarClubSession } from '../../../../services/calendar.service';
import { inject } from '@angular/core';

@Component({
  selector: 'app-calendar-month-view',
  standalone: true,
  imports: [CommonModule, CdkDropList, CdkDrag, CdkDropListGroup, SportIconComponent],
  templateUrl: './calendar-month-view.component.html',
  styleUrl: './calendar-month-view.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarMonthViewComponent {
  readonly EMPTY: CalendarEntry[] = [];

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
  @Output() dropped = new EventEmitter<{ drop: CdkDragDrop<CalendarEntry[]>; day: CalendarDay }>();
  @Output() joinClubSession = new EventEmitter<CalendarClubSession>();
  @Output() cancelClubSession = new EventEmitter<CalendarClubSession>();

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  trackByDay(_: number, day: CalendarDay): string { return day.key; }

  trackByEntry(_: number, e: CalendarEntry): string {
    if (e.kind === 'fused') return 'fused-' + e.scheduled.id;
    if (e.kind === 'scheduled') return 'sw-' + e.scheduled.id;
    if (e.kind === 'club-session') return 'club-' + e.clubSession.id;
    return 'sess-' + e.session.id;
  }
}

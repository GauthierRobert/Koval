import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { RouterModule } from '@angular/router';

import { ScheduledWorkout } from '../../../../../services/coach.service';
import {
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  TrainingType,
} from '../../../../../models/training.model';
import { SportIconComponent } from '../../../../shared/sport-icon/sport-icon.component';
import { CalendarEntry } from '../../calendar.component';
import { formatTimeHMS } from '../../../../shared/format/format.utils';

@Component({
  selector: 'app-calendar-workout-card',
  standalone: true,
  imports: [CommonModule, RouterModule, SportIconComponent, TranslateModule],
  templateUrl: './calendar-workout-card.component.html',
  styleUrl: './calendar-workout-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalendarWorkoutCardComponent {
  @Input() entry!: CalendarEntry;
  @Input() isFuture = false;

  @Output() workoutSelect = new EventEmitter<ScheduledWorkout>();
  @Output() complete = new EventEmitter<ScheduledWorkout>();
  @Output() skip = new EventEmitter<ScheduledWorkout>();
  @Output() workoutDelete = new EventEmitter<ScheduledWorkout>();
  @Output() reschedule = new EventEmitter<{ workout: ScheduledWorkout; event: Event }>();
  @Output() openAnalysis = new EventEmitter<string>();

  get scheduled(): ScheduledWorkout {
    if (this.entry.kind !== 'scheduled' && this.entry.kind !== 'fused') {
      throw new Error(`Expected scheduled or fused entry, got ${this.entry.kind}`);
    }
    return this.entry.scheduled;
  }

  formatDuration(workout: ScheduledWorkout): string {
    if (!workout.totalDurationSeconds) return workout.duration || '-';
    return formatTimeHMS(workout.totalDurationSeconds);
  }

  getTypeColor(type: string): string {
    return TRAINING_TYPE_COLORS[type as TrainingType] || '#888';
  }

  getTypeLabel(type: string): string {
    return TRAINING_TYPE_LABELS[type as TrainingType] || type;
  }
}

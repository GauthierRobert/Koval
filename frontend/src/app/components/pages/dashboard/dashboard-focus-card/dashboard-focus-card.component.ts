import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ScheduledWorkout} from '../../../../services/coach.service';

@Component({
  selector: 'app-dashboard-focus-card',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './dashboard-focus-card.component.html',
  styleUrl: './dashboard-focus-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardFocusCardComponent {
  @Input() upcomingWorkouts: ScheduledWorkout[] = [];
  @Input() todayKey = '';

  @Output() openDetail = new EventEmitter<ScheduledWorkout>();

  formatScheduleDuration(workout: ScheduledWorkout): string {
    if (!workout.totalDurationSeconds) return workout.duration || '';
    const totalSec = workout.totalDurationSeconds;
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }

  formatScheduleDate(dateStr: string): string {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-US', {weekday: 'short', month: 'short', day: 'numeric'});
  }
}

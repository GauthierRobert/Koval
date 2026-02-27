import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrainingService, Training } from '../../services/training.service';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, switchMap, map } from 'rxjs/operators';
import { RouterModule } from '@angular/router';
import { WorkoutVisualizationComponent } from '../workout-visualization/workout-visualization.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { WorkoutDetailModalComponent } from '../workout-detail-modal/workout-detail-modal.component';
import { SessionAnalysisComponent } from '../session-analysis/session-analysis.component';
import { CalendarService } from '../../services/calendar.service';
import { AuthService, User } from '../../services/auth.service';
import { HistoryService } from '../../services/history.service';
import { ScheduledWorkout } from '../../services/coach.service';
import { SportIconComponent } from '../sport-icon/sport-icon.component';

function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

@Component({
  selector: 'app-workout-selection',
  standalone: true,
  imports: [
    CommonModule,
    WorkoutVisualizationComponent,
    RouterModule,
    SidebarComponent,
    WorkoutDetailModalComponent,
    SessionAnalysisComponent,
    SportIconComponent,
  ],
  templateUrl: './workout-selection.component.html',
  styleUrl: './workout-selection.component.css',
})
export class WorkoutSelectionComponent implements OnInit {
  private trainingService = inject(TrainingService);
  private calendarService = inject(CalendarService);
  private authService = inject(AuthService);
  private historyService = inject(HistoryService);

  selectedTraining$: Observable<Training | null> = this.trainingService.selectedTraining$;
  selectedSession$ = this.historyService.selectedSession$;

  upcomingWorkouts$!: Observable<ScheduledWorkout[]>;
  overdueWorkouts$!: Observable<ScheduledWorkout[]>;

  selectedScheduledWorkout: ScheduledWorkout | null = null;
  currentUser: User | null = null;

  private reload$ = new BehaviorSubject<void>(undefined);

  ngOnInit(): void {
    this.authService.user$.subscribe(u => this.currentUser = u);

    const schedule$ = this.authService.user$.pipe(
      filter((u) => !!u),
      switchMap(() => this.reload$),
      switchMap(() => {
        const today = new Date();
        const thirtyDaysAgo = new Date(today);
        thirtyDaysAgo.setDate(today.getDate() - 30);

        const endOfWeek = new Date(today);
        endOfWeek.setDate(today.getDate() + (7 - today.getDay()));

        return this.calendarService.getMySchedule(
          toDateKey(thirtyDaysAgo),
          toDateKey(endOfWeek)
        );
      })
    );

    const todayKey = toDateKey(new Date());

    this.upcomingWorkouts$ = schedule$.pipe(
      map((workouts) =>
        workouts.filter((w) => w.status === 'PENDING' && w.scheduledDate >= todayKey)
      )
    );

    this.overdueWorkouts$ = schedule$.pipe(
      map((workouts) =>
        workouts.filter((w) => w.status === 'PENDING' && w.scheduledDate < todayKey)
      )
    );
  }

  openDetail(workout: ScheduledWorkout): void {
    this.selectedScheduledWorkout = workout;
  }

  onDetailClosed(): void {
    this.selectedScheduledWorkout = null;
  }

  onDetailStarted(): void {
    this.selectedScheduledWorkout = null;
  }

  onDetailStatusChanged(): void {
    this.selectedScheduledWorkout = null;
    this.reload$.next();
  }

  onSessionClosed(): void {
    this.historyService.selectSession(null);
  }

  formatScheduleDate(dateStr: string): string {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
  }

  formatScheduleDuration(workout: ScheduledWorkout): string {
    if (!workout.totalDurationSeconds) return workout.duration || '';
    const totalSec = workout.totalDurationSeconds;
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }

}

import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject, of } from 'rxjs';
import { switchMap, map, catchError, startWith } from 'rxjs/operators';
import { Training, WorkoutBlock, TrainingService } from '../../services/training.service';
import { ScheduledWorkout } from '../../services/coach.service';
import { CalendarService } from '../../services/calendar.service';
import { WorkoutExecutionService } from '../../services/workout-execution.service';

interface TrainingState {
  training: Training | null;
  loading: boolean;
  error: string | null;
}

const IDLE_STATE: TrainingState = { training: null, loading: false, error: null };
const LOADING_STATE: TrainingState = { training: null, loading: true, error: null };

@Component({
  selector: 'app-workout-detail-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workout-detail-modal.component.html',
  styleUrl: './workout-detail-modal.component.css',
})
export class WorkoutDetailModalComponent {
  @Input() set workout(value: ScheduledWorkout | null) {
    this.workout$.next(value);
  }
  @Output() closed = new EventEmitter<void>();
  @Output() started = new EventEmitter<void>();
  @Output() statusChanged = new EventEmitter<void>();

  private trainingService = inject(TrainingService);
  private calendarService = inject(CalendarService);
  private executionService = inject(WorkoutExecutionService);

  workout$ = new BehaviorSubject<ScheduledWorkout | null>(null);

  trainingState$ = this.workout$.pipe(
    switchMap((w) => {
      if (!w) return of(IDLE_STATE);
      return this.trainingService.getTrainingById(w.trainingId).pipe(
        map((t): TrainingState => ({ training: t, loading: false, error: null })),
        catchError(() => of<TrainingState>({ training: null, loading: false, error: 'Could not load workout details.' })),
        startWith(LOADING_STATE)
      );
    })
  );

  close(): void {
    this.closed.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close();
    }
  }

  startTraining(training: Training): void {
    this.executionService.startWorkout(training);
    this.started.emit();
  }

  markComplete(): void {
    const workout = this.workout$.value;
    if (!workout) return;
    this.calendarService.markCompleted(workout.id).subscribe(() => {
      this.statusChanged.emit();
      this.close();
    });
  }

  markSkipped(): void {
    const workout = this.workout$.value;
    if (!workout) return;
    this.calendarService.markSkipped(workout.id).subscribe(() => {
      this.statusChanged.emit();
      this.close();
    });
  }

  getBlockWidth(block: WorkoutBlock, training: Training): number {
    const total = training.blocks.reduce((a, b) => a + b.durationSeconds, 0);
    return (block.durationSeconds / total) * 100;
  }

  getBlockHeight(block: WorkoutBlock, training: Training): number {
    const maxP = this.getMaxPower(training);
    if (block.type === 'FREE') return (65 / maxP) * 100;
    const power =
      block.type === 'RAMP'
        ? Math.max(block.powerStartPercent || 0, block.powerEndPercent || 0)
        : block.powerTargetPercent || 0;
    return (power / maxP) * 100;
  }

  getBlockClipPath(block: WorkoutBlock, training: Training): string {
    if (block.type !== 'RAMP') return 'none';
    const maxP = this.getMaxPower(training);
    const startH = ((block.powerStartPercent || 0) / maxP) * 100;
    const endH = ((block.powerEndPercent || 0) / maxP) * 100;
    const currentH = Math.max(startH, endH);
    const startRel = 100 - (startH / currentH) * 100;
    const endRel = 100 - (endH / currentH) * 100;
    return `polygon(0% ${startRel}%, 100% ${endRel}%, 100% 100%, 0% 100%)`;
  }

  getBlockColor(block: WorkoutBlock): string {
    if (block.type === 'FREE') return '#636e72';
    if (block.type === 'WARMUP') return 'rgba(9, 132, 227, 0.6)';
    if (block.type === 'COOLDOWN') return 'rgba(108, 92, 231, 0.6)';
    const power =
      block.type === 'RAMP'
        ? ((block.powerStartPercent || 0) + (block.powerEndPercent || 0)) / 2
        : block.powerTargetPercent || 0;
    if (power < 55) return '#b2bec3';
    if (power < 75) return '#3498db';
    if (power < 90) return '#2ecc71';
    if (power < 105) return '#f1c40f';
    if (power < 120) return '#e67e22';
    return '#e74c3c';
  }

  getTotalDuration(training: Training): string {
    const totalSec = training.blocks.reduce((a, b) => a + b.durationSeconds, 0);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }

  getFormattedDate(workout: ScheduledWorkout): string {
    const d = new Date(workout.scheduledDate + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' });
  }

  private getMaxPower(training: Training): number {
    const powers = training.blocks.flatMap((b) => [
      b.powerTargetPercent || 0,
      b.powerStartPercent || 0,
      b.powerEndPercent || 0,
    ]);
    return Math.max(150, Math.max(...powers) + 20);
  }
}

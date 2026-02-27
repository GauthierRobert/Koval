
import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BehaviorSubject, of } from 'rxjs';
import { switchMap, map, catchError, startWith } from 'rxjs/operators';
import { Training, WorkoutBlock, TrainingService, TrainingType, hasDurationEstimate } from '../../services/training.service';
import { ScheduledWorkout } from '../../services/coach.service';
import { CalendarService } from '../../services/calendar.service';
import { WorkoutExecutionService } from '../../services/workout-execution.service';
import { DurationEstimationService } from '../../services/duration-estimation.service';
import { ZoneService } from '../../services/zone.service';
import { ZoneSystem } from '../../services/zone';
import { AuthService } from '../../services/auth.service';

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
    if (value?.trainingId) {
      this.trainingService.getTrainingById(value.trainingId).subscribe(training => {
        if (training?.zoneSystemId) {
          this.zoneService.getZoneSystemById(training.zoneSystemId).subscribe({
            next: (zs) => this.currentZoneSystem = zs,
            error: () => this.currentZoneSystem = null
          });
        } else {
          this.currentZoneSystem = null;
        }
      });
    }
  }
  @Output() closed = new EventEmitter<void>();
  @Output() started = new EventEmitter<void>();
  @Output() statusChanged = new EventEmitter<void>();

  private trainingService = inject(TrainingService);
  private calendarService = inject(CalendarService);
  private executionService = inject(WorkoutExecutionService);
  private authService = inject(AuthService);
  private durationService = inject(DurationEstimationService);
  private zoneService = inject(ZoneService);
  private currentZoneSystem: ZoneSystem | null = null;

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
    const workout = this.workout$.value;
    this.executionService.startWorkout(training, workout ? workout.id : null);
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

  private flattenElements(blocks: WorkoutBlock[]): WorkoutBlock[] {
    // Backend now returns flattened blocks or handles repeats differently.
    // This is a pass-through now that 'repeats' is removed.
    return blocks;
  }

  getBlockWidth(block: WorkoutBlock, training: Training): number {
    const total = this.getNumericalTotalDuration(training);
    if (total === 0) return 0;
    return ((this.getEstimatedBlockDuration(block, training)) / total) * 100;
  }

  getBlockHeight(block: WorkoutBlock, training: Training): number {
    const maxI = this.getMaxIntensity(training);
    if (block.type === 'PAUSE') return 100;
    if (block.type === 'FREE') return (65 / maxI) * 100;
    const intensity =
      block.type === 'RAMP'
        ? Math.max(block.intensityStart || 0, block.intensityEnd || 0)
        : block.intensityTarget || 0;
    return (intensity / maxI) * 100;
  }

  getBlockClipPath(block: WorkoutBlock, training: Training): string {
    if (block.type !== 'RAMP') return 'none';
    const maxI = this.getMaxIntensity(training);
    const startH = ((block.intensityStart || 0) / maxI) * 100;
    const endH = ((block.intensityEnd || 0) / maxI) * 100;
    const currentH = Math.max(startH, endH);
    const startRel = 100 - (startH / currentH) * 100;
    const endRel = 100 - (endH / currentH) * 100;
    return `polygon(0% ${startRel}%, 100% ${endRel}%, 100% 100%, 0% 100%)`;
  }

  getBlockColor(block: WorkoutBlock): string {
    if (block.type === 'PAUSE') return '#636e72';
    if (block.type === 'FREE') return '#636e72';
    if (block.type === 'WARMUP') return 'rgba(9, 132, 227, 0.6)';
    if (block.type === 'COOLDOWN') return 'rgba(108, 92, 231, 0.6)';
    const intensity =
      block.type === 'RAMP'
        ? ((block.intensityStart || 0) + (block.intensityEnd || 0)) / 2
        : block.intensityTarget || 0;
    if (intensity < 55) return '#b2bec3';
    if (intensity < 75) return '#3498db';
    if (intensity < 90) return '#2ecc71';
    if (intensity < 105) return '#f1c40f';
    if (intensity < 120) return '#e67e22';
    return '#e74c3c';
  }

  getNumericalTotalDuration(training: Training): number {
    if (training.estimatedDurationSeconds) return training.estimatedDurationSeconds;
    if (!training.blocks) return 0;
    return training.blocks.reduce((acc, b) => acc + (this.getEstimatedBlockDuration(b, training)), 0);
  }

  // Helper
  getEstimatedBlockDuration(block: WorkoutBlock, training: Training): number {
    return this.durationService.estimateDuration(block, training, this.currentZoneSystem);
  }

  isDurationEstimated(training: Training): boolean {
    return hasDurationEstimate(training);
  }

  getTotalDuration(training: Training): string {
    const totalSec = this.getNumericalTotalDuration(training);
    if (totalSec === 0) return '0m';
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h}h ${m} m`;
    return `${m} m`;
  }

  isFutureDate(dateKey: string): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const d = new Date(dateKey + 'T00:00:00');
    return d > today;
  }

  getFormattedDate(workout: ScheduledWorkout): string {
    const d = new Date(workout.scheduledDate + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' });
  }

  private getMaxIntensity(training: Training): number {
    if (!training.blocks) return 150;
    const intensities = training.blocks.flatMap((b) => [
      b.intensityTarget || 0,
      b.intensityStart || 0,
      b.intensityEnd || 0,
    ]);
    const maxBlockIntensity = intensities.length > 0 ? Math.max(...intensities) : 0;
    return Math.max(150, maxBlockIntensity + 20);
  }

  getDisplayIntensity(block: WorkoutBlock): string {
    if (block.type === 'PAUSE') return 'PAUSE';
    if (block.type === 'FREE') return 'FREE';
    if (block.type === 'RAMP') return `${block.intensityStart}%-${block.intensityEnd}%`;
    return `${block.intensityTarget}%`;
  }

  calculateIntensityValue(percent: number | undefined, training: Training): string {
    if (percent === undefined) return '0';
    const user = this.authService.currentUser;

    if (training.sportType === 'CYCLING') {
      const ftp = user?.ftp || 250;
      return Math.round((percent * ftp) / 100).toString() + 'W';
    }

    if (training.sportType === 'RUNNING') {
      const threshold = user?.functionalThresholdPace || 240;
      const secondsPerKm = threshold / (percent / 100);
      return this.formatPace(secondsPerKm) + '/km';
    }

    if (training.sportType === 'SWIMMING') {
      const threshold = user?.criticalSwimSpeed || 90;
      const secondsPer100m = threshold / (percent / 100);
      return this.formatPace(secondsPer100m) + '/100m';
    }

    return percent.toString() + '%';
  }

  formatPace(totalSeconds: number): string {
    const m = Math.floor(totalSeconds / 60);
    const s = Math.round(totalSeconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }
}

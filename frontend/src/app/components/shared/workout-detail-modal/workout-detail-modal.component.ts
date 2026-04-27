import {Component, EventEmitter, HostListener, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {A11yModule} from '@angular/cdk/a11y';
import {BehaviorSubject, of} from 'rxjs';
import {catchError, map, startWith, switchMap} from 'rxjs/operators';
import {TrainingService} from '../../../services/training.service';
import {flattenElements, hasDurationEstimate, Training, WorkoutBlock} from '../../../models/training.model';
import {ScheduledWorkout} from '../../../services/coach.service';
import {CalendarService} from '../../../services/calendar.service';
import {WorkoutExecutionService} from '../../../services/workout-execution.service';
import {DurationEstimationService} from '../../../services/duration-estimation.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {AuthService} from '../../../services/auth.service';
import {formatPace as sharedFormatPace} from '../format/format.utils';
import {
  getBlockClipPath as sharedGetBlockClipPath,
  getBlockColor as sharedGetBlockColor,
  getBlockHeight as sharedGetBlockHeight,
  getDisplayIntensity as sharedGetDisplayIntensity,
  getMaxIntensity,
} from '../block-helpers/block-helpers';

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
  imports: [CommonModule, TranslateModule, RouterModule, A11yModule],
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
  private router = inject(Router);
  private currentZoneSystem: ZoneSystem | null = null;

  showBlockDetails = false;

  workout$ = new BehaviorSubject<ScheduledWorkout | null>(null);

  trainingState$ = this.workout$.pipe(
    switchMap((w) => {
      if (!w) return of(IDLE_STATE);
      return this.trainingService.getTrainingById(w.trainingId).pipe(
        map((t): TrainingState => ({ training: t, loading: false, error: null })),
        catchError(() => of<TrainingState>({ training: null, loading: false, error: 'WORKOUT_DETAIL.ERROR_LOAD_FAILED' })),
        startWith(LOADING_STATE)
      );
    })
  );

  close(): void {
    this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.workout$.value) {
      this.close();
    }
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

  toggleBlockDetails(): void {
    this.showBlockDetails = !this.showBlockDetails;
  }

  viewInLibrary(training: Training): void {
    this.close();
    this.router.navigate(['/trainings', training.id]);
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
    return sharedGetBlockHeight(block, getMaxIntensity(training));
  }

  getBlockClipPath(block: WorkoutBlock, training: Training): string {
    return sharedGetBlockClipPath(block, getMaxIntensity(training));
  }

  getBlockColor(block: WorkoutBlock, training?: Training): string {
    return sharedGetBlockColor(block, training?.sportType);
  }

  getFlatBlocks(training: Training): WorkoutBlock[] {
    return flattenElements(training.blocks ?? []);
  }

  getNumericalTotalDuration(training: Training): number {
    if (training.estimatedDurationSeconds) return training.estimatedDurationSeconds;
    if (!training.blocks) return 0;
    return this.getFlatBlocks(training).reduce((acc, b) => acc + (this.getEstimatedBlockDuration(b, training)), 0);
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
    return getMaxIntensity(training);
  }

  formatBlockDuration(block: WorkoutBlock, training: Training): string {
    const sec = this.getEstimatedBlockDuration(block, training);
    if (sec <= 0) return '—';
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    if (m >= 60) {
      const h = Math.floor(m / 60);
      const rm = m % 60;
      return `${h}h${rm > 0 ? rm + 'm' : ''}`;
    }
    return s > 0 ? `${m}:${s.toString().padStart(2, '0')}` : `${m}m`;
  }

  formatBlockDistance(block: WorkoutBlock, training: Training): string {
    const meters = this.durationService.estimateDistance(block, training, this.currentZoneSystem);
    if (meters <= 0) return '—';
    if (meters >= 1000) return (meters / 1000).toFixed(2) + ' km';
    return Math.round(meters) + ' m';
  }

  getDisplayIntensity(block: WorkoutBlock): string {
    return sharedGetDisplayIntensity(block);
  }

  getBlockIntensityPercent(block: WorkoutBlock): number | undefined {
    if (block.intensityTarget && block.intensityTarget > 0) return block.intensityTarget;
    if (block.intensityStart && block.intensityStart > 0 && block.intensityEnd && block.intensityEnd > 0) {
      return (block.intensityStart + block.intensityEnd) / 2;
    }
    return undefined;
  }

  isEstimationRelevant(training: Training): boolean {
    return training.sportType === 'CYCLING';
  }

  calculateIntensityValue(percent: number | undefined, training: Training): string {
    if (percent === undefined) return '0';
    const user = this.authService.currentUser;

    if (training.sportType === 'CYCLING') {
      if (!user?.ftp) return `${percent}%`;
      return Math.round((percent * user.ftp) / 100).toString() + 'W';
    }

    if (training.sportType === 'RUNNING') {
      if (!user?.functionalThresholdPace) return `${percent}%`;
      const secondsPerKm = user.functionalThresholdPace / (percent / 100);
      return this.formatPace(secondsPerKm) + '/km';
    }

    if (training.sportType === 'SWIMMING') {
      if (!user?.criticalSwimSpeed) return `${percent}%`;
      const secondsPer100m = user.criticalSwimSpeed / (percent / 100);
      return this.formatPace(secondsPer100m) + '/100m';
    }

    return percent.toString() + '%';
  }

  formatPace(totalSeconds: number): string {
    return sharedFormatPace(totalSeconds);
  }
}

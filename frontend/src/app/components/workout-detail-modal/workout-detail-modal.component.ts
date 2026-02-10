import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Training, WorkoutBlock, TrainingService } from '../../services/training.service';
import { ScheduledWorkout } from '../../services/coach.service';
import { CalendarService } from '../../services/calendar.service';
import { WorkoutExecutionService } from '../../services/workout-execution.service';

@Component({
  selector: 'app-workout-detail-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workout-detail-modal.component.html',
  styleUrl: './workout-detail-modal.component.css',
})
export class WorkoutDetailModalComponent implements OnChanges {
  @Input() workout: ScheduledWorkout | null = null;
  @Output() closed = new EventEmitter<void>();
  @Output() started = new EventEmitter<void>();
  @Output() statusChanged = new EventEmitter<void>();

  private trainingService = inject(TrainingService);
  private calendarService = inject(CalendarService);
  private executionService = inject(WorkoutExecutionService);

  training: Training | null = null;
  loading = false;
  error: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['workout'] && this.workout) {
      this.loading = true;
      this.error = null;
      this.training = null;
      this.trainingService.getTrainingById(this.workout.trainingId).subscribe({
        next: (t) => {
          this.training = t;
          this.loading = false;
        },
        error: () => {
          this.error = 'Could not load workout details.';
          this.loading = false;
        },
      });
    }
  }

  close(): void {
    this.closed.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close();
    }
  }

  startTraining(): void {
    if (this.training) {
      this.executionService.startWorkout(this.training);
      this.started.emit();
    }
  }

  markComplete(): void {
    if (!this.workout) return;
    this.calendarService.markCompleted(this.workout.id).subscribe(() => {
      this.statusChanged.emit();
      this.close();
    });
  }

  markSkipped(): void {
    if (!this.workout) return;
    this.calendarService.markSkipped(this.workout.id).subscribe(() => {
      this.statusChanged.emit();
      this.close();
    });
  }

  // Chart helpers (matching WorkoutVisualizationComponent patterns)
  getBlockWidth(block: WorkoutBlock): number {
    if (!this.training) return 0;
    const total = this.training.blocks.reduce((a, b) => a + b.durationSeconds, 0);
    return (block.durationSeconds / total) * 100;
  }

  getBlockHeight(block: WorkoutBlock): number {
    const maxP = this.getMaxPower();
    if (block.type === 'FREE') return (65 / maxP) * 100;
    const power =
      block.type === 'RAMP'
        ? Math.max(block.powerStartPercent || 0, block.powerEndPercent || 0)
        : block.powerTargetPercent || 0;
    return (power / maxP) * 100;
  }

  getBlockClipPath(block: WorkoutBlock): string {
    if (block.type !== 'RAMP') return 'none';
    const maxP = this.getMaxPower();
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

  getTotalDuration(): string {
    if (!this.training) return '-';
    const totalSec = this.training.blocks.reduce((a, b) => a + b.durationSeconds, 0);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }

  getFormattedDate(): string {
    if (!this.workout) return '';
    const d = new Date(this.workout.scheduledDate + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' });
  }

  private getMaxPower(): number {
    if (!this.training) return 150;
    const powers = this.training.blocks.flatMap((b) => [
      b.powerTargetPercent || 0,
      b.powerStartPercent || 0,
      b.powerEndPercent || 0,
    ]);
    return Math.max(150, Math.max(...powers) + 20);
  }
}

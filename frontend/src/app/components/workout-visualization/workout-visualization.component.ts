import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Training, WorkoutBlock, TrainingService } from '../../services/training.service';
import { WorkoutExecutionService } from '../../services/workout-execution.service';
import { ExportService } from '../../services/export.service';
import { ScheduleModalComponent } from '../schedule-modal/schedule-modal.component';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-workout-visualization',
  standalone: true,
  imports: [CommonModule, ScheduleModalComponent],
  templateUrl: './workout-visualization.component.html',
  styleUrl: './workout-visualization.component.css'
})
export class WorkoutVisualizationComponent {
  @Input() training: Training | null = null;
  private trainingService = inject(TrainingService);
  private executionService = inject(WorkoutExecutionService);
  private authService = inject(AuthService);
  ftp$ = this.trainingService.ftp$;
  user$ = this.authService.user$;
  isExportDropdownOpen = false;
  isScheduleModalOpen = false;
  showBlockDetails = false;

  toggleDetails() {
    this.showBlockDetails = !this.showBlockDetails;
  }

  openScheduleModal() {
    this.isScheduleModalOpen = true;
  }

  startWorkout() {
    if (this.training) {
      this.executionService.startWorkout(this.training);
    }
  }

  getBlockWidth(block: WorkoutBlock): number {
    if (!this.training) return 0;
    const totalDuration = this.getNumericalTotalDuration();
    if (totalDuration === 0) return 0;
    return ((block.durationSeconds || 0) / totalDuration) * 100;
  }

  getEffectiveIntensity(block: WorkoutBlock, type: 'TARGET' | 'START' | 'END' = 'TARGET'): number {
    const user = this.authService.currentUser;
    if (!this.training || !user) return 0;

    // 1. CYCLING
    if (this.training.sportType === 'CYCLING') {
      let percent: number | undefined;
      if (type === 'TARGET') percent = block.powerTargetPercent;
      else if (type === 'START') percent = block.powerStartPercent;
      else if (type === 'END') percent = block.powerEndPercent;

      if (percent !== undefined && percent !== null) return percent;
      return 0;
    }

    // 2. RUNNING
    if (this.training.sportType === 'RUNNING') {
      const thresholdPace = user.functionalThresholdPace || 240; // Default 4:00/km
      let targetPace: number | undefined;

      if (type === 'TARGET') targetPace = block.paceTargetSecondsPerKm;
      else if (type === 'START') targetPace = block.paceStartSecondsPerKm;
      else if (type === 'END') targetPace = block.paceEndSecondsPerKm;

      if (targetPace && targetPace > 0) {
        return (thresholdPace / targetPace) * 100;
      }
      return 0;
    }

    // 3. SWIMMING
    if (this.training.sportType === 'SWIMMING') {
      const thresholdCss = user.criticalSwimSpeed || 90; // Default 1:30/100m
      let targetPace: number | undefined;

      if (type === 'TARGET') targetPace = block.swimPacePer100m;
      // Ramp/Interval swimming usually follows steady pace, or complex structure not fully mapped yet.
      // Fallback to generic pace fields if swim-specific is missing? 
      // For now, strict swim usage.

      if (targetPace && targetPace > 0) {
        return (thresholdCss / targetPace) * 100;
      }
      return 0;
    }

    return 0;
  }

  getBlockHeight(block: WorkoutBlock): number {
    const maxI = this.getMaxIntensity();
    if (block.type === 'PAUSE') return 100;
    if (block.type === 'FREE') return (65 / maxI) * 100;

    // Use effective intensity
    const target = this.getEffectiveIntensity(block, 'TARGET');
    const start = this.getEffectiveIntensity(block, 'START');
    const end = this.getEffectiveIntensity(block, 'END');

    const intensity = block.type === 'RAMP' ? Math.max(start, end) : target;
    return (intensity / maxI) * 100;
  }

  getBlockClipPath(block: WorkoutBlock): string {
    if (block.type !== 'RAMP') return 'none';

    const maxI = this.getMaxIntensity();
    const startVal = this.getEffectiveIntensity(block, 'START');
    const endVal = this.getEffectiveIntensity(block, 'END');

    const startH = (startVal / maxI) * 100;
    const endH = (endVal / maxI) * 100;
    const currentH = Math.max(startH, endH);

    // Calculate relative heights within the bar's own bounding box
    // Avoid division by zero
    if (currentH === 0) return 'none';

    const startRel = 100 - (startH / currentH) * 100;
    const endRel = 100 - (endH / currentH) * 100;

    return `polygon(0% ${startRel}%, 100% ${endRel}%, 100% 100%, 0% 100%)`;
  }

  getBlockColor(block: WorkoutBlock): string {
    if (block.type === 'PAUSE') return '#636e72';
    if (block.type === 'FREE') return '#636e72';
    if (block.type === 'WARMUP') return 'rgba(9, 132, 227, 0.6)';
    if (block.type === 'COOLDOWN') return 'rgba(108, 92, 231, 0.6)';

    const start = this.getEffectiveIntensity(block, 'START');
    const end = this.getEffectiveIntensity(block, 'END');
    const target = this.getEffectiveIntensity(block, 'TARGET');

    const power = block.type === 'RAMP'
      ? (start + end) / 2
      : target;

    if (power < 55) return '#b2bec3'; // Z1
    if (power < 75) return '#3498db'; // Z2
    if (power < 90) return '#2ecc71'; // Z3
    if (power < 105) return '#f1c40f'; // Z4
    if (power < 120) return '#e67e22'; // Z5
    return '#e74c3c'; // Z6
  }

  getDisplayIntensity(block: WorkoutBlock): string {
    if (block.type === 'PAUSE') return 'PAUSE';
    if (block.type === 'FREE') return 'FREE';

    // For RAMP, showing "Start% - End%" might mean recalculating text
    const start = Math.round(this.getEffectiveIntensity(block, 'START'));
    const end = Math.round(this.getEffectiveIntensity(block, 'END'));
    const target = Math.round(this.getEffectiveIntensity(block, 'TARGET'));

    if (block.type === 'RAMP') return `${start}%-${end}%`;
    return `${target}%`;
  }

  getMaxIntensity(): number {
    if (!this.training || !this.training.blocks) return 150;

    // Scan all blocks for max effective intensity
    const intensities = this.training.blocks.flatMap(b => [
      this.getEffectiveIntensity(b, 'TARGET'),
      this.getEffectiveIntensity(b, 'START'),
      this.getEffectiveIntensity(b, 'END')
    ]);

    const maxBlockIntensity = intensities.length > 0 ? Math.max(...intensities) : 0;
    return Math.max(150, maxBlockIntensity + 20);
  }

  getYAxisLabels(): number[] {
    const maxI = this.getMaxIntensity();
    const step = maxI > 200 ? 100 : 50;
    const labels = [];
    for (let i = 0; i <= maxI; i += step) {
      labels.unshift(i);
    }
    return labels;
  }

  getNumericalTotalDuration(): number {
    if (!this.training) return 0;
    if (this.training.estimatedDurationSeconds) return this.training.estimatedDurationSeconds;
    if (!this.training.blocks) return 0;
    return this.training.blocks.reduce((acc, b) => acc + (b.durationSeconds ?? 0) * (b.repeats || 1), 0);
  }

  getTotalDuration(): string {
    const totalSeconds = this.getNumericalTotalDuration();
    if (totalSeconds === 0) return '0 min';
    const m = Math.floor(totalSeconds / 60);
    return `${m}m`;
  }

  formatDuration(seconds: number | undefined): string {
    if (seconds === undefined) return '0m';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (s === 0) return `${m}m`;
    return `${m}m ${s}s`;
  }

  calculateIntensityValue(percent: number | undefined): string {
    if (percent === undefined || percent === 0 || !this.training) return '-';

    const user = this.authService.currentUser;

    if (this.training.sportType === 'CYCLING') {
      const ftp = user?.ftp || 250;
      return Math.round((percent * ftp) / 100).toString() + 'W';
    }

    if (this.training.sportType === 'RUNNING') {
      const threshold = user?.functionalThresholdPace || 240;
      const secondsPerKm = threshold / (percent / 100);
      if (!isFinite(secondsPerKm)) return '-';
      return this.formatPace(secondsPerKm) + '/km';
    }

    if (this.training.sportType === 'SWIMMING') {
      const threshold = user?.criticalSwimSpeed || 90;
      const secondsPer100m = threshold / (percent / 100);
      if (!isFinite(secondsPer100m)) return '-';
      return this.formatPace(secondsPer100m) + '/100m';
    }

    return percent.toString() + '%';
  }

  formatPace(totalSeconds: number): string {
    const m = Math.floor(totalSeconds / 60);
    const s = Math.round(totalSeconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  getSportUnit(): string {
    if (!this.training) return '%';
    if (this.training.sportType === 'CYCLING') return 'W';
    if (this.training.sportType === 'RUNNING') return 'min/km';
    if (this.training.sportType === 'SWIMMING') return 'min/100m';
    return '%';
  }

  private closeDropdownListener = () => {
    this.isExportDropdownOpen = false;
    document.removeEventListener('click', this.closeDropdownListener);
  };

  toggleExportDropdown(event: Event) {
    event.stopPropagation();

    if (this.isExportDropdownOpen) {
      this.closeDropdownListener(); // Close if open
    } else {
      this.isExportDropdownOpen = true;
      setTimeout(() => document.addEventListener('click', this.closeDropdownListener), 0);
    }
  }

  exportToZwift(): void {
    if (!this.training) return;
    this.closeDropdownListener(); // Close menu
    // Use current FTP value from service, default to 250
    this.trainingService.ftp$.subscribe(ftp => {
      this.exportService.exportToZwift(this.training!, ftp);
    }).unsubscribe();
  }

  exportToJSON(): void {
    if (!this.training) return;
    this.closeDropdownListener(); // Close menu
    this.exportService.exportToJSON(this.training);
  }

  private exportService = inject(ExportService);
}

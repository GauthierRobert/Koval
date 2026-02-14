import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Training, WorkoutBlock, TrainingService } from '../../services/training.service';
import { WorkoutExecutionService } from '../../services/workout-execution.service';
import { ExportService } from '../../services/export.service';
import { ScheduleModalComponent } from '../schedule-modal/schedule-modal.component';

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
  ftp$ = this.trainingService.ftp$;
  isExportDropdownOpen = false;
  isScheduleModalOpen = false;

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
    const totalDuration = this.training.blocks.reduce((acc, b) => acc + b.durationSeconds, 0);
    return (block.durationSeconds / totalDuration) * 100;
  }

  getBlockHeight(block: WorkoutBlock): number {
    const maxP = this.getMaxPower();
    if (block.type === 'FREE') return (65 / maxP) * 100; // Default height for free ride (Zone 2)
    const power = block.type === 'RAMP' ? Math.max(block.powerStartPercent || 0, block.powerEndPercent || 0) : (block.powerTargetPercent || 0);
    return (power / maxP) * 100;
  }

  getBlockClipPath(block: WorkoutBlock): string {
    if (block.type !== 'RAMP') return 'none';
    const maxP = this.getMaxPower();
    const startH = ((block.powerStartPercent || 0) / maxP) * 100;
    const endH = ((block.powerEndPercent || 0) / maxP) * 100;
    const currentH = Math.max(startH, endH);

    // Calculate relative heights within the bar's own bounding box
    const startRel = 100 - (startH / currentH) * 100;
    const endRel = 100 - (endH / currentH) * 100;

    return `polygon(0% ${startRel}%, 100% ${endRel}%, 100% 100%, 0% 100%)`;
  }

  getBlockColor(block: WorkoutBlock): string {
    if (block.type === 'FREE') return '#636e72'; // Distant Grey
    if (block.type === 'WARMUP') return 'rgba(9, 132, 227, 0.6)';
    if (block.type === 'COOLDOWN') return 'rgba(108, 92, 231, 0.6)';

    const power = block.type === 'RAMP'
      ? ((block.powerStartPercent || 0) + (block.powerEndPercent || 0)) / 2
      : (block.powerTargetPercent || 0);

    if (power < 55) return '#b2bec3'; // Zone 1 - Light Grey
    if (power < 75) return '#3498db'; // Zone 2 - Blue
    if (power < 90) return '#2ecc71'; // Zone 3 - Green
    if (power < 105) return '#f1c40f'; // Zone 4 - Yellow
    if (power < 120) return '#e67e22'; // Zone 5 - Orange
    return '#e74c3c'; // Zone 6 - Red
  }

  getDisplayPower(block: WorkoutBlock): string {
    if (block.type === 'FREE') return 'FREE';
    if (block.type === 'RAMP') return `${block.powerStartPercent}%-${block.powerEndPercent}%`;
    return `${block.powerTargetPercent}%`;
  }

  getMaxPower(): number {
    if (!this.training) return 150;
    const powers = this.training.blocks.flatMap(b => [
      b.powerTargetPercent || 0,
      b.powerStartPercent || 0,
      b.powerEndPercent || 0
    ]);
    const maxBlockPower = Math.max(...powers);
    return Math.max(150, maxBlockPower + 20); // Add some padding
  }

  getYAxisLabels(): number[] {
    const maxP = this.getMaxPower();
    const step = maxP > 200 ? 100 : 50;
    const labels = [];
    for (let i = 0; i <= maxP; i += step) {
      labels.unshift(i);
    }
    return labels;
  }

  getTotalDuration(): string {
    if (!this.training) return '0 min';
    const totalSeconds = this.training.blocks.reduce((acc, b) => acc + b.durationSeconds, 0);
    const m = Math.floor(totalSeconds / 60);
    return `${m}m`;
  }

  formatDuration(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (s === 0) return `${m}m`;
    return `${m}m ${s}s`;
  }

  calculateWatts(percent: number, ftp: number): number {
    return Math.round((percent * ftp) / 100);
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

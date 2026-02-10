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
  template: `
    <div class="viz-container" *ngIf="training">
      <div class="header">
        <div class="title-section">
          <h1>{{ training.title }}</h1>
          <p class="description">{{ training.description }}</p>
        </div>
        <div class="action-buttons">
          <div class="export-dropdown">
            <button class="export-btn" (click)="toggleExportDropdown($event)">
              EXPORT
              <span class="dropdown-caret">▼</span>
            </button>
            <div class="dropdown-menu glass" *ngIf="isExportDropdownOpen">
              <button class="dropdown-item" (click)="exportToZwift()">Zwift (.zwo)</button>
              <button class="dropdown-item" (click)="exportToJSON()">JSON</button>
            </div>
          </div>
          <button class="schedule-btn" (click)="openScheduleModal()">SCHEDULE</button>
          <button class="start-btn" (click)="startWorkout()">
            <span class="play-icon">▶</span> START WORKOUT
          </button>
        </div>
      </div>

      <app-schedule-modal
        [isOpen]="isScheduleModalOpen"
        [preselectedTraining]="training"
        mode="athlete"
        (closed)="isScheduleModalOpen = false"
        (scheduled)="isScheduleModalOpen = false"
      ></app-schedule-modal>

      <div class="chart-wrapper glass">
        <div class="y-axis">
          <span *ngFor="let label of getYAxisLabels()">{{ label }}%</span>
        </div>
        <div class="chart-area">
          <div 
            *ngFor="let block of training.blocks; let i = index" 
            class="block-bar-wrapper"
            [style.width.%]="getBlockWidth(block)"
          >
            <div class="bar-container" [style.height.%]="getBlockHeight(block)">
              <div 
                class="block-bar"
                [style.background]="getBlockColor(block)"
                [style.clip-path]="getBlockClipPath(block)"
                [attr.data-type]="block.type"
              ></div>
              
              <div class="block-tooltip glass" *ngIf="ftp$ | async as ftp">
                <span class="tooltip-label">{{ block.label }}</span>
                <span class="tooltip-type">{{ block.type }}</span>
                <ng-container *ngIf="block.type !== 'FREE'">
                  <span class="tooltip-power" *ngIf="block.type !== 'RAMP'">{{ block.powerTargetPercent }}%</span>
                  <span class="tooltip-power" *ngIf="block.type === 'RAMP'">{{ block.powerStartPercent }}% → {{ block.powerEndPercent }}%</span>
                  <span class="tooltip-watts" *ngIf="block.type !== 'RAMP'">{{ calculateWatts(block.powerTargetPercent || 0, ftp) }}W</span>
                  <span class="tooltip-watts" *ngIf="block.type === 'RAMP'">{{ calculateWatts(block.powerStartPercent || 0, ftp) }}W → {{ calculateWatts(block.powerEndPercent || 0, ftp) }}W</span>
                </ng-container>
                <span class="tooltip-duration">{{ formatDuration(block.durationSeconds) }}</span>
              </div>
              
              <div class="block-label" *ngIf="getBlockWidth(block) > 5">
                 {{ getDisplayPower(block) }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="stats glass">
        <div class="stat">
          <span class="label">Duration</span>
          <span class="value">{{ getTotalDuration() }}</span>
        </div>
        <div class="stat">
          <span class="label">Blocks</span>
          <span class="value">{{ training.blocks.length }}</span>
        </div>
        <div class="stat">
          <span class="label">Intensity</span>
          <span class="value">High</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .viz-container {
      padding: 24px 40px;
      height: 100%;
      display: flex;
      flex-direction: column;
      gap: 20px;
      overflow-y: auto;
    }
    .header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 20px;
    }
    .header h1 {
      font-size: 32px;
      margin: 0 0 8px 0;
      background: var(--accent-gradient);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .action-buttons {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    .export-dropdown {
      position: relative;
    }
    .export-btn {
      background: rgba(255,255,255,0.1);
      color: white;
      border: 1px solid var(--glass-border);
      padding: 10px 18px;
      border-radius: 10px;
      font-weight: 600;
      font-size: 13px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s;
    }
    .export-btn:hover {
      background: rgba(255,255,255,0.15);
    }
    .dropdown-caret {
      font-size: 10px;
      opacity: 0.7;
    }
    .dropdown-menu {
      position: absolute;
      top: 100%;
      right: 0;
      margin-top: 8px;
      min-width: 140px;
      background: #1e272e;
      border: 1px solid var(--glass-border);
      border-radius: 10px;
      padding: 6px;
      display: flex;
      flex-direction: column;
      gap: 2px;
      z-index: 100;
      box-shadow: 0 10px 30px rgba(0,0,0,0.5);
    }
    .dropdown-item {
      background: transparent;
      border: none;
      color: var(--text-muted);
      padding: 8px 12px;
      text-align: left;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      border-radius: 6px;
      transition: all 0.2s;
    }
    .dropdown-item:hover {
      background: rgba(255,255,255,0.1);
      color: white;
    }
    .schedule-btn {
      background: rgba(255,255,255,0.1);
      color: var(--accent-color);
      border: 1px solid var(--accent-color);
      padding: 10px 18px;
      border-radius: 10px;
      font-weight: 700;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.2s;
    }
    .schedule-btn:hover {
      background: rgba(255, 157, 0, 0.15);
    }
    .start-btn {
      background: #2ecc71;
      color: white;
      border: none;
      padding: 12px 24px;
      border-radius: 12px;
      font-weight: 700;
      font-size: 16px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 10px;
      transition: all 0.2s;
      box-shadow: 0 4px 15px rgba(46, 204, 113, 0.3);
    }
    .start-btn:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(46, 204, 113, 0.4);
      filter: brightness(1.1);
    }
    .play-icon { font-size: 12px; }
    .description {
      color: var(--text-muted);
      max-width: 600px;
      line-height: 1.6;
    }
    .chart-wrapper {
      height: 300px;
      display: flex;
      padding: 24px;
      position: relative;
    }
    .y-axis {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding-right: 12px;
      color: var(--text-muted);
      font-size: 10px;
      border-right: 1px solid var(--glass-border);
    }
    .chart-area {
      flex: 1;
      display: flex;
      align-items: flex-end;
      gap: 2px;
      padding-left: 8px;
    }
    .block-bar-wrapper {
      height: 100%;
      display: flex;
      align-items: flex-end;
    }
    .bar-container {
      width: 100%;
      position: relative;
      display: flex;
      flex-direction: column;
      justify-content: flex-end;
    }
    .block-bar {
      width: 100%;
      height: 100%;
      min-width: 2px;
      border-radius: 4px 4px 0 0;
      position: relative;
      transition: transform 0.2s, filter 0.2s;
    }
    .bar-container:hover .block-bar {
      transform: scaleY(1.01);
      filter: brightness(1.1);
    }
    .bar-container:hover .block-tooltip {
      opacity: 1;
      visibility: visible;
      transform: translateX(-50%) translateY(-10px);
    }
    .block-tooltip {
      position: absolute;
      bottom: 100%;
      left: 50%;
      transform: translateX(-50%) translateY(0);
      padding: 12px;
      min-width: 160px;
      z-index: 100;
      opacity: 0;
      visibility: hidden;
      transition: all 0.2s;
      display: flex;
      flex-direction: column;
      gap: 4px;
      pointer-events: none;
      box-shadow: 0 10px 30px rgba(0,0,0,0.5);
    }
    .tooltip-label { font-size: 11px; text-transform: uppercase; color: var(--text-muted); }
    .tooltip-type { font-size: 9px; color: var(--accent-color); font-weight: bold; }
    .tooltip-power { font-size: 16px; font-weight: 700; color: white; }
    .tooltip-watts { font-size: 14px; color: var(--accent-color); font-weight: 600; }
    .tooltip-duration { font-size: 12px; color: var(--text-muted); }

    .block-bar[data-type="FREE"] {
      background: repeating-linear-gradient(
        45deg,
        #666,
        #666 10px,
        #555 10px,
        #555 20px
      ) !important;
    }

    .block-bar[data-type="WARMUP"] {
      border: 2px solid #0984e3;
      backdrop-filter: blur(4px);
    }

    .block-bar[data-type="COOLDOWN"] {
      border: 2px solid #6c5ce7;
      backdrop-filter: blur(4px);
    }
    
    .block-label {
      position: absolute;
      bottom: 10px;
      left: 50%;
      transform: translateX(-50%);
      font-size: 10px;
      font-weight: 600;
      color: rgba(255,255,255,0.9);
      text-shadow: 0 1px 2px rgba(0,0,0,0.5);
      white-space: nowrap;
      pointer-events: none;
      z-index: 5;
    }
    .stats {
      display: flex;
      gap: 24px;
      padding: 24px;
    }
    .stat {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .stat .label {
      font-size: 12px;
      color: var(--text-muted);
      text-transform: uppercase;
    }
    .stat .value {
      font-size: 20px;
      font-weight: 700;
    }
  `]
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

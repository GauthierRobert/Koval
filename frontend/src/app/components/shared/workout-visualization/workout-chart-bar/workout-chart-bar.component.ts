import { ChangeDetectionStrategy, Component, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { WorkoutBlock, flattenElements, Training } from '../../../../models/training.model';
import { AuthService } from '../../../../services/auth.service';
import { DurationEstimationService } from '../../../../services/duration-estimation.service';
import { ZoneSystem } from '../../../../services/zone';
import { formatPace as sharedFormatPace } from '../../format/format.utils';

@Component({
  selector: 'app-workout-chart-bar',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './workout-chart-bar.component.html',
  styleUrl: './workout-chart-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutChartBarComponent {
  @Input() blocks: WorkoutBlock[] = [];
  @Input() displayUnit: 'PERCENT' | 'ABSOLUTE' = 'PERCENT';
  @Input() ftp: number = 0;
  @Input() sportType: string = '';
  @Input() compact: boolean = false;
  @Input() training: Training | null = null;
  @Input() currentZoneSystem: ZoneSystem | null = null;

  private authService = inject(AuthService);
  private durationService = inject(DurationEstimationService);
  private translate = inject(TranslateService);

  get displayFlatBlocks(): WorkoutBlock[] {
    return flattenElements(this.blocks);
  }

  getBlockWidth(block: WorkoutBlock): number {
    if (!this.training) return 0;
    const totalDuration = this.getNumericalTotalDuration();
    if (totalDuration === 0) return 0;
    return ((this.getEstimatedBlockDuration(block)) / totalDuration) * 100;
  }

  getBlockHeight(block: WorkoutBlock): number {
    const maxI = this.getMaxIntensity();
    if (block.type === 'PAUSE') return 100;
    if (block.type === 'FREE') return (65 / maxI) * 100;
    if (block.type === 'TRANSITION') return (30 / maxI) * 100;

    const target = this.getEffectiveIntensity(block, 'TARGET');
    const start = this.getEffectiveIntensity(block, 'START');
    const end = this.getEffectiveIntensity(block, 'END');

    const intensity = block.type === 'RAMP' ? Math.max(start, end) : target;
    return (intensity / maxI) * 100;
  }

  getBlockColor(block: WorkoutBlock): string {
    if (block.type === 'PAUSE') return '#636e72';
    if (block.type === 'FREE') return '#636e72';
    if (block.type === 'TRANSITION') return '#fd79a8';
    if (block.type === 'WARMUP') return 'rgba(9, 132, 227, 0.6)';
    if (block.type === 'COOLDOWN') return 'rgba(108, 92, 231, 0.6)';

    const start = this.getEffectiveIntensity(block, 'START');
    const end = this.getEffectiveIntensity(block, 'END');
    const target = this.getEffectiveIntensity(block, 'TARGET');

    const power = block.type === 'RAMP' ? (start + end) / 2 : target;

    if (power < 55) return '#b2bec3';
    if (power < 75) return '#3498db';
    if (power < 90) return '#2ecc71';
    if (power < 105) return '#f1c40f';
    if (power < 120) return '#e67e22';
    return '#e74c3c';
  }

  getBlockClipPath(block: WorkoutBlock): string {
    if (block.type !== 'RAMP') return 'none';

    const maxI = this.getMaxIntensity();
    const startVal = this.getEffectiveIntensity(block, 'START');
    const endVal = this.getEffectiveIntensity(block, 'END');

    const startH = (startVal / maxI) * 100;
    const endH = (endVal / maxI) * 100;
    const currentH = Math.max(startH, endH);

    if (currentH === 0) return 'none';

    const startRel = 100 - (startH / currentH) * 100;
    const endRel = 100 - (endH / currentH) * 100;

    return `polygon(0% ${startRel}%, 100% ${endRel}%, 100% 100%, 0% 100%)`;
  }

  getEffectiveIntensity(block: WorkoutBlock, type: 'TARGET' | 'START' | 'END' = 'TARGET'): number {
    let percent: number | undefined;
    if (type === 'TARGET') percent = block.intensityTarget;
    else if (type === 'START') percent = block.intensityStart;
    else if (type === 'END') percent = block.intensityEnd;

    if (percent !== undefined && percent !== null) return percent;
    return 0;
  }

  getMaxIntensity(): number {
    if (!this.training) return 150;

    const intensities = this.displayFlatBlocks.flatMap(b => [
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
    return this.displayFlatBlocks.reduce((acc, b) => acc + this.getEstimatedBlockDuration(b), 0);
  }

  getEstimatedBlockDuration(block: WorkoutBlock): number {
    if (!this.training) return 0;
    return this.durationService.estimateDuration(block, this.training, this.currentZoneSystem);
  }

  getBlockIntensityDisplay(block: WorkoutBlock): string {
    if (block.zoneLabel) return block.zoneLabel;
    if (block.zoneTarget) return block.zoneTarget;
    if (block.type === 'PAUSE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_PAUSE');
    if (block.type === 'FREE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_FREE');
    if (block.type === 'TRANSITION') return block.transitionType ?? 'T';
    if (block.type === 'RAMP') {
      return `${block.intensityStart || 0}% → ${block.intensityEnd || 0}%`;
    }
    return block.intensityTarget ? `${block.intensityTarget}%` : '-';
  }

  getDisplayIntensity(block: WorkoutBlock): string {
    if (block.type === 'PAUSE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_PAUSE').toUpperCase();
    if (block.type === 'FREE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_FREE').toUpperCase();
    if (block.type === 'TRANSITION') return block.transitionType ?? 'T';

    const start = this.getEffectiveIntensity(block, 'START');
    const end = this.getEffectiveIntensity(block, 'END');
    const target = this.getEffectiveIntensity(block, 'TARGET');

    if (this.displayUnit === 'ABSOLUTE') {
      if (block.type === 'RAMP') {
        return `${this.calculateIntensityValue(start)} - ${this.calculateIntensityValue(end)}`;
      }
      return this.calculateIntensityValue(target);
    }

    if (block.type === 'RAMP') return `${Math.round(start)}%-${Math.round(end)}%`;
    return `${Math.round(target)}%`;
  }

  isNarrowBlock(block: WorkoutBlock): boolean {
    return block.type === 'PAUSE' || this.getBlockWidth(block) < 5;
  }

  blockColor(block: WorkoutBlock): string {
    return this.getBlockColor(block);
  }

  calculateIntensityValue(percent: number | undefined): string {
    if (percent === undefined || percent === 0 || !this.training) return '-';

    const user = this.authService.currentUser;

    if (this.training.sportType === 'CYCLING') {
      if (!user?.ftp) return `${percent}%`;
      return Math.round((percent * user.ftp) / 100).toString() + 'W';
    }

    if (this.training.sportType === 'RUNNING') {
      if (!user?.functionalThresholdPace) return `${percent}%`;
      const secondsPerKm = user.functionalThresholdPace / (percent / 100);
      if (!isFinite(secondsPerKm)) return '-';
      return this.formatPace(secondsPerKm) + '/km';
    }

    if (this.training.sportType === 'SWIMMING') {
      if (!user?.criticalSwimSpeed) return `${percent}%`;
      const secondsPer100m = user.criticalSwimSpeed / (percent / 100);
      if (!isFinite(secondsPer100m)) return '-';
      return this.formatPace(secondsPer100m) + '/100m';
    }

    return percent.toString() + '%';
  }

  formatPace(totalSeconds: number): string {
    return sharedFormatPace(totalSeconds);
  }

  formatBlockDurationOrDistance(block: WorkoutBlock): string {
    if (!block.durationSeconds && block.distanceMeters) {
      const km = block.distanceMeters / 1000;
      return km >= 1 ? `${km}km` : `${block.distanceMeters}m`;
    }
    return this.formatDuration(block.durationSeconds, block);
  }

  formatDuration(seconds: number | undefined, block?: WorkoutBlock): string {
    if (seconds === undefined && block) {
      seconds = this.getEstimatedBlockDuration(block);
    }
    if (seconds === undefined) return '0min';

    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (s === 0) return `${m}min`;
    if (m === 0) return `${s}sec`;
    return `${m}m ${s}sec`;
  }
}

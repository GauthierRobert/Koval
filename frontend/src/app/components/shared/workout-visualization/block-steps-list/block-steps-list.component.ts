import { ChangeDetectionStrategy, Component, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { WorkoutBlock } from '../../../../models/training.model';
import { isSet } from '../../../../models/training.model';
import { AuthService } from '../../../../services/auth.service';
import { DurationEstimationService } from '../../../../services/duration-estimation.service';
import { Training } from '../../../../models/training.model';
import { ZoneSystem } from '../../../../services/zone';
import { formatPace as sharedFormatPace } from '../../format/format.utils';
import { getBlockColor as sharedGetBlockColor } from '../../block-helpers/block-helpers';

@Component({
  selector: 'app-block-steps-list',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './block-steps-list.component.html',
  styleUrl: './block-steps-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BlockStepsListComponent {
  @Input() blocks: WorkoutBlock[] = [];
  @Input() displayUnit: 'PERCENT' | 'ABSOLUTE' = 'PERCENT';
  @Input() ftp: number = 0;
  @Input() sportType: string = '';
  @Input() training: Training | null = null;
  @Input() currentZoneSystem: ZoneSystem | null = null;

  private authService = inject(AuthService);
  private durationService = inject(DurationEstimationService);
  private translate = inject(TranslateService);

  isSetElement(block: WorkoutBlock): boolean {
    return isSet(block);
  }

  blockColor(block: WorkoutBlock): string {
    return this.getBlockColor(block);
  }

  getBlockColor(block: WorkoutBlock): string {
    return sharedGetBlockColor(block, this.sportType);
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

  formatBlockDurationOrDistance(block: WorkoutBlock): string {
    if (!block.durationSeconds && block.distanceMeters) {
      const km = block.distanceMeters / 1000;
      return km >= 1 ? `${km}km` : `${block.distanceMeters}m`;
    }
    return this.formatDuration(block.durationSeconds, block);
  }

  getEffectiveIntensity(block: WorkoutBlock, type: 'TARGET' | 'START' | 'END' = 'TARGET'): number {
    let percent: number | undefined;
    if (type === 'TARGET') percent = block.intensityTarget;
    else if (type === 'START') percent = block.intensityStart;
    else if (type === 'END') percent = block.intensityEnd;

    if (percent !== undefined && percent !== null) return percent;
    return 0;
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

  formatBlockEstimates(block: WorkoutBlock): { duration: string; durationEstimated: boolean; distance: string; distanceEstimated: boolean } {
    const hasDuration = (block.durationSeconds ?? 0) > 0;
    const hasDistance = (block.distanceMeters ?? 0) > 0;

    let duration: string;
    let durationEstimated: boolean;
    if (hasDuration) {
      duration = this.formatDuration(block.durationSeconds);
      durationEstimated = false;
    } else {
      const est = this.getEstimatedBlockDuration(block);
      duration = est > 0 ? this.formatDuration(est) : '-';
      durationEstimated = est > 0;
    }

    let distance: string;
    let distanceEstimated: boolean;
    if (hasDistance) {
      const km = block.distanceMeters! / 1000;
      distance = km >= 1 ? `${km.toFixed(1)}km` : `${block.distanceMeters}m`;
      distanceEstimated = false;
    } else {
      const est = this.getEstimatedBlockDistance(block);
      if (est > 0) {
        const km = est / 1000;
        distance = km >= 1 ? `${km.toFixed(1)}km` : `${est}m`;
      } else {
        distance = '-';
      }
      distanceEstimated = est > 0;
    }

    return { duration, durationEstimated, distance, distanceEstimated };
  }

  isDurationEstimated(): boolean {
    return false; // Not needed in this component context
  }

  getEstimatedBlockDuration(block: WorkoutBlock): number {
    if (!this.training) return 0;
    return this.durationService.estimateDuration(block, this.training, this.currentZoneSystem);
  }

  getEstimatedBlockDistance(block: WorkoutBlock): number {
    if (!this.training) return 0;
    return this.durationService.estimateDistance(block, this.training, this.currentZoneSystem);
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
}

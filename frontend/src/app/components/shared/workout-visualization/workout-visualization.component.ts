import {Component, inject, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TrainingService} from '../../../services/training.service';
import {flattenElements, hasDurationEstimate, isSet, Training, WorkoutBlock} from '../../../models/training.model';
import {WorkoutExecutionService} from '../../../services/workout-execution.service';
import {HttpClient} from '@angular/common/http';
import {ExportService} from '../../../services/export.service';
import {TrainingActionModalComponent} from '../training-action-modal/training-action-modal.component';
import {AuthService} from '../../../services/auth.service';
import {DurationEstimationService} from '../../../services/duration-estimation.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {formatPace as sharedFormatPace} from '../format/format.utils';
import {environment} from '../../../../environments/environment';

@Component({
  selector: 'app-workout-visualization',
  standalone: true,
  imports: [CommonModule, TrainingActionModalComponent, TranslateModule],
  templateUrl: './workout-visualization.component.html',
  styleUrl: './workout-visualization.component.css'
})
export class WorkoutVisualizationComponent {
  @Input() training: Training | null = null;
  @Input() compact = false;
  private trainingService = inject(TrainingService);
  private executionService = inject(WorkoutExecutionService);
  private authService = inject(AuthService);
  private durationService = inject(DurationEstimationService);
  private zoneService = inject(ZoneService);

  private router = inject(Router);
  private currentZoneSystem: ZoneSystem | null = null;
  private translate = inject(TranslateService);

  ngOnChanges() {
    if (this.training?.zoneSystemId) {
      this.zoneService.getZoneSystemById(this.training.zoneSystemId).subscribe({
        next: (zs) => this.currentZoneSystem = zs,
        error: () => this.currentZoneSystem = null
      });
    } else {
      this.currentZoneSystem = null;
    }
  }
  user$ = this.authService.user$;
  isExportDropdownOpen = false;
  isScheduleModalOpen = false;
  displayUnit: 'PERCENT' | 'ABSOLUTE' = 'PERCENT'; // Default to %

  get displayBlocks(): WorkoutBlock[] {
    return this.training?.blocks ?? [];
  }

  /** Flat list for chart, metrics, zone distribution. */
  get displayFlatBlocks(): WorkoutBlock[] {
    return flattenElements(this.displayBlocks);
  }

  isSetElement(block: WorkoutBlock): boolean {
    return isSet(block);
  }

  isOwner(): boolean {
    const user = this.authService.currentUser;
    return !!user && !!this.training && this.training.createdBy === user.id;
  }

  enterEditMode(): void {
    if (this.training?.id) {
      this.router.navigate(['/builder', this.training.id]);
    }
  }

  hasReferenceValue(): boolean {
    if (!this.training) return false;
    const user = this.authService.currentUser;
    if (this.training.sportType === 'CYCLING') return !!user?.ftp;
    if (this.training.sportType === 'RUNNING') return !!user?.functionalThresholdPace;
    if (this.training.sportType === 'SWIMMING') return !!user?.criticalSwimSpeed;
    return false;
  }

  toggleUnits() {
    if (!this.hasReferenceValue()) {
      this.displayUnit = 'PERCENT';
      return;
    }
    this.displayUnit = this.displayUnit === 'PERCENT' ? 'ABSOLUTE' : 'PERCENT';
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
    return ((this.getEstimatedBlockDuration(block)) / totalDuration) * 100;
  }

  getEffectiveIntensity(block: WorkoutBlock, type: 'TARGET' | 'START' | 'END' = 'TARGET'): number {
    let percent: number | undefined;
    if (type === 'TARGET') percent = block.intensityTarget;
    else if (type === 'START') percent = block.intensityStart;
    else if (type === 'END') percent = block.intensityEnd;

    if (percent !== undefined && percent !== null) return percent;
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
    if (block.type === 'PAUSE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_PAUSE').toUpperCase();
    if (block.type === 'FREE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_FREE').toUpperCase();

    const start = this.getEffectiveIntensity(block, 'START');
    const end = this.getEffectiveIntensity(block, 'END');
    const target = this.getEffectiveIntensity(block, 'TARGET');

    if (this.displayUnit === 'ABSOLUTE') {
      if (block.type === 'RAMP') {
        return `${this.calculateIntensityValue(start)} - ${this.calculateIntensityValue(end)}`;
      }
      return this.calculateIntensityValue(target);
    }

    // PERCENT mode
    if (block.type === 'RAMP') return `${Math.round(start)}%-${Math.round(end)}%`;
    return `${Math.round(target)}%`;
  }

  getMaxIntensity(): number {
    if (!this.training) return 150;

    // Scan all flat blocks for max effective intensity
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

  // Helper to centralize estimation
  getEstimatedBlockDuration(block: WorkoutBlock): number {
    if (!this.training) return 0;
    return this.durationService.estimateDuration(block, this.training, this.currentZoneSystem);
  }

  isDurationEstimated(): boolean {
    return this.training ? hasDurationEstimate(this.training) : false;
  }

  getTotalDuration(): string {
    const totalSeconds = this.getNumericalTotalDuration();
    if (totalSeconds === 0) return '0 min';
    const m = Math.floor(totalSeconds / 60);
    return `${m}m`;
  }

  formatDuration(seconds: number | undefined, block?: WorkoutBlock): string {
    // If undefined provided, try to estimate from block if given?
    if (seconds === undefined && block) {
      seconds = this.getEstimatedBlockDuration(block);
    }
    if (seconds === undefined) return '0min';

    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (s === 0) return `${m}min`;
    if(m === 0) return `${s}sec`
    return `${m}m ${s}sec`;
  }

  /**
   * Returns the display string for a block's duration/distance.
   * When no duration is set but distance is, shows the distance.
   */
  formatBlockDurationOrDistance(block: WorkoutBlock): string {
    if (!block.durationSeconds && block.distanceMeters) {
      const km = block.distanceMeters / 1000;
      return km >= 1 ? `${km}km` : `${block.distanceMeters}m`;
    }
    return this.formatDuration(block.durationSeconds, block);
  }

  /**
   * Returns true when the block is too narrow to display a horizontal label.
   * Threshold is 5% of total width.
   */
  isNarrowBlock(block: WorkoutBlock): boolean {
    return block.type === 'PAUSE' || this.getBlockWidth(block) < 5;
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
      this.exportService.exportToZwift(this.training!, ftp ?? undefined);
    }).unsubscribe();
  }

  exportToJSON(): void {
    if (!this.training) return;
    this.closeDropdownListener(); // Close menu
    this.exportService.exportToJSON(this.training);
  }

  sendToZwift(): void {
    if (!this.training) return;
    this.closeDropdownListener();
    this.http.post(`${environment.apiUrl}/api/integration/zwift/push-workout/${this.training.id}`, {}).subscribe({
      error: () => {},
    });
  }

  private exportService = inject(ExportService);
  private http = inject(HttpClient);

  getBlockZoneName(block: WorkoutBlock): string {
    if (block.zoneTarget) return block.zoneTarget.toUpperCase();

    const intensity =
      block.type === 'RAMP'
        ? ((block.intensityStart || 0) + (block.intensityEnd || 0)) / 2
        : block.intensityTarget || 0;

    if (this.currentZoneSystem) {
      const zone = this.currentZoneSystem.zones.find(
        (z) => intensity >= z.low && intensity <= z.high,
      );
      if (zone) return zone.label.toUpperCase();
    }

    // Hardcoded fallback
    if (intensity < 55) return 'Z1';
    if (intensity < 75) return 'Z2';
    if (intensity < 90) return 'Z3';
    if (intensity < 105) return 'Z4';
    if (intensity < 120) return 'Z5';
    return 'Z6';
  }

  private readonly ZONE_COLORS: Record<string, string> = {
    Z1: '#b2bec3',
    Z2: '#3498db',
    Z3: '#2ecc71',
    Z4: '#f1c40f',
    Z5: '#e67e22',
    Z6: '#e74c3c',
  };

  getZoneRepartition(): { label: string; color: string; seconds: number; percentage: number }[] {
    if (!this.training) return [];

    const zoneMap = new Map<string, number>();
    let totalSeconds = 0;

    for (const block of this.displayFlatBlocks) {
      if (block.type === 'PAUSE') continue;
      const dur = this.getEstimatedBlockDuration(block);
      if (dur <= 0) continue;
      const zone = this.getBlockZoneName(block);
      zoneMap.set(zone, (zoneMap.get(zone) || 0) + dur);
      totalSeconds += dur;
    }

    if (totalSeconds === 0) return [];

    // Sort by zone label
    const sorted = [...zoneMap.entries()].sort((a, b) => a[0].localeCompare(b[0]));

    return sorted.map(([label, seconds]) => ({
      label,
      color: this.ZONE_COLORS[label] || '#636e72',
      seconds,
      percentage: Math.round((seconds / totalSeconds) * 100),
    }));
  }

  getEstimatedBlockDistance(block: WorkoutBlock): number {
    if (!this.training) return 0;
    return this.durationService.estimateDistance(block, this.training, this.currentZoneSystem);
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

  getBlockIntensityDisplay(block: WorkoutBlock): string {
    if (block.zoneLabel) return block.zoneLabel;
    if (block.zoneTarget) return block.zoneTarget;
    if (block.type === 'PAUSE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_PAUSE');
    if (block.type === 'FREE') return this.translate.instant('WORKOUT_VIZ.INTENSITY_FREE');
    if (block.type === 'RAMP') {
      return `${block.intensityStart || 0}% → ${block.intensityEnd || 0}%`;
    }
    return block.intensityTarget ? `${block.intensityTarget}%` : '-';
  }
}

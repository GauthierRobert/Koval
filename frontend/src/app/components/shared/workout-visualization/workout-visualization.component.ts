import {Component, inject, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TrainingService} from '../../../services/training.service';
import {
  flattenElements,
  hasDurationEstimate,
  isSet,
  SPORT_OPTIONS,
  Training,
  TRAINING_TYPE_COLORS,
  TRAINING_TYPE_LABELS,
  TrainingType,
  WorkoutBlock,
} from '../../../models/training.model';
import {WorkoutExecutionService} from '../../../services/workout-execution.service';
import {HttpClient} from '@angular/common/http';
import {ExportService} from '../../../services/export.service';
import {TrainingActionModalComponent} from '../training-action-modal/training-action-modal.component';
import {BlockStepsListComponent} from './block-steps-list/block-steps-list.component';
import {WorkoutChartBarComponent} from './workout-chart-bar/workout-chart-bar.component';
import {AuthService} from '../../../services/auth.service';
import {NolioSyncService} from '../../../services/nolio-sync.service';
import {DurationEstimationService} from '../../../services/duration-estimation.service';
import {ZoneService} from '../../../services/zone.service';
import {ZoneSystem} from '../../../services/zone';
import {formatPace as sharedFormatPace} from '../format/format.utils';
import {environment} from '../../../../environments/environment';
import {getBlockColor as sharedGetBlockColor, normalizeSport} from '../block-helpers/block-helpers';
import {ZoneClassificationService} from '../../../services/zone-classification.service';

@Component({
  selector: 'app-workout-visualization',
  standalone: true,
  imports: [CommonModule, TrainingActionModalComponent, TranslateModule, BlockStepsListComponent, WorkoutChartBarComponent],
  templateUrl: './workout-visualization.component.html',
  styleUrl: './workout-visualization.component.css'
})
export class WorkoutVisualizationComponent {
  @Input() training: Training | null = null;
  @Input() compact = false;
  private trainingService = inject(TrainingService);
  private executionService = inject(WorkoutExecutionService);
  private authService = inject(AuthService);
  nolioSync = inject(NolioSyncService);
  private durationService = inject(DurationEstimationService);
  private zoneService = inject(ZoneService);
  private zoneCls = inject(ZoneClassificationService);

  private router = inject(Router);
  currentZoneSystem: ZoneSystem | null = null;
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
      this.router.navigate(['/trainings', this.training.id, 'edit']);
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
    if (block.type === 'TRANSITION') return (30 / maxI) * 100;

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
    return sharedGetBlockColor(block, this.training?.sportType);
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

  getSportLabel(): string {
    if (!this.training) return '';
    const opt = SPORT_OPTIONS.find((o) => o.value === this.training!.sportType);
    return opt?.label ?? this.training.sportType;
  }

  getSportColor(): string {
    if (!this.training) return 'var(--text-muted)';
    switch (this.training.sportType) {
      case 'SWIMMING': return '#06b6d4';
      case 'CYCLING': return '#22c55e';
      case 'RUNNING': return '#f97316';
      case 'BRICK': return '#a855f7';
      default: return 'var(--text-muted)';
    }
  }

  getTrainingTypeLabel(): string {
    if (!this.training?.trainingType) return '';
    return TRAINING_TYPE_LABELS[this.training.trainingType as TrainingType] ?? this.training.trainingType;
  }

  getTrainingTypeColor(): string {
    if (!this.training?.trainingType) return 'var(--accent-color)';
    return TRAINING_TYPE_COLORS[this.training.trainingType as TrainingType] ?? 'var(--accent-color)';
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

  pushToNolio(): void {
    if (!this.training) return;
    this.closeDropdownListener();
    this.nolioSync.pushTraining(this.training.id).subscribe({
      next: (updated) => {
        if (this.training) {
          this.training.nolioWorkoutId = updated.nolioWorkoutId;
          this.training.nolioSyncStatus = updated.nolioSyncStatus;
          this.training.nolioLastSyncedAt = updated.nolioLastSyncedAt;
          this.training.nolioSyncError = updated.nolioSyncError;
        }
      },
      error: () => {},
    });
  }

  canPushToNolio(): boolean {
    const user = this.authService.currentUser;
    return !!user?.linkedAccounts?.nolioWrite && this.isOwner();
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
      const zi = this.zoneCls.classifyZone(intensity, this.currentZoneSystem.zones);
      const zone = this.currentZoneSystem.zones[zi];
      if (zone) return zone.label.toUpperCase();
    }

    const sport = normalizeSport(this.training?.sportType);
    const defaultZones = this.zoneCls.defaultZonesBySport[sport];
    const zi = this.zoneCls.classifyZone(intensity, defaultZones);
    return defaultZones[zi]?.label.toUpperCase() ?? 'Z1';
  }

  getZoneRepartition(): { label: string; color: string; seconds: number; percentage: number }[] {
    if (!this.training) return [];

    const sport = normalizeSport(this.training.sportType);
    const zones = this.currentZoneSystem?.zones ?? this.zoneCls.defaultZonesBySport[sport];
    const zoneMap = new Map<string, number>();
    let totalSeconds = 0;

    for (const block of this.displayFlatBlocks) {
      if (block.type === 'PAUSE' || block.type === 'TRANSITION') continue;
      const dur = this.getEstimatedBlockDuration(block);
      if (dur <= 0) continue;
      const zone = this.getBlockZoneName(block);
      zoneMap.set(zone, (zoneMap.get(zone) || 0) + dur);
      totalSeconds += dur;
    }

    if (totalSeconds === 0) return [];

    // Sort by zone label
    const sorted = [...zoneMap.entries()].sort((a, b) => a[0].localeCompare(b[0]));

    return sorted.map(([label, seconds]) => {
      const zi = zones.findIndex(z => z.label.toUpperCase() === label);
      return {
        label,
        color: zi >= 0 ? this.zoneCls.getZoneColor(zi, zones, sport) : '#636e72',
        seconds,
        percentage: Math.round((seconds / totalSeconds) * 100),
      };
    });
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
    if (block.type === 'TRANSITION') return block.transitionType ?? 'T';
    if (block.type === 'RAMP') {
      return `${block.intensityStart || 0}% → ${block.intensityEnd || 0}%`;
    }
    return block.intensityTarget ? `${block.intensityTarget}%` : '-';
  }
}

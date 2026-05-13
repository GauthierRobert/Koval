import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  Input,
  OnChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TrainingService } from '../../../services/training.service';
import {
  flattenElements,
  hasDurationEstimate,
  isSet,
  Training,
  WorkoutBlock,
} from '../../../models/training.model';
import {
  blockClipPath,
  blockEstimateDisplay,
  blockHeightPercent,
  effectiveIntensity,
  formatBlockDuration,
  formatDistanceMeters,
  formatTotalSeconds,
  intensityValueFromUser,
  maxFlatIntensity,
  sportColorFor,
  sportLabelFor,
  sportUnitFor,
  trainingTypeColorFor,
  trainingTypeLabelFor,
  yAxisLabels,
} from './workout-visualization.utils';
import { WorkoutExecutionService } from '../../../services/workout-execution.service';
import { HttpClient } from '@angular/common/http';
import { ExportService } from '../../../services/export.service';
import { TrainingActionModalComponent } from '../training-action-modal/training-action-modal.component';
import { BlockStepsListComponent } from './block-steps-list/block-steps-list.component';
import { WorkoutChartBarComponent } from './workout-chart-bar/workout-chart-bar.component';
import { FavoriteStarComponent } from '../favorite-star/favorite-star.component';
import { TrainingTagEditorComponent } from '../training-tag-editor/training-tag-editor.component';
import { AuthService } from '../../../services/auth.service';
import { NolioSyncService } from '../../../services/nolio-sync.service';
import { DurationEstimationService } from '../../../services/duration-estimation.service';
import { ZoneService } from '../../../services/zone.service';
import { ZoneSystem } from '../../../services/zone';
import { formatPace as sharedFormatPace } from '../format/format.utils';
import { environment } from '../../../../environments/environment';
import {
  getBlockColor as sharedGetBlockColor,
  normalizeSport,
} from '../block-helpers/block-helpers';
import { ZoneClassificationService } from '../../../services/zone-classification.service';

@Component({
  selector: 'app-workout-visualization',
  standalone: true,
  imports: [
    CommonModule,
    TrainingActionModalComponent,
    TranslateModule,
    BlockStepsListComponent,
    WorkoutChartBarComponent,
    FavoriteStarComponent,
    TrainingTagEditorComponent,
  ],
  templateUrl: './workout-visualization.component.html',
  styleUrl: './workout-visualization.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutVisualizationComponent implements OnChanges {
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
  private cdr = inject(ChangeDetectorRef);

  ngOnChanges() {
    if (this.training?.zoneSystemId) {
      this.zoneService.getZoneSystemById(this.training.zoneSystemId).subscribe({
        next: (zs) => {
          this.currentZoneSystem = zs;
          this.cdr.markForCheck();
        },
        error: () => {
          this.currentZoneSystem = null;
          this.cdr.markForCheck();
        },
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

  onToggleFavorite(): void {
    if (!this.training?.id) return;
    this.trainingService.toggleFavorite(this.training.id).subscribe({ error: () => {} });
  }

  onTagsChange(tags: string[]): void {
    if (!this.training?.id) return;
    this.trainingService.updateTrainingTags(this.training.id, tags).subscribe({ error: () => {} });
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
    return (this.getEstimatedBlockDuration(block) / totalDuration) * 100;
  }

  getEffectiveIntensity(block: WorkoutBlock, type: 'TARGET' | 'START' | 'END' = 'TARGET'): number {
    return effectiveIntensity(block, type);
  }

  getBlockHeight(block: WorkoutBlock): number {
    return blockHeightPercent(block, this.getMaxIntensity());
  }

  getBlockClipPath(block: WorkoutBlock): string {
    return blockClipPath(block, this.getMaxIntensity(), effectiveIntensity);
  }

  getBlockColor(block: WorkoutBlock): string {
    return sharedGetBlockColor(block, this.training?.sportType);
  }

  getDisplayIntensity(block: WorkoutBlock): string {
    if (block.type === 'PAUSE')
      return this.translate.instant('WORKOUT_VIZ.INTENSITY_PAUSE').toUpperCase();
    if (block.type === 'FREE')
      return this.translate.instant('WORKOUT_VIZ.INTENSITY_FREE').toUpperCase();
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
    return this.training ? maxFlatIntensity(this.displayFlatBlocks) : 150;
  }

  getYAxisLabels(): number[] {
    return yAxisLabels(this.getMaxIntensity());
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
    return formatTotalSeconds(this.getNumericalTotalDuration());
  }

  formatDuration(seconds: number | undefined, block?: WorkoutBlock): string {
    const resolved = seconds ?? (block ? this.getEstimatedBlockDuration(block) : undefined);
    return formatBlockDuration(resolved);
  }

  formatBlockDurationOrDistance(block: WorkoutBlock): string {
    if (!block.durationSeconds && block.distanceMeters) {
      return formatDistanceMeters(block.distanceMeters);
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
    if (!this.training) return '-';
    return intensityValueFromUser(percent, this.training, this.authService.currentUser);
  }

  formatPace(totalSeconds: number): string {
    return sharedFormatPace(totalSeconds);
  }

  getSportLabel(): string {
    return sportLabelFor(this.training);
  }
  getSportColor(): string {
    return sportColorFor(this.training);
  }
  getTrainingTypeLabel(): string {
    return trainingTypeLabelFor(this.training);
  }
  getTrainingTypeColor(): string {
    return trainingTypeColorFor(this.training);
  }
  getSportUnit(): string {
    return sportUnitFor(this.training);
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
    this.closeDropdownListener();
    const ftp = this.trainingService.currentFtp;
    this.exportService.exportToZwift(this.training, ftp ?? undefined);
  }

  exportToJSON(): void {
    if (!this.training) return;
    this.closeDropdownListener(); // Close menu
    this.exportService.exportToJSON(this.training);
  }

  sendToZwift(): void {
    if (!this.training) return;
    this.closeDropdownListener();
    this.http
      .post(`${environment.apiUrl}/api/integration/zwift/push-workout/${this.training.id}`, {})
      .subscribe({
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
      const zi = zones.findIndex((z) => z.label.toUpperCase() === label);
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

  formatBlockEstimates(block: WorkoutBlock) {
    return blockEstimateDisplay(
      block,
      () => this.getEstimatedBlockDuration(block),
      () => this.getEstimatedBlockDistance(block),
    );
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

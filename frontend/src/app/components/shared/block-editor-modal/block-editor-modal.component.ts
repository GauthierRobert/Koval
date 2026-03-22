import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {WorkoutBlock} from '../../../models/training.model';

@Component({
  selector: 'app-block-editor-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './block-editor-modal.component.html',
  styleUrl: './block-editor-modal.component.css',
})
export class BlockEditorModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() block: WorkoutBlock | null = null;
  @Input() sportType = 'CYCLING';
  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<WorkoutBlock>();

  type: WorkoutBlock['type'] = 'STEADY';
  label = '';
  durationMinutes = 5;
  durationSeconds = 0;
  distanceMeters: number | null = null;
  intensityTarget: number | null = 75;
  intensityStart: number | null = null;
  intensityEnd: number | null = null;
  cadenceTarget: number | null = null;
  zoneTarget = '';

  readonly BLOCK_TYPES: WorkoutBlock['type'][] = [
    'WARMUP', 'STEADY', 'INTERVAL', 'RAMP', 'COOLDOWN', 'FREE', 'PAUSE',
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      if (this.block) {
        this.type = this.block.type;
        this.label = this.block.label || '';
        const totalSec = this.block.durationSeconds || 0;
        this.durationMinutes = Math.floor(totalSec / 60);
        this.durationSeconds = totalSec % 60;
        this.distanceMeters = this.block.distanceMeters ?? null;
        this.intensityTarget = this.block.intensityTarget ?? null;
        this.intensityStart = this.block.intensityStart ?? null;
        this.intensityEnd = this.block.intensityEnd ?? null;
        this.cadenceTarget = this.block.cadenceTarget ?? null;
        this.zoneTarget = this.block.zoneTarget || '';
      } else {
        this.resetDefaults();
      }
    }
  }

  get isRamp(): boolean {
    return this.type === 'RAMP';
  }

  get hideIntensity(): boolean {
    return this.type === 'FREE' || this.type === 'PAUSE';
  }

  get isCreateMode(): boolean {
    return this.block === null;
  }

  close(): void {
    this.closed.emit();
  }

  save(): void {
    const totalSeconds = (this.durationMinutes || 0) * 60 + (this.durationSeconds || 0);

    const block: WorkoutBlock = {
      type: this.type,
      label: this.label || this.type,
      ...(totalSeconds > 0 ? { durationSeconds: totalSeconds } : {}),
      ...(this.distanceMeters ? { distanceMeters: this.distanceMeters } : {}),
      ...(!this.hideIntensity && !this.isRamp && this.intensityTarget
        ? { intensityTarget: this.intensityTarget }
        : {}),
      ...(this.isRamp && this.intensityStart ? { intensityStart: this.intensityStart } : {}),
      ...(this.isRamp && this.intensityEnd ? { intensityEnd: this.intensityEnd } : {}),
      ...(this.cadenceTarget ? { cadenceTarget: this.cadenceTarget } : {}),
      ...(this.zoneTarget ? { zoneTarget: this.zoneTarget } : {}),
    };

    this.saved.emit(block);
  }

  canSave(): boolean {
    const hasDuration = (this.durationMinutes || 0) * 60 + (this.durationSeconds || 0) > 0;
    const hasDistance = (this.distanceMeters || 0) > 0;
    return hasDuration || hasDistance;
  }

  private resetDefaults(): void {
    this.type = 'STEADY';
    this.label = '';
    this.durationMinutes = 5;
    this.durationSeconds = 0;
    this.distanceMeters = null;
    this.intensityTarget = 75;
    this.intensityStart = null;
    this.intensityEnd = null;
    this.cadenceTarget = null;
    this.zoneTarget = '';
  }
}

import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { StrokeType, SwimEquipment, WorkoutBlock } from '../../../../models/training.model';
import { getBlockTypeColor } from '../../../shared/block-helpers/block-helpers';

type BlockType = WorkoutBlock['type'];

const STROKE_TYPES: StrokeType[] = [
  'FREESTYLE',
  'BACKSTROKE',
  'BREASTSTROKE',
  'BUTTERFLY',
  'IM',
  'KICK',
  'PULL',
  'DRILL',
  'CHOICE',
];

const SWIM_EQUIPMENT_OPTIONS: SwimEquipment[] = [
  'PADDLES',
  'PULL_BUOY',
  'FINS',
  'SNORKEL',
  'BAND',
  'KICKBOARD',
];

@Component({
  selector: 'app-block-editor-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './block-editor-form.component.html',
  styleUrl: './block-editor-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BlockEditorFormComponent {
  @Input() editType: BlockType = 'STEADY';
  @Input() editLabel = '';
  @Input() editDurationMin = 5;
  @Input() editDurationSec = 0;
  @Input() editDistanceMeters: number | null = null;
  @Input() editIntensity: number | null = 75;
  @Input() editIntensityStart: number | null = null;
  @Input() editIntensityEnd: number | null = null;
  @Input() editCadence: number | null = null;
  @Input() editZone = '';
  @Input() editStrokeType: StrokeType | null = null;
  @Input() editEquipment: SwimEquipment[] = [];
  @Input() editSendOffSeconds: number | null = null;
  @Input() selectedBlockIndex = -1;
  @Input() BLOCK_TYPES: BlockType[] = [];
  @Input() sportType: string = 'CYCLING';

  @Output() editTypeChange = new EventEmitter<BlockType>();
  @Output() editLabelChange = new EventEmitter<string>();
  @Output() editDurationMinChange = new EventEmitter<number>();
  @Output() editDurationSecChange = new EventEmitter<number>();
  @Output() editDistanceMetersChange = new EventEmitter<number | null>();
  @Output() editIntensityChange = new EventEmitter<number | null>();
  @Output() editIntensityStartChange = new EventEmitter<number | null>();
  @Output() editIntensityEndChange = new EventEmitter<number | null>();
  @Output() editCadenceChange = new EventEmitter<number | null>();
  @Output() editZoneChange = new EventEmitter<string>();
  @Output() editStrokeTypeChange = new EventEmitter<StrokeType | null>();
  @Output() editEquipmentChange = new EventEmitter<SwimEquipment[]>();
  @Output() editSendOffSecondsChange = new EventEmitter<number | null>();

  @Output() add = new EventEmitter<void>();
  @Output() update = new EventEmitter<void>();
  @Output() deselect = new EventEmitter<void>();

  readonly STROKE_TYPES = STROKE_TYPES;
  readonly SWIM_EQUIPMENT_OPTIONS = SWIM_EQUIPMENT_OPTIONS;

  isRamp(): boolean {
    return this.editType === 'RAMP';
  }

  hideIntensity(): boolean {
    return this.editType === 'FREE' || this.editType === 'PAUSE';
  }

  isSwim(): boolean {
    return this.sportType === 'SWIMMING';
  }

  showSwimFields(): boolean {
    return this.isSwim() && this.editType !== 'PAUSE' && this.editType !== 'TRANSITION';
  }

  blockColor(block: { type: string }): string {
    return getBlockTypeColor(block.type, this.sportType);
  }

  onTypeSelect(t: BlockType): void {
    this.editType = t;
    this.editTypeChange.emit(t);
  }

  onStrokeChange(value: string): void {
    const next = (value as StrokeType) || null;
    this.editStrokeType = next;
    this.editStrokeTypeChange.emit(next);
  }

  isEquipmentSelected(item: SwimEquipment): boolean {
    return this.editEquipment.includes(item);
  }

  toggleEquipment(item: SwimEquipment): void {
    const next = this.isEquipmentSelected(item)
      ? this.editEquipment.filter((e) => e !== item)
      : [...this.editEquipment, item];
    this.editEquipment = next;
    this.editEquipmentChange.emit(next);
  }

  get sendOffMin(): number {
    return Math.floor((this.editSendOffSeconds ?? 0) / 60);
  }

  get sendOffSec(): number {
    return (this.editSendOffSeconds ?? 0) % 60;
  }

  onSendOffMinChange(value: number | null): void {
    const m = Math.max(0, value ?? 0);
    const s = this.sendOffSec;
    const total = m * 60 + s;
    this.editSendOffSeconds = total > 0 ? total : null;
    this.editSendOffSecondsChange.emit(this.editSendOffSeconds);
  }

  onSendOffSecChange(value: number | null): void {
    const s = Math.max(0, Math.min(59, value ?? 0));
    const m = this.sendOffMin;
    const total = m * 60 + s;
    this.editSendOffSeconds = total > 0 ? total : null;
    this.editSendOffSecondsChange.emit(this.editSendOffSeconds);
  }
}

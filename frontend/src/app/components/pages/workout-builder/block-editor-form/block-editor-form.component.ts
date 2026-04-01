import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { WorkoutBlock } from '../../../../models/training.model';

type BlockType = WorkoutBlock['type'];

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
  @Input() selectedBlockIndex = -1;
  @Input() BLOCK_TYPES: BlockType[] = [];

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

  @Output() add = new EventEmitter<void>();
  @Output() update = new EventEmitter<void>();
  @Output() deselect = new EventEmitter<void>();

  isRamp(): boolean {
    return this.editType === 'RAMP';
  }

  hideIntensity(): boolean {
    return this.editType === 'FREE' || this.editType === 'PAUSE';
  }

  blockColor(block: { type: string }): string {
    const colors: Record<string, string> = {
      WARMUP: '#f59e0b',
      STEADY: '#22c55e',
      INTERVAL: '#ef4444',
      RAMP: '#8b5cf6',
      COOLDOWN: '#3b82f6',
      FREE: '#6b7280',
      PAUSE: '#374151',
    };
    return colors[block.type] || '#6b7280';
  }

  onTypeSelect(t: BlockType): void {
    this.editType = t;
    this.editTypeChange.emit(t);
  }
}

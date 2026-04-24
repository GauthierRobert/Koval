import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { WorkoutBlock } from '../../../../models/training.model';
import { getBlockColor as sharedGetBlockColor } from '../../../shared/block-helpers/block-helpers';

@Component({
  selector: 'app-set-editor-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './set-editor-form.component.html',
  styleUrl: './set-editor-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SetEditorFormComponent {
  @Input() editSetReps = 3;
  @Input() editSetNoRest = false;
  @Input() editSetRestDuration = 120;
  @Input() editSetRestIntensity = 60;
  @Input() editSetPassiveRest = false;
  @Input() editLabel = '';
  @Input() setElements: WorkoutBlock[] = [];
  @Input() sportType: string = 'CYCLING';

  @Output() editSetRepsChange = new EventEmitter<number>();
  @Output() editSetNoRestChange = new EventEmitter<boolean>();
  @Output() editSetRestDurationChange = new EventEmitter<number>();
  @Output() editSetRestIntensityChange = new EventEmitter<number>();
  @Output() editSetPassiveRestChange = new EventEmitter<boolean>();
  @Output() editLabelChange = new EventEmitter<string>();

  @Output() update = new EventEmitter<void>();
  @Output() dissociate = new EventEmitter<void>();
  @Output() deselect = new EventEmitter<void>();

  blockColor(block: WorkoutBlock): string {
    return sharedGetBlockColor(block, this.sportType);
  }

  formatBlockDuration(block: WorkoutBlock): string {
    const sec = block.durationSeconds ?? 0;
    if (sec < 60) return `${sec}s`;
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}m${s}s` : `${m}m`;
  }
}

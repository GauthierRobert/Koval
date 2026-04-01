import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { WorkoutBlock } from '../../../../models/training.model';

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

  @Output() editSetRepsChange = new EventEmitter<number>();
  @Output() editSetNoRestChange = new EventEmitter<boolean>();
  @Output() editSetRestDurationChange = new EventEmitter<number>();
  @Output() editSetRestIntensityChange = new EventEmitter<number>();
  @Output() editSetPassiveRestChange = new EventEmitter<boolean>();
  @Output() editLabelChange = new EventEmitter<string>();

  @Output() update = new EventEmitter<void>();
  @Output() dissociate = new EventEmitter<void>();
  @Output() deselect = new EventEmitter<void>();

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

  formatBlockDuration(block: WorkoutBlock): string {
    const sec = block.durationSeconds ?? 0;
    if (sec < 60) return `${sec}s`;
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}m${s}s` : `${m}m`;
  }
}

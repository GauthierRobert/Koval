import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { WorkoutBlock } from '../../../../models/training.model';

@Component({
  selector: 'app-set-block-item',
  standalone: true,
  imports: [CommonModule, TranslateModule, CdkDrag, CdkDragHandle],
  templateUrl: './set-block-item.component.html',
  styleUrl: './set-block-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SetBlockItemComponent {
  @Input() block!: WorkoutBlock;
  @Input() index = 0;
  @Input() isSelected = false;
  @Input() selectedChildIndex = -1;
  @Input() isSelectedForSet = false;

  @Output() select = new EventEmitter<number>();
  @Output() selectChild = new EventEmitter<{ parentIndex: number; childIndex: number }>();
  @Output() selectForSet = new EventEmitter<number>();
  @Output() duplicate = new EventEmitter<number>();
  @Output() remove = new EventEmitter<number>();
  @Output() removeChild = new EventEmitter<{ parentIndex: number; childIndex: number }>();

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

  formatSeconds(sec: number): string {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}m${s}s` : `${m}m`;
  }
}

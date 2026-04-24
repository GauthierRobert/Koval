import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { WorkoutBlock } from '../../../../models/training.model';
import { getBlockColor as sharedGetBlockColor } from '../../../shared/block-helpers/block-helpers';

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
  @Input() sportType: string = 'CYCLING';

  @Output() select = new EventEmitter<number>();
  @Output() selectChild = new EventEmitter<{ parentIndex: number; childIndex: number }>();
  @Output() selectForSet = new EventEmitter<number>();
  @Output() duplicate = new EventEmitter<number>();
  @Output() remove = new EventEmitter<number>();
  @Output() removeChild = new EventEmitter<{ parentIndex: number; childIndex: number }>();

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

  formatSeconds(sec: number): string {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}m${s}s` : `${m}m`;
  }
}

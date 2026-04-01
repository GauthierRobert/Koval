import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { WorkoutBlock } from '../../../../models/training.model';

@Component({
  selector: 'app-block-list-item',
  standalone: true,
  imports: [CommonModule, TranslateModule, CdkDrag, CdkDragHandle],
  templateUrl: './block-list-item.component.html',
  styleUrl: './block-list-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BlockListItemComponent {
  @Input() block!: WorkoutBlock;
  @Input() index = 0;
  @Input() isSelected = false;
  @Input() isSelectedForSet = false;
  @Input() blockColor = '#6b7280';

  @Output() select = new EventEmitter<number>();
  @Output() selectForSet = new EventEmitter<number>();
  @Output() duplicate = new EventEmitter<number>();
  @Output() remove = new EventEmitter<number>();

  formatBlockDuration(block: WorkoutBlock): string {
    const sec = block.durationSeconds ?? 0;
    if (sec < 60) return `${sec}s`;
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}m${s}s` : `${m}m`;
  }
}

import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { WorkoutBlock } from '../../../../models/training.model';
import { formatBlockSize } from '../../../shared/block-helpers/block-helpers';

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

  @Output() blockSelect = new EventEmitter<number>();
  @Output() selectForSet = new EventEmitter<number>();
  @Output() duplicate = new EventEmitter<number>();
  @Output() remove = new EventEmitter<number>();

  formatBlockDuration(block: WorkoutBlock): string {
    return formatBlockSize(block);
  }
}

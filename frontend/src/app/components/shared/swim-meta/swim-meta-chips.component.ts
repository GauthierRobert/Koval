import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { WorkoutBlock } from '../../../models/training.model';
import { formatSendOff, hasSwimMeta, isSwim } from './swim-meta.utils';

/**
 * Renders swim-specific chips (stroke, send-off, equipment) for a WorkoutBlock.
 * Renders nothing when sport is not SWIMMING or the block has no swim metadata,
 * so callers can drop it in without an outer @if.
 *
 * Host element is the chip row — parents that need full-width grid placement
 * can target `app-swim-meta-chips { grid-column: 1 / -1; }`.
 */
@Component({
  selector: 'app-swim-meta-chips',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './swim-meta-chips.component.html',
  styleUrl: './swim-meta-chips.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.swim-meta-host]': 'show' },
})
export class SwimMetaChipsComponent {
  @Input() block: WorkoutBlock | null = null;
  @Input() sport: string | null | undefined = null;
  /** `compact` shrinks chip padding/font for use inside tooltips and dense lists. */
  @Input() size: 'normal' | 'compact' = 'normal';

  get show(): boolean {
    return isSwim(this.sport) && hasSwimMeta(this.block);
  }

  formatSendOff = formatSendOff;
}

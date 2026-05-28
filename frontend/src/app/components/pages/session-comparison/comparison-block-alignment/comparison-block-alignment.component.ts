import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  ComparisonAlignedBlock,
  ComparisonSessionEntry,
} from '../../../../services/session-comparison.service';
import { formatTimeText } from '../../../shared/format/format.utils';

@Component({
  selector: 'app-comparison-block-alignment',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './comparison-block-alignment.component.html',
  styleUrl: './comparison-block-alignment.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonBlockAlignmentComponent {
  @Input({ required: true }) sessions: ComparisonSessionEntry[] = [];
  @Input({ required: true }) alignedBlocks: ComparisonAlignedBlock[] = [];
  @Input() colors: string[] = [];

  formatDuration(s: number): string {
    return formatTimeText(s);
  }

  cellFor(block: ComparisonAlignedBlock, sessionId: string) {
    return block.perSession.find((c) => c.sessionId === sessionId) ?? null;
  }

  /** Power delta vs the reference session, or null if either side absent. */
  powerDelta(block: ComparisonAlignedBlock, index: number): number | null {
    if (index === 0) return null;
    const ref = block.perSession[0];
    const cell = block.perSession[index];
    if (!ref || !ref.present || !cell || !cell.present) return null;
    return Math.round(cell.actualPower - ref.actualPower);
  }
}

import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ComparisonSessionEntry } from '../../../../services/session-comparison.service';
import { formatTimeText } from '../../../shared/format/format.utils';

interface BlockTypeDist {
  type: string;
  seconds: number;
  percentage: number;
}

/**
 * Side-by-side intensity distribution per session, derived from block-type duration shares.
 * Real zone classification needs FIT records on the client; for the compact comparison
 * view we surface block-type shares which the backend already computed.
 */
@Component({
  selector: 'app-comparison-zone-sidebyside',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './comparison-zone-sidebyside.component.html',
  styleUrl: './comparison-zone-sidebyside.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonZoneSidebysideComponent {
  @Input({ required: true }) sessions: ComparisonSessionEntry[] = [];
  @Input() colors: string[] = [];

  private static readonly TYPE_COLORS: Record<string, string> = {
    WARMUP: '#94a3b8',
    COOLDOWN: '#64748b',
    FREE: '#a8a29e',
    STEADY: '#22c55e',
    INTERVAL: '#ef4444',
    RAMP: '#f59e0b',
  };

  distribution(session: ComparisonSessionEntry): BlockTypeDist[] {
    const blocks = session.blockSummaries ?? [];
    const totals = new Map<string, number>();
    let total = 0;
    for (const b of blocks) {
      const t = b.type ?? 'OTHER';
      totals.set(t, (totals.get(t) ?? 0) + b.durationSeconds);
      total += b.durationSeconds;
    }
    if (total === 0) return [];
    return Array.from(totals.entries()).map(([type, seconds]) => ({
      type,
      seconds,
      percentage: Math.round((seconds / total) * 100),
    }));
  }

  colorFor(type: string): string {
    return ComparisonZoneSidebysideComponent.TYPE_COLORS[type] ?? '#888';
  }

  formatDuration(s: number): string {
    return formatTimeText(s);
  }
}

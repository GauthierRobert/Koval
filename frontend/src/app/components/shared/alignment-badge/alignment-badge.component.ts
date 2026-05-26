import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { alignmentZone } from '../../../models/alignment.model';

/**
 * Small colored pill showing a session's plan-alignment percentage. Green inside the 90–110%
 * on-target band, red outside it, neutral when unrated. The number itself carries the meaning,
 * so colour is never the sole signal.
 */
@Component({
  selector: 'app-alignment-badge',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="align-badge"
      [class.zone-green]="score != null && zone === 'green'"
      [class.zone-red]="score != null && zone === 'red'"
      [class.zone-none]="score == null"
      [attr.title]="title"
      [attr.aria-label]="title"
    >
      {{ score != null ? score + '%' : '—' }}
    </span>
  `,
  styles: [
    `
      .align-badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 2.75rem;
        padding: 2px var(--space-xs);
        border-radius: var(--radius-sm);
        font-size: var(--text-xs);
        font-weight: 700;
        line-height: 1.2;
        font-variant-numeric: tabular-nums;
        border: 1px solid transparent;
      }
      .zone-green {
        color: var(--success-color);
        background: color-mix(in srgb, var(--success-color) 14%, transparent);
        border-color: color-mix(in srgb, var(--success-color) 30%, transparent);
      }
      .zone-red {
        color: var(--danger-color);
        background: color-mix(in srgb, var(--danger-color) 14%, transparent);
        border-color: color-mix(in srgb, var(--danger-color) 30%, transparent);
      }
      .zone-none {
        color: var(--text-muted);
        background: var(--overlay-5);
        border-color: var(--glass-border);
      }
    `,
  ],
})
export class AlignmentBadgeComponent {
  @Input() score: number | null | undefined = null;

  get zone(): 'green' | 'red' {
    return alignmentZone(this.score ?? 100);
  }

  get title(): string {
    return this.score != null ? `Plan alignment: ${this.score}%` : 'Plan alignment: not rated';
  }
}

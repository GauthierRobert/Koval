import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

export interface ZoneChip {
  label: string;
  color: string;
}

/** Compact zone-filter strip reused above charts and inside block-breakdown. */
@Component({
  selector: 'app-zone-filter-chips',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    @if (chips.length > 1) {
      <div class="zone-filter-chips" [class.dense]="dense">
        @for (chip of chips; track chip.label) {
          <button
            type="button"
            class="zone-chip"
            [class.zone-chip--active]="isActive(chip.label)"
            [style.--chip-color]="chip.color"
            (click)="toggled.emit(chip.label)"
          >
            <span class="zone-dot" [style.background]="chip.color"></span>
            {{ chip.label }}
          </button>
        }
        @if (active.size > 0) {
          <button type="button" class="zone-chip zone-chip--clear" (click)="cleared.emit()">
            {{ 'SESSION_ANALYSIS.ZONE_FILTER_CLEAR' | translate }}
          </button>
        }
      </div>
    }
  `,
  styles: [
    `
      .zone-filter-chips {
        display: flex;
        gap: 6px;
        flex-wrap: wrap;
        align-items: center;
      }
      .zone-chip {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        background: var(--overlay-5);
        border: none;
        color: var(--text-muted);
        padding: 8px 12px;
        border-radius: 6px;
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.05em;
        cursor: pointer;
        transition: all 0.15s;
        opacity: 0.55;
      }
      .zone-chip--active {
        background: var(--overlay-10, rgba(0, 0, 0, 0.1));
        color: var(--text-color);
        opacity: 1;
      }
      .zone-chip--clear {
        opacity: 0.85;
        font-style: italic;
      }
      .zone-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
      }
      .dense .zone-chip {
        padding: 6px 10px;
        font-size: 10px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ZoneFilterChipsComponent {
  @Input() chips: ZoneChip[] = [];
  @Input() active: Set<string> = new Set();
  @Input() dense = false;

  @Output() toggled = new EventEmitter<string>();
  @Output() cleared = new EventEmitter<void>();

  isActive(label: string): boolean {
    return this.active.size === 0 || this.active.has(label);
  }
}

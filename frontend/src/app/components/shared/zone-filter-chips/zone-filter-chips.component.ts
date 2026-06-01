import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ZoneChip {
  label: string;
  color: string;
}

/** Compact zone-filter strip reused above charts and inside block-breakdown. */
@Component({
  selector: 'app-zone-filter-chips',
  standalone: true,
  imports: [CommonModule],
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
            {{ chip.label }}
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
        height: 32px;
        box-sizing: border-box;
        background: var(--overlay-5);
        border: none;
        /* Zone color persists; opacity signals on/off so color isn't the only cue. */
        color: var(--chip-color, var(--text-muted));
        padding: 0 10px;
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
        opacity: 1;
      }
      .dense .zone-chip {
        height: 28px;
        padding: 0 10px;
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

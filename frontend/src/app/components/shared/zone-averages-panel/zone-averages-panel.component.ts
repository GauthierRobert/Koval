import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { formatTimeHMS } from '../format/format.utils';

export interface ZoneAverages {
  zoneLabels: string[];
  durationSec: number;
  avgPower: number;
  avgHR: number;
  avgCadence: number;
  avgSpeedKmh: number;
  distanceMeters: number;
}

/** Compact panel showing averages computed only over samples in the active zone filter. */
@Component({
  selector: 'app-zone-averages-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    @if (averages && averages.durationSec > 0) {
      <div class="zone-averages glass" role="status" aria-live="polite">
        <div class="za-title">
          <span class="za-label">{{ 'SESSION_ANALYSIS.ZONE_AVG_TITLE' | translate }}</span>
          <span class="za-zones">{{ averages.zoneLabels.join(' · ') }}</span>
        </div>
        <div class="za-grid">
          <div class="za-cell">
            <span class="za-cell-lbl">{{ 'SESSION_ANALYSIS.ZONE_AVG_TIME' | translate }}</span>
            <span class="za-cell-val">{{ formatTime(averages.durationSec) }}</span>
          </div>
          @if (sportType === 'CYCLING' && averages.avgPower > 0) {
            <div class="za-cell za-accent">
              <span class="za-cell-lbl">{{ 'SESSION_ANALYSIS.ZONE_AVG_POWER' | translate }}</span>
              <span class="za-cell-val">{{ averages.avgPower | number: '1.0-0' }} W</span>
            </div>
          }
          @if (averages.avgSpeedKmh > 0) {
            <div class="za-cell">
              <span class="za-cell-lbl">{{ 'SESSION_ANALYSIS.ZONE_AVG_SPEED' | translate }}</span>
              <span class="za-cell-val">{{ averages.avgSpeedKmh | number: '1.1-1' }} km/h</span>
            </div>
          }
          @if (averages.avgHR > 0) {
            <div class="za-cell za-hr">
              <span class="za-cell-lbl">{{ 'SESSION_ANALYSIS.ZONE_AVG_HR' | translate }}</span>
              <span class="za-cell-val">{{ averages.avgHR | number: '1.0-0' }} bpm</span>
            </div>
          }
          @if (averages.avgCadence > 0) {
            <div class="za-cell za-cad">
              <span class="za-cell-lbl">{{ 'SESSION_ANALYSIS.ZONE_AVG_CAD' | translate }}</span>
              <span class="za-cell-val">{{ averages.avgCadence | number: '1.0-0' }}</span>
            </div>
          }
          @if (averages.distanceMeters > 0) {
            <div class="za-cell">
              <span class="za-cell-lbl">{{ 'SESSION_ANALYSIS.ZONE_AVG_DIST' | translate }}</span>
              <span class="za-cell-val">{{ formatDistance(averages.distanceMeters) }}</span>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [
    `
      .zone-averages {
        padding: 12px 16px;
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .za-title {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 10px;
        font-size: 11px;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--text-muted);
      }
      .za-zones {
        color: var(--accent-color, #ff9d00);
        letter-spacing: 0.02em;
        text-transform: none;
        font-weight: 600;
      }
      .za-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
        gap: 10px 16px;
      }
      .za-cell {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .za-cell-lbl {
        font-size: 10px;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--text-muted);
      }
      .za-cell-val {
        font-size: 16px;
        font-weight: 700;
        color: var(--text-color);
      }
      .za-accent .za-cell-val {
        color: var(--accent-color, #ff9d00);
      }
      .za-hr .za-cell-val {
        color: #e74c3c;
      }
      .za-cad .za-cell-val {
        color: #3b82f6;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ZoneAveragesPanelComponent {
  @Input() averages: ZoneAverages | null = null;
  @Input() sportType = 'CYCLING';

  formatTime(s: number): string {
    return formatTimeHMS(Math.max(0, Math.round(s)));
  }

  formatDistance(m: number): string {
    if (m >= 1000) return `${(m / 1000).toFixed(m >= 10000 ? 1 : 2)} km`;
    return `${Math.round(m)} m`;
  }
}

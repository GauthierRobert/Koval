import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { formatPaceWithUnit, formatTimeHMS } from '../../../shared/format/format.utils';
import { SelectionStats } from '../fit-timeseries-chart/fit-timeseries-chart.utils';

/** Stats for the chart's drag-to-select range, rendered as its own panel below the chart. */
@Component({
  selector: 'app-selection-stats-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    @if (stats && stats.durationSec > 0) {
      @let s = stats;
      <div class="selection-stats glass" role="status" aria-live="polite">
        <div class="ss-title">
          <span class="ss-label">{{ 'SESSION_ANALYSIS.SELECTION_TITLE' | translate }}</span>
          <span class="ss-range">{{ formatTime(s.startSec) }} → {{ formatTime(s.endSec) }}</span>
        </div>
        <div class="ss-grid">
          <div class="ss-cell">
            <span class="ss-cell-lbl">{{ 'SESSION_ANALYSIS.SELECTION_DUR' | translate }}</span>
            <span class="ss-cell-val">{{ formatTime(s.durationSec) }}</span>
          </div>
          @if (s.distanceMeters > 0) {
            <div class="ss-cell">
              <span class="ss-cell-lbl">{{ 'SESSION_ANALYSIS.SELECTION_DIST' | translate }}</span>
              <span class="ss-cell-val">{{ formatDistance(s.distanceMeters) }}</span>
            </div>
          }
          @if (sportType === 'CYCLING' && s.avgPower > 0) {
            <div class="ss-cell ss-accent">
              <span class="ss-cell-lbl">{{
                'SESSION_ANALYSIS.SELECTION_AVG_POWER' | translate
              }}</span>
              <span class="ss-cell-val">{{ s.avgPower | number: '1.0-0' }} W</span>
            </div>
            @if (s.normalizedPower > 0) {
              <div class="ss-cell">
                <span class="ss-cell-lbl">{{ 'SESSION_ANALYSIS.SELECTION_NP' | translate }}</span>
                <span class="ss-cell-val">{{ s.normalizedPower | number: '1.0-0' }} W</span>
              </div>
            }
            <div class="ss-cell">
              <span class="ss-cell-lbl">{{
                'SESSION_ANALYSIS.SELECTION_MAX_POWER' | translate
              }}</span>
              <span class="ss-cell-val">{{ s.maxPower | number: '1.0-0' }} W</span>
            </div>
          }
          @if (sportType !== 'CYCLING' && s.avgSpeedKmh > 0) {
            <div class="ss-cell ss-accent">
              <span class="ss-cell-lbl">{{
                'SESSION_ANALYSIS.SELECTION_AVG_PACE' | translate
              }}</span>
              <span class="ss-cell-val">{{ formatPace(s.avgSpeedKmh) }}</span>
            </div>
          }
          @if (s.avgHR > 0) {
            <div class="ss-cell ss-hr">
              <span class="ss-cell-lbl">{{ 'SESSION_ANALYSIS.SELECTION_AVG_HR' | translate }}</span>
              <span class="ss-cell-val">{{ s.avgHR | number: '1.0-0' }} bpm</span>
            </div>
            <div class="ss-cell ss-hr">
              <span class="ss-cell-lbl">{{ 'SESSION_ANALYSIS.SELECTION_MAX_HR' | translate }}</span>
              <span class="ss-cell-val">{{ s.maxHR | number: '1.0-0' }} bpm</span>
            </div>
          }
          @if (s.avgCadence > 0) {
            <div class="ss-cell ss-cad">
              <span class="ss-cell-lbl">{{
                'SESSION_ANALYSIS.SELECTION_AVG_CAD' | translate
              }}</span>
              <span class="ss-cell-val">{{ cadenceDisplay(s.avgCadence) | number: '1.0-0' }}</span>
            </div>
          }
          @if (s.elevationGain > 0) {
            <div class="ss-cell ss-elev">
              <span class="ss-cell-lbl">{{
                'SESSION_ANALYSIS.SELECTION_ELEV_GAIN' | translate
              }}</span>
              <span class="ss-cell-val">{{ s.elevationGain | number: '1.0-0' }} m</span>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [
    `
      .selection-stats {
        padding: 12px 16px;
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .ss-title {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 10px;
        font-size: 11px;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--text-muted);
      }
      .ss-range {
        color: var(--accent-color, #ff9d00);
        letter-spacing: 0.02em;
        text-transform: none;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
      .ss-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
        gap: 10px 16px;
      }
      .ss-cell {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .ss-cell-lbl {
        font-size: 10px;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--text-muted);
      }
      .ss-cell-val {
        font-size: 16px;
        font-weight: 700;
        color: var(--text-color);
        font-variant-numeric: tabular-nums;
      }
      .ss-accent .ss-cell-val {
        color: var(--accent-color, #ff9d00);
      }
      .ss-hr .ss-cell-val {
        color: #e74c3c;
      }
      .ss-cad .ss-cell-val {
        color: #3b82f6;
      }
      .ss-elev .ss-cell-val {
        color: #4caf50;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectionStatsPanelComponent {
  @Input() stats: SelectionStats | null = null;
  @Input() sportType = 'CYCLING';

  formatTime(s: number): string {
    return formatTimeHMS(Math.max(0, Math.round(s)));
  }

  formatDistance(m: number): string {
    if (m >= 1000) return `${(m / 1000).toFixed(m >= 10000 ? 1 : 2)} km`;
    return `${Math.round(m)} m`;
  }

  formatPace(kmh: number): string {
    if (kmh <= 0.5) return '—';
    if (this.sportType === 'SWIMMING') {
      return formatPaceWithUnit(360 / kmh, 'SWIMMING');
    }
    return formatPaceWithUnit(3600 / kmh, this.sportType);
  }

  cadenceDisplay(c: number): number {
    return this.sportType === 'RUNNING' ? c * 2 : c;
  }
}

import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ComparisonSessionEntry } from '../../../../services/session-comparison.service';
import { ColumnMetrics } from '../comparison-column/comparison-column.component';
import { formatTimeText } from '../../../shared/format/format.utils';

interface MetricRow {
  key: string;
  labelKey: string;
  format: 'int' | 'duration' | 'two-decimals' | 'one-decimal';
  unit: string;
  values: (number | null)[];
  max: number;
  leaderIndex: number;
}

@Component({
  selector: 'app-comparison-metrics-table',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './comparison-metrics-table.component.html',
  styleUrl: './comparison-metrics-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonMetricsTableComponent {
  private _sessions = signal<ComparisonSessionEntry[]>([]);
  @Input({ required: true }) set sessions(value: ComparisonSessionEntry[]) {
    this._sessions.set(value ?? []);
  }
  get sessions(): ComparisonSessionEntry[] {
    return this._sessions();
  }

  private _metrics = signal<(ColumnMetrics | null)[]>([]);
  /** Per-session scoped metrics (selection / zone-filter aware) emitted by each
   * comparison-column. Index matches session index. When an entry is null, the
   * row falls back to the session-entry totals. */
  @Input() set metricsOverrides(value: (ColumnMetrics | null)[]) {
    this._metrics.set(value ?? []);
  }

  @Input() colors: string[] = [];

  scopeLabel = computed<string>(() => {
    const m = this._metrics();
    const scoped = m.find((x) => x && x.scope !== 'full');
    return scoped ? scoped.scope : 'full';
  });

  rows = computed<MetricRow[]>(() => {
    const s = this._sessions();
    const m = this._metrics();
    if (s.length === 0) return [];

    const valueOf = (key: string, i: number): number | null => {
      const override = m[i];
      const sess = s[i];
      switch (key) {
        case 'duration':
          return override?.durationSec ?? sess.totalDurationSeconds;
        case 'distance':
          return override?.distanceMeters ?? sess.totalDistance;
        case 'avg-power':
          return override ? override.avgPower : sess.avgPower;
        case 'np':
          return override ? override.normalizedPower : sess.normalizedPower;
        case 'if':
          return override ? override.intensityFactor : sess.intensityFactor;
        case 'tss':
          return override ? override.tss : sess.tss;
        case 'avg-hr':
          return override ? override.avgHR : sess.avgHR;
        case 'avg-cad':
          return override ? override.avgCadence : sess.avgCadence;
        case 'avg-speed':
          if (override) return override.avgSpeedKmh;
          return sess.avgSpeed != null ? sess.avgSpeed * 3.6 : null;
        default:
          return null;
      }
    };

    const defs: Omit<MetricRow, 'max' | 'leaderIndex' | 'values'>[] = [
      {
        key: 'duration',
        labelKey: 'SESSION_COMPARE.METRIC_DURATION',
        format: 'duration',
        unit: '',
      },
      {
        key: 'distance',
        labelKey: 'SESSION_COMPARE.METRIC_DISTANCE',
        format: 'one-decimal',
        unit: 'km',
      },
      { key: 'avg-power', labelKey: 'SESSION_COMPARE.METRIC_AVG_POWER', format: 'int', unit: 'W' },
      { key: 'np', labelKey: 'SESSION_COMPARE.METRIC_NORM_POWER', format: 'int', unit: 'W' },
      { key: 'if', labelKey: 'SESSION_COMPARE.METRIC_INTENSITY', format: 'two-decimals', unit: '' },
      { key: 'tss', labelKey: 'SESSION_COMPARE.METRIC_TRAINING_LOAD', format: 'int', unit: '' },
      { key: 'avg-hr', labelKey: 'SESSION_COMPARE.METRIC_AVG_HR', format: 'int', unit: 'bpm' },
      { key: 'avg-cad', labelKey: 'SESSION_COMPARE.METRIC_AVG_CAD', format: 'int', unit: 'rpm' },
      {
        key: 'avg-speed',
        labelKey: 'SESSION_COMPARE.METRIC_AVG_SPEED',
        format: 'one-decimal',
        unit: 'km/h',
      },
    ];

    return defs
      .map<MetricRow>((def) => {
        const values = s.map((_, i) => {
          const v = valueOf(def.key, i);
          return v == null || v <= 0 ? null : v;
        });
        let max = 0;
        let leaderIndex = -1;
        values.forEach((v, i) => {
          if (v != null && v > max) {
            max = v;
            leaderIndex = i;
          }
        });
        return { ...def, values, max, leaderIndex };
      })
      .filter((row) => row.values.some((v) => v != null));
  });

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
  }

  formatCell(row: MetricRow, index: number): string {
    const v = row.values[index];
    if (v == null) return '—';
    switch (row.format) {
      case 'duration':
        return formatTimeText(v);
      case 'two-decimals':
        return v.toFixed(2);
      case 'one-decimal':
        return (row.key === 'distance' ? v / 1000 : v).toFixed(1);
      default:
        return Math.round(v).toString();
    }
  }

  barPct(row: MetricRow, index: number): number {
    const v = row.values[index];
    if (v == null || row.max <= 0) return 0;
    return (v / row.max) * 100;
  }

  isLeader(row: MetricRow, index: number): boolean {
    return row.leaderIndex === index;
  }

  /** Negative delta vs the row's highest value, formatted in the row's unit
   * style. Returns null for the leader, missing values, or single-session rows. */
  diffFromMax(row: MetricRow, index: number): string | null {
    if (row.leaderIndex < 0 || row.leaderIndex === index) return null;
    const v = row.values[index];
    if (v == null || row.max <= 0) return null;
    const delta = v - row.max;
    if (delta === 0) return null;
    const sign = delta < 0 ? '−' : '+';
    const abs = Math.abs(delta);
    switch (row.format) {
      case 'duration':
        return `${sign}${formatTimeText(abs)}`;
      case 'two-decimals':
        return `${sign}${abs.toFixed(2)}`;
      case 'one-decimal':
        return `${sign}${(row.key === 'distance' ? abs / 1000 : abs).toFixed(1)}`;
      default:
        return `${sign}${Math.round(abs)}`;
    }
  }
}

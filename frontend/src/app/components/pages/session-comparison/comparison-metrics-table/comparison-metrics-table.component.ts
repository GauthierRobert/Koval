import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  ComparisonMetricDelta,
  ComparisonSessionEntry,
} from '../../../../services/session-comparison.service';
import { formatTimeText } from '../../../shared/format/format.utils';

interface MetricRow {
  key: string;
  labelKey: string;
  format: 'int' | 'duration' | 'two-decimals' | 'distance';
  values: (number | null)[];
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

  @Input() biggestDeltas: ComparisonMetricDelta[] = [];
  @Input() colors: string[] = [];

  rows = computed<MetricRow[]>(() => {
    const s = this._sessions();
    if (s.length === 0) return [];
    const rows: MetricRow[] = [
      {
        key: 'duration',
        labelKey: 'SESSION_COMPARE.METRIC_DURATION',
        format: 'duration',
        values: s.map((x) => x.totalDurationSeconds),
      },
      {
        key: 'tss',
        labelKey: 'SESSION_COMPARE.METRIC_TSS',
        format: 'int',
        values: s.map((x) => x.tss),
      },
      {
        key: 'if',
        labelKey: 'SESSION_COMPARE.METRIC_IF',
        format: 'two-decimals',
        values: s.map((x) => x.intensityFactor),
      },
      {
        key: 'np',
        labelKey: 'SESSION_COMPARE.METRIC_NP',
        format: 'int',
        values: s.map((x) => x.normalizedPower ?? x.avgPower),
      },
      {
        key: 'avg-hr',
        labelKey: 'SESSION_COMPARE.METRIC_AVG_HR',
        format: 'int',
        values: s.map((x) => x.avgHR),
      },
      {
        key: 'avg-cad',
        labelKey: 'SESSION_COMPARE.METRIC_AVG_CAD',
        format: 'int',
        values: s.map((x) => x.avgCadence),
      },
      {
        key: 'distance',
        labelKey: 'SESSION_COMPARE.METRIC_DISTANCE',
        format: 'distance',
        values: s.map((x) => x.totalDistance),
      },
    ];
    return rows.filter((row) => row.values.some((v) => v != null));
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
      case 'distance':
        return (v / 1000).toFixed(1) + ' km';
      default:
        return Math.round(v).toString();
    }
  }

  formatDelta(row: MetricRow, index: number): string {
    if (index === 0) return '';
    const ref = row.values[0];
    const v = row.values[index];
    if (ref == null || v == null) return '';
    const delta = v - ref;
    if (row.format === 'duration') {
      const sign = delta >= 0 ? '+' : '−';
      return ` (${sign}${formatTimeText(Math.abs(delta))})`;
    }
    if (row.format === 'two-decimals') {
      return ` (${delta >= 0 ? '+' : ''}${delta.toFixed(2)})`;
    }
    if (row.format === 'distance') {
      return ` (${delta >= 0 ? '+' : ''}${(delta / 1000).toFixed(1)}km)`;
    }
    return ` (${delta >= 0 ? '+' : ''}${Math.round(delta)})`;
  }

  deltaPolarity(row: MetricRow, index: number): 'positive' | 'negative' | 'neutral' {
    if (index === 0) return 'neutral';
    const ref = row.values[0];
    const v = row.values[index];
    if (ref == null || v == null) return 'neutral';
    if (v === ref) return 'neutral';
    return v > ref ? 'positive' : 'negative';
  }
}

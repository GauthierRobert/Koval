import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FitRecord } from '../../../../services/metrics.service';

export interface DecouplingSegment {
  label: string;
  avgMetric: number;
  avgHR: number;
  efficiency: number;
}

export interface DecouplingResult {
  segments: DecouplingSegment[];
  decouplingPct: number;
  color: string;
  level: 'good' | 'moderate' | 'high';
  usePower: boolean;
}

const NINETY_MINUTES = 5400;
const MIN_RECORDS = 300; // 5 minutes minimum
const NP_ROLLING_WINDOW = 30;
const GREEN = 'var(--success-color)';
const AMBER = 'oklch(0.75 0.16 75)';
const RED = 'var(--danger-color)';

/**
 * Coggan Normalized Power: 30 s rolling mean → 4th-power mean → 4th root.
 * Mirrors the backend `NormalizedPowerCalculator` so the segment value
 * lines up with the session-level NP shown in the header.
 */
function normalizedPower(watts: number[]): number {
  if (!watts.length) return 0;
  const series = watts.length >= NP_ROLLING_WINDOW ? rollingMean(watts, NP_ROLLING_WINDOW) : watts;
  let sum4 = 0;
  for (const v of series) sum4 += v * v * v * v;
  return Math.pow(sum4 / series.length, 0.25);
}

function rollingMean(values: number[], window: number): number[] {
  const n = values.length;
  const out = new Array<number>(n - window + 1);
  let sum = 0;
  for (let i = 0; i < window; i++) sum += values[i];
  out[0] = sum / window;
  for (let i = window; i < n; i++) {
    sum += values[i] - values[i - window];
    out[i - window + 1] = sum / window;
  }
  return out;
}

@Component({
  selector: 'app-decoupling-gauge',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './decoupling-gauge.component.html',
  styleUrl: './decoupling-gauge.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecouplingGaugeComponent {
  @Input({ required: true }) records: FitRecord[] = [];
  @Input({ required: true }) sportType = '';

  get result(): DecouplingResult | null {
    return this.computeDecoupling();
  }

  formatMetric(kmh: number): string {
    if (!kmh || kmh <= 0.5) return '—';
    const secPerKm = 3600 / kmh;
    const m = Math.floor(secPerKm / 60);
    const s = Math.round(secPerKm % 60);
    return `${m}:${String(s).padStart(2, '0')} /km`;
  }

  /** SVG arc path for a segment of the semi-circle gauge. */
  arcPath(startPct: number, endPct: number, radius: number, cx: number, cy: number): string {
    // Map percentage (0-1) to angle (PI to 0, left-to-right semi-circle)
    const startAngle = Math.PI * (1 - startPct);
    const endAngle = Math.PI * (1 - endPct);
    const x1 = cx + radius * Math.cos(startAngle);
    const y1 = cy - radius * Math.sin(startAngle);
    const x2 = cx + radius * Math.cos(endAngle);
    const y2 = cy - radius * Math.sin(endAngle);
    const largeArc = Math.abs(endPct - startPct) > 0.5 ? 1 : 0;
    return `M ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2}`;
  }

  /** Position of the needle marker on the arc for a given decoupling %. */
  needlePos(
    decouplingPct: number,
    radius: number,
    cx: number,
    cy: number,
  ): { x: number; y: number } {
    // Scale: 0% maps to left (PI), 15%+ maps to right (0)
    const clamped = Math.max(0, Math.min(decouplingPct, 15));
    const pct = clamped / 15;
    const angle = Math.PI * (1 - pct);
    return {
      x: cx + radius * Math.cos(angle),
      y: cy - radius * Math.sin(angle),
    };
  }

  private computeDecoupling(): DecouplingResult | null {
    if (this.sportType === 'SWIMMING') return null;

    // Cycling decoupling is only meaningful with power data — speed is too noisy
    // (terrain, drafting, stops) to reflect aerobic drift. Hide for power-less rides.
    const hasPower = this.sportType === 'CYCLING' && this.records.some((r) => r.power > 0);
    if (this.sportType === 'CYCLING' && !hasPower) return null;
    const usePower = hasPower;

    // Drop only HR-less records. Coasting seconds (power = 0) stay in the
    // sample so the segment's reported NP / avg metric stays comparable to
    // the session-level value shown in the header.
    const filtered = this.records.filter((r) => {
      if (r.heartRate <= 0) return false;
      return usePower ? true : r.speed > 0;
    });

    if (filtered.length < MIN_RECORDS) return null;

    const totalDuration =
      filtered.length >= 2 ? filtered[filtered.length - 1].timestamp - filtered[0].timestamp : 0;
    const segmentCount = totalDuration > NINETY_MINUTES ? 3 : 2;
    const segmentSize = Math.floor(filtered.length / segmentCount);

    if (segmentSize < MIN_RECORDS / 2) return null;

    const segments: DecouplingSegment[] = [];
    const rawEfficiencies: number[] = [];
    const segmentLabels =
      segmentCount === 2 ? ['1st half', '2nd half'] : ['1st third', '2nd third', '3rd third'];

    for (let i = 0; i < segmentCount; i++) {
      const start = i * segmentSize;
      const end = i === segmentCount - 1 ? filtered.length : (i + 1) * segmentSize;
      const slice = filtered.slice(start, end);

      let hrSum = 0;
      for (const r of slice) hrSum += r.heartRate;
      const avgHR = hrSum / slice.length;
      // For cycling, use Normalized Power for the segment — this matches the
      // Coggan Pa:Hr decoupling convention and makes the segment metric
      // directly comparable to the session header's NP.
      const segmentMetric = usePower
        ? normalizedPower(slice.map((r) => r.power))
        : slice.reduce((a, r) => a + r.speed, 0) / slice.length;
      // Raw efficiency kept unrounded for decoupling % computation — for
      // running, speed (m/s) / HR is ~0.02, so rounding to 2 decimals
      // collapses segments to identical values and masks the drift.
      const rawEfficiency = avgHR > 0 ? segmentMetric / avgHR : 0;
      rawEfficiencies.push(rawEfficiency);

      // Display values: power as W, speed as km/h. Efficiency display
      // precision scales with the metric's magnitude.
      const displayMetric = usePower ? segmentMetric : segmentMetric * 3.6;
      const metricDecimals = usePower ? 0 : 1;
      const efficiencyDecimals = usePower ? 2 : 4;
      const pow = (n: number) => Math.pow(10, n);

      segments.push({
        label: segmentLabels[i],
        avgMetric: Math.round(displayMetric * pow(metricDecimals)) / pow(metricDecimals),
        avgHR: Math.round(avgHR),
        efficiency: Math.round(rawEfficiency * pow(efficiencyDecimals)) / pow(efficiencyDecimals),
      });
    }

    const first = rawEfficiencies[0];
    const last = rawEfficiencies[rawEfficiencies.length - 1];
    const decouplingPct = first > 0 ? ((first - last) / first) * 100 : 0;
    const rounded = Math.round(decouplingPct * 10) / 10;
    const abs = Math.abs(rounded);

    let color: string;
    let level: 'good' | 'moderate' | 'high';
    if (abs < 5) {
      color = GREEN;
      level = 'good';
    } else if (abs < 10) {
      color = AMBER;
      level = 'moderate';
    } else {
      color = RED;
      level = 'high';
    }

    return { segments, decouplingPct: rounded, color, level, usePower };
  }
}

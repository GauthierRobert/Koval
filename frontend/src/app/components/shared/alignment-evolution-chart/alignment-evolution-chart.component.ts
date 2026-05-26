import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AlignmentHistoryPoint, alignmentZone } from '../../../models/alignment.model';

interface PlottedPoint {
  cx: number;
  cy: number;
  score: number;
  zone: 'green' | 'red';
  label: string;
}

interface ChartVm {
  points: PlottedPoint[];
  linePath: string;
  bandY: number;
  bandHeight: number;
  baselineY: number;
  yTicks: { y: number; label: number }[];
  xLabels: { x: number; label: string; anchor: string }[];
}

/**
 * Line/scatter of plan-alignment percentage over time. The 90–110% on-target band is shaded green;
 * points outside it are red, inside green. Pure SVG so it scales responsively and stays accessible.
 */
@Component({
  selector: 'app-alignment-evolution-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './alignment-evolution-chart.component.html',
  styleUrl: './alignment-evolution-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlignmentEvolutionChartComponent {
  readonly W = 760;
  readonly H = 260;
  private readonly padL = 38;
  private readonly padR = 14;
  private readonly padT = 14;
  private readonly padB = 28;

  vm: ChartVm | null = null;

  @Input({ required: true }) set points(value: AlignmentHistoryPoint[]) {
    this.vm = value && value.length > 0 ? this.build(value) : null;
  }

  private build(data: AlignmentHistoryPoint[]): ChartVm {
    const plotW = this.W - this.padL - this.padR;
    const plotH = this.H - this.padT - this.padB;

    const scores = data.map((d) => d.effectiveScore);
    const yMin = Math.min(80, Math.min(...scores) - 5);
    const yMax = Math.max(120, Math.max(...scores) + 5);

    const x = (i: number) =>
      data.length <= 1 ? this.padL + plotW / 2 : this.padL + (i / (data.length - 1)) * plotW;
    const y = (score: number) =>
      this.padT + (1 - (score - yMin) / (yMax - yMin)) * plotH;

    const points: PlottedPoint[] = data.map((d, i) => ({
      cx: x(i),
      cy: y(d.effectiveScore),
      score: d.effectiveScore,
      zone: alignmentZone(d.effectiveScore),
      label: `${this.formatDate(d.date)} · ${d.title ?? ''} · ${d.effectiveScore}%`,
    }));

    const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.cx} ${p.cy}`).join(' ');

    const tickValues = [...new Set([Math.round(yMin), 90, 100, 110, Math.round(yMax)])]
      .filter((v) => v >= yMin && v <= yMax)
      .sort((a, b) => a - b);

    return {
      points,
      linePath,
      bandY: y(110),
      bandHeight: y(90) - y(110),
      baselineY: y(100),
      yTicks: tickValues.map((v) => ({ y: y(v), label: v })),
      xLabels: this.xLabels(data, x),
    };
  }

  private xLabels(data: AlignmentHistoryPoint[], x: (i: number) => number) {
    if (data.length === 0) return [];
    const labels = [{ x: x(0), label: this.formatDate(data[0].date), anchor: 'start' }];
    if (data.length > 1) {
      labels.push({
        x: x(data.length - 1),
        label: this.formatDate(data[data.length - 1].date),
        anchor: 'end',
      });
    }
    return labels;
  }

  private formatDate(iso: string): string {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }
}

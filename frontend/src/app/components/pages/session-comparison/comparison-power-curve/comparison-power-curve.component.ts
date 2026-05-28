import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ComparisonSessionEntry } from '../../../../services/session-comparison.service';

interface CurveSeries {
  label: string;
  color: string;
  points: { duration: number; power: number }[];
}

/**
 * Mean-maximal power curves overlaid for 2-4 sessions. Lightweight canvas renderer
 * tuned for the comparison view; the standalone {@code PowerCurveChartComponent}
 * only renders a single series, so we draw multiple here directly.
 */
@Component({
  selector: 'app-comparison-power-curve',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './comparison-power-curve.component.html',
  styleUrl: './comparison-power-curve.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonPowerCurveComponent implements AfterViewInit, OnChanges {
  @Input({ required: true }) sessions: ComparisonSessionEntry[] = [];
  @Input() colors: string[] = [];

  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  ngAfterViewInit(): void {
    this.render();
  }

  ngOnChanges(_: SimpleChanges): void {
    if (this.canvasRef) this.render();
  }

  series(): CurveSeries[] {
    return this.sessions.map((s, i) => {
      const points = Object.entries(s.powerCurve ?? {})
        .map(([dur, watts]) => ({ duration: Number(dur), power: Number(watts) }))
        .filter((p) => p.duration >= 5 && p.power > 0)
        .sort((a, b) => a.duration - b.duration);
      return { label: s.title, color: this.colors[i] ?? '#888', points };
    });
  }

  private render(): void {
    const canvas = this.canvasRef.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const ratio = window.devicePixelRatio || 1;
    const cssW = canvas.clientWidth;
    const cssH = canvas.clientHeight || 240;
    canvas.width = cssW * ratio;
    canvas.height = cssH * ratio;
    ctx.scale(ratio, ratio);
    ctx.clearRect(0, 0, cssW, cssH);

    const all = this.series().filter((s) => s.points.length > 0);
    if (all.length === 0) {
      ctx.fillStyle = '#888';
      ctx.font = '12px sans-serif';
      ctx.fillText('No power curve data', 16, cssH / 2);
      return;
    }

    const pad = { l: 44, r: 12, t: 12, b: 28 };
    const w = cssW - pad.l - pad.r;
    const h = cssH - pad.t - pad.b;

    const allPoints = all.flatMap((s) => s.points);
    const minDur = 5;
    const maxDur = Math.max(...allPoints.map((p) => p.duration));
    const maxPwr = Math.max(...allPoints.map((p) => p.power));

    // Log x-axis
    const logMin = Math.log10(minDur);
    const logMax = Math.log10(maxDur);
    const x = (dur: number) => pad.l + ((Math.log10(dur) - logMin) / (logMax - logMin)) * w;
    const y = (pwr: number) => pad.t + h - (pwr / maxPwr) * h;

    // Grid
    ctx.strokeStyle = 'rgba(255,255,255,0.08)';
    ctx.lineWidth = 1;
    for (const tick of [5, 30, 60, 300, 1200, 3600]) {
      if (tick > maxDur) break;
      const xp = x(tick);
      ctx.beginPath();
      ctx.moveTo(xp, pad.t);
      ctx.lineTo(xp, pad.t + h);
      ctx.stroke();
      ctx.fillStyle = '#888';
      ctx.font = '10px sans-serif';
      ctx.fillText(tick >= 60 ? `${tick / 60}m` : `${tick}s`, xp - 8, pad.t + h + 14);
    }
    for (let p = 0; p <= maxPwr; p += Math.ceil(maxPwr / 5 / 50) * 50) {
      const yp = y(p);
      ctx.beginPath();
      ctx.moveTo(pad.l, yp);
      ctx.lineTo(pad.l + w, yp);
      ctx.stroke();
      ctx.fillStyle = '#888';
      ctx.font = '10px sans-serif';
      ctx.fillText(`${p}W`, 6, yp + 3);
    }

    // Lines
    for (const series of all) {
      ctx.strokeStyle = series.color;
      ctx.lineWidth = 2;
      ctx.beginPath();
      series.points.forEach((p, idx) => {
        const px = x(p.duration);
        const py = y(p.power);
        if (idx === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      });
      ctx.stroke();
    }
  }
}

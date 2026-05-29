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
import {
  CURVE_MARGINS_Y,
  CurvePoint,
  cssToRgb,
  curveMargins,
  curveXRatio,
  niceAxis,
  resolveCurveTheme,
  toCurvePoints,
} from '../../session-analysis/power-curve-chart/power-curve-chart.utils';
import { DURATION_LABELS } from '../../../../services/analytics.service';

interface CurveSeries {
  label: string;
  color: string;
  rgb: [number, number, number];
  points: CurvePoint[];
}

/**
 * Mean-maximal power curves overlaid for 2-4 sessions. Shares drawing helpers with the
 * single-session {@code PowerCurveChartComponent} so the axes, gridlines, fill gradient,
 * sample dots, and labels all read consistently — only the stroke/fill color varies per
 * session.
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

  private resizeObserver: ResizeObserver | null = null;

  ngAfterViewInit(): void {
    this.render();
    if (!this.resizeObserver) {
      this.resizeObserver = new ResizeObserver(() => this.render());
      this.resizeObserver.observe(this.canvasRef.nativeElement);
    }
  }

  ngOnChanges(_: SimpleChanges): void {
    if (this.canvasRef) this.render();
  }

  series(): CurveSeries[] {
    return this.sessions.map((s, i) => {
      const points = toCurvePoints(s.powerCurve ?? {}).filter((p) => p.duration >= 5);
      const colorCss = this.colors[i] ?? '#888';
      const rgb = cssToRgb(colorCss) ?? [136, 136, 136];
      return { label: s.title, color: `rgb(${rgb.join(',')})`, rgb, points };
    });
  }

  private render(): void {
    const canvas = this.canvasRef.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = Math.max(1, window.devicePixelRatio || 1);
    const cssW = canvas.clientWidth;
    const cssH = canvas.clientHeight;
    if (cssW <= 0 || cssH <= 0) return;
    const targetW = Math.round(cssW * dpr);
    const targetH = Math.round(cssH * dpr);
    if (canvas.width !== targetW) canvas.width = targetW;
    if (canvas.height !== targetH) canvas.height = targetH;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssW, cssH);

    const all = this.series().filter((s) => s.points.length > 0);
    const theme = resolveCurveTheme();

    if (all.length === 0) {
      ctx.fillStyle = theme.textColor;
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('No power curve data', cssW / 2, cssH / 2);
      return;
    }

    const { mL, mR } = curveMargins(cssW);
    const { mT, mB } = CURVE_MARGINS_Y;
    const cW = Math.max(1, cssW - mL - mR);
    const cH = Math.max(1, cssH - mT - mB);

    // Shared X domain across all series so durations line up visually.
    const allPoints = all.flatMap((s) => s.points);
    const xDomain = [...allPoints].sort((a, b) => a.duration - b.duration);

    // Shared Y axis: take the max across all series.
    const maxPower = Math.max(...allPoints.map((p) => p.power));
    const { yMax, step } = niceAxis(maxPower);

    // Y gridlines + labels
    ctx.font = '10px monospace';
    ctx.fillStyle = theme.textColor;
    ctx.strokeStyle = theme.gridColor;
    ctx.lineWidth = 1;
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    const ySteps = Math.round(yMax / step);
    for (let i = 0; i <= ySteps; i++) {
      const v = step * i;
      const y = mT + cH * (1 - i / ySteps);
      ctx.beginPath();
      ctx.moveTo(mL, y);
      ctx.lineTo(mL + cW, y);
      ctx.stroke();
      ctx.fillText(`${Math.round(v)}W`, mL - 6, y);
    }

    // Axis spine
    ctx.strokeStyle = 'rgba(255,255,255,0.22)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(mL, mT);
    ctx.lineTo(mL, mT + cH);
    ctx.lineTo(mL + cW, mT + cH);
    ctx.stroke();

    // X labels — use canonical duration labels (5s, 30s, 1m, ...) at union of sample points.
    ctx.fillStyle = theme.textColor;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    const labelPad = 6;
    let lastLabelRight = -Infinity;
    const seenDurations = new Set<number>();
    for (const p of xDomain) {
      if (seenDurations.has(p.duration)) continue;
      seenDurations.add(p.duration);
      const label = DURATION_LABELS[p.duration] ?? `${p.duration}s`;
      const x = mL + curveXRatio(p.duration, xDomain) * cW;
      const labelW = ctx.measureText(label).width;
      const labelLeft = x - labelW / 2;
      if (labelLeft < lastLabelRight + labelPad) continue;
      ctx.fillText(label, x, mT + cH + 4);
      lastLabelRight = x + labelW / 2;
    }

    // Per-series: filled area + line + dots
    for (const series of all) {
      const pathPoints = series.points.map((p) => ({
        x: mL + curveXRatio(p.duration, xDomain) * cW,
        y: mT + cH * (1 - p.power / yMax),
      }));

      ctx.beginPath();
      ctx.moveTo(pathPoints[0].x, mT + cH);
      for (const pt of pathPoints) ctx.lineTo(pt.x, pt.y);
      ctx.lineTo(pathPoints[pathPoints.length - 1].x, mT + cH);
      ctx.closePath();
      const grad = ctx.createLinearGradient(0, mT, 0, mT + cH);
      grad.addColorStop(0, `rgba(${series.rgb.join(',')},0.22)`);
      grad.addColorStop(1, `rgba(${series.rgb.join(',')},0.02)`);
      ctx.fillStyle = grad;
      ctx.fill();

      ctx.beginPath();
      ctx.moveTo(pathPoints[0].x, pathPoints[0].y);
      for (let i = 1; i < pathPoints.length; i++) {
        ctx.lineTo(pathPoints[i].x, pathPoints[i].y);
      }
      ctx.strokeStyle = series.color;
      ctx.lineWidth = 2;
      ctx.lineJoin = 'round';
      ctx.lineCap = 'round';
      ctx.stroke();

      for (const pt of pathPoints) {
        ctx.beginPath();
        ctx.arc(pt.x, pt.y, 2.5, 0, Math.PI * 2);
        ctx.fillStyle = series.color;
        ctx.fill();
      }
    }
  }
}

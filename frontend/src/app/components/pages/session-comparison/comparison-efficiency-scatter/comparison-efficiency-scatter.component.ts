import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ComparisonSessionEntry } from '../../../../services/session-comparison.service';
import { cssToRgb } from '../../session-analysis/power-curve-chart/power-curve-chart.utils';

interface ScatterPoint {
  label: string;
  x: number;
  y: number;
  color: string;
  rgb: [number, number, number];
  efficiency: number;
}

@Component({
  selector: 'app-comparison-efficiency-scatter',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './comparison-efficiency-scatter.component.html',
  styleUrl: './comparison-efficiency-scatter.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonEfficiencyScatterComponent implements AfterViewInit, OnChanges {
  @Input({ required: true }) sessions: ComparisonSessionEntry[] = [];
  @Input() colors: string[] = [];
  @Input() sportType = 'CYCLING';

  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  private translate = inject(TranslateService);
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

  private buildPoints(): ScatterPoint[] {
    const useSpeed = this.sportType !== 'CYCLING';
    return this.sessions
      .map((s, i) => {
        const x = s.avgHR ?? null;
        const y = useSpeed ? (s.avgSpeed ?? null) : (s.normalizedPower ?? s.avgPower ?? null);
        if (x == null || y == null || x <= 0 || y <= 0) return null;
        const colorCss = this.colors[i] ?? '#888';
        const rgb = cssToRgb(colorCss) ?? [136, 136, 136];
        return {
          label: s.title,
          x,
          y,
          color: `rgb(${rgb.join(',')})`,
          rgb,
          efficiency: y / x,
        } as ScatterPoint;
      })
      .filter((p): p is ScatterPoint => p != null);
  }

  private niceRange(min: number, max: number, padRatio = 0.12): [number, number, number] {
    const span = Math.max(1, max - min);
    const pad = span * padRatio;
    const lo = Math.max(0, min - pad);
    const hi = max + pad;
    const stepRaw = (hi - lo) / 4;
    const mag = Math.pow(10, Math.floor(Math.log10(stepRaw)));
    const norm = stepRaw / mag;
    const step = (norm < 1.5 ? 1 : norm < 3.5 ? 2 : norm < 7.5 ? 5 : 10) * mag;
    const niceLo = Math.floor(lo / step) * step;
    const niceHi = Math.ceil(hi / step) * step;
    return [niceLo, niceHi, step];
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

    const points = this.buildPoints();
    if (points.length < 2) {
      ctx.fillStyle = 'rgba(255,255,255,0.55)';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(
        this.translate.instant('SESSION_COMPARE.SCATTER_INSUFFICIENT'),
        cssW / 2,
        cssH / 2,
      );
      return;
    }

    const useSpeed = this.sportType !== 'CYCLING';
    const yUnit = useSpeed ? 'km/h' : 'W';
    const xUnit = 'bpm';

    const mL = 48;
    const mR = 16;
    const mT = 14;
    const mB = 36;
    const cW = Math.max(1, cssW - mL - mR);
    const cH = Math.max(1, cssH - mT - mB);

    const xs = points.map((p) => p.x);
    const ys = points.map((p) => p.y);
    const [xLo, xHi, xStep] = this.niceRange(Math.min(...xs), Math.max(...xs));
    const [yLo, yHi, yStep] = this.niceRange(Math.min(...ys), Math.max(...ys));

    const px = (v: number) => mL + ((v - xLo) / (xHi - xLo || 1)) * cW;
    const py = (v: number) => mT + cH - ((v - yLo) / (yHi - yLo || 1)) * cH;

    const grid = 'rgba(255,255,255,0.08)';
    const gridStrong = 'rgba(255,255,255,0.22)';
    const textColor = 'rgba(255,255,255,0.70)';

    ctx.strokeStyle = grid;
    ctx.lineWidth = 1;
    ctx.font = '10px monospace';
    ctx.fillStyle = textColor;
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    for (let v = yLo; v <= yHi + 0.0001; v += yStep) {
      const y = py(v);
      ctx.beginPath();
      ctx.moveTo(mL, y);
      ctx.lineTo(mL + cW, y);
      ctx.stroke();
      ctx.fillText(`${this.formatAxis(v, useSpeed)}`, mL - 6, y);
    }
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let v = xLo; v <= xHi + 0.0001; v += xStep) {
      const x = px(v);
      ctx.beginPath();
      ctx.moveTo(x, mT);
      ctx.lineTo(x, mT + cH);
      ctx.stroke();
      ctx.fillText(`${Math.round(v)}`, x, mT + cH + 4);
    }

    ctx.strokeStyle = gridStrong;
    ctx.beginPath();
    ctx.moveTo(mL, mT);
    ctx.lineTo(mL, mT + cH);
    ctx.lineTo(mL + cW, mT + cH);
    ctx.stroke();

    const effs = points.map((p) => p.efficiency).sort((a, b) => a - b);
    const refs = [
      effs[0] * 0.9,
      (effs[0] + effs[effs.length - 1]) / 2,
      effs[effs.length - 1] * 1.1,
    ];
    ctx.strokeStyle = 'rgba(255,255,255,0.10)';
    ctx.setLineDash([4, 4]);
    ctx.lineWidth = 1;
    for (const eff of refs) {
      if (!isFinite(eff) || eff <= 0) continue;
      const x1 = xLo;
      const y1 = eff * x1;
      const x2 = xHi;
      const y2 = eff * x2;
      const clip = this.clipLine(x1, y1, x2, y2, xLo, xHi, yLo, yHi);
      if (!clip) continue;
      ctx.beginPath();
      ctx.moveTo(px(clip[0]), py(clip[1]));
      ctx.lineTo(px(clip[2]), py(clip[3]));
      ctx.stroke();
    }
    ctx.setLineDash([]);

    ctx.fillStyle = textColor;
    ctx.font = '11px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.fillText(
      `${this.translate.instant('SESSION_COMPARE.SCATTER_X_LABEL')} (${xUnit})`,
      mL + cW / 2,
      mT + cH + 18,
    );
    ctx.save();
    ctx.translate(12, mT + cH / 2);
    ctx.rotate(-Math.PI / 2);
    ctx.textBaseline = 'middle';
    ctx.fillText(
      useSpeed
        ? `${this.translate.instant('SESSION_COMPARE.SCATTER_Y_LABEL_SPEED')} (${yUnit})`
        : `${this.translate.instant('SESSION_COMPARE.SCATTER_Y_LABEL_POWER')} (${yUnit})`,
      0,
      0,
    );
    ctx.restore();

    for (const p of points) {
      const x = px(p.x);
      const y = py(p.y);
      ctx.beginPath();
      ctx.arc(x, y, 14, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(${p.rgb.join(',')},0.18)`;
      ctx.fill();

      ctx.beginPath();
      ctx.arc(x, y, 5.5, 0, Math.PI * 2);
      ctx.fillStyle = p.color;
      ctx.fill();
      ctx.strokeStyle = 'rgba(255,255,255,0.85)';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    ctx.fillStyle = 'rgba(255,255,255,0.85)';
    ctx.font = '10px sans-serif';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    for (const p of points) {
      const x = px(p.x);
      const y = py(p.y);
      const label = `${this.formatAxis(p.y, useSpeed)}${yUnit} / ${Math.round(p.x)}${xUnit}`;
      ctx.fillText(label, x + 10, y - 12);
    }
  }

  private formatAxis(v: number, useSpeed: boolean): string {
    return useSpeed ? v.toFixed(1) : Math.round(v).toString();
  }

  private clipLine(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    xMin: number,
    xMax: number,
    yMin: number,
    yMax: number,
  ): [number, number, number, number] | null {
    const dx = x2 - x1;
    const dy = y2 - y1;
    let t0 = 0;
    let t1 = 1;
    const p = [-dx, dx, -dy, dy];
    const q = [x1 - xMin, xMax - x1, y1 - yMin, yMax - y1];
    for (let i = 0; i < 4; i++) {
      if (p[i] === 0) {
        if (q[i] < 0) return null;
      } else {
        const t = q[i] / p[i];
        if (p[i] < 0) {
          if (t > t1) return null;
          if (t > t0) t0 = t;
        } else {
          if (t < t0) return null;
          if (t < t1) t1 = t;
        }
      }
    }
    return [x1 + t0 * dx, y1 + t0 * dy, x1 + t1 * dx, y1 + t1 * dy];
  }
}

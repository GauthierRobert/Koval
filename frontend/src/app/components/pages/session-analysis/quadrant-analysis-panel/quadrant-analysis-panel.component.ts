import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FitRecord } from '../../../../services/metrics.service';

export type QuadrantProfile = 'punchy' | 'muscular' | 'spinner' | 'balanced' | 'easy';
export type QuadrantId = 1 | 2 | 3 | 4;

interface ScatterPoint {
  cpv: number;
  aepf: number;
  q: QuadrantId;
}

export interface YTick {
  value: number;
  topPct: number;
}

export interface QuadrantResult {
  q1Pct: number;
  q2Pct: number;
  q3Pct: number;
  q4Pct: number;
  cpvThreshold: number;
  aepfThreshold: number;
  maxAepf: number;
  yTicks: YTick[];
  ftpPct: number; // position % of FTP horizontal threshold (0=top, 100=bottom)
  quarters: { cpv: number; aepf: number }[];
  profile: QuadrantProfile;
  sampleCount: number;
}

// Coggan QA reference geometry.
const CRANK_LENGTH_M = 0.1725;
const REF_CADENCE_RPM = 90;
const REF_OMEGA = (REF_CADENCE_RPM * 2 * Math.PI) / 60;
const REF_CPV = CRANK_LENGTH_M * REF_OMEGA; // ~1.626 m/s

// Visible X band — clamps the chart to a realistic cycling cadence range
// (~33 rpm to ~122 rpm at 172.5 mm crank).
const MIN_CPV = 0.6;
const MAX_CPV = 2.2;
const RPM_PCT = ((REF_CPV - MIN_CPV) / (MAX_CPV - MIN_CPV)) * 100;
const X_TICKS = [
  { value: 0.6, leftPct: 0 },
  { value: 1.0, leftPct: 25 },
  { value: 1.4, leftPct: 50 },
  { value: 1.8, leftPct: 75 },
  { value: 2.2, leftPct: 100 },
];

const Q_COLORS: Record<QuadrantId, string> = {
  1: 'oklch(0.68 0.22 25)',
  2: 'oklch(0.72 0.18 55)',
  3: 'oklch(0.55 0.04 240)',
  4: 'oklch(0.70 0.16 220)',
};

@Component({
  selector: 'app-quadrant-analysis-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './quadrant-analysis-panel.component.html',
  styleUrl: './quadrant-analysis-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuadrantAnalysisPanelComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) records: FitRecord[] = [];
  @Input({ required: true }) sportType = '';
  @Input() ftp: number | null = null;

  @ViewChild('cv', { static: false }) canvasRef?: ElementRef<HTMLCanvasElement>;

  result: QuadrantResult | null = null;
  helpOpen = false;
  readonly qColors = Q_COLORS;
  readonly xAxisTicks = X_TICKS;
  readonly rpmPct = RPM_PCT;

  private points: ScatterPoint[] = [];
  private resizeObserver?: ResizeObserver;

  ngAfterViewInit(): void {
    this.recompute();
    this.attachResize();
  }

  ngOnChanges(_changes: SimpleChanges): void {
    this.helpOpen = false;
    this.recompute();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  toggleHelp(): void {
    this.helpOpen = !this.helpOpen;
  }

  closeHelp(): void {
    this.helpOpen = false;
  }

  private attachResize(): void {
    if (typeof ResizeObserver === 'undefined' || !this.canvasRef) return;
    this.resizeObserver = new ResizeObserver(() => this.draw());
    this.resizeObserver.observe(this.canvasRef.nativeElement);
  }

  private recompute(): void {
    this.result = this.computeQuadrants();
    setTimeout(() => this.draw());
  }

  private computeQuadrants(): QuadrantResult | null {
    if (this.sportType !== 'CYCLING') return null;
    if (!this.ftp || this.ftp <= 0) return null;
    if (!this.records || this.records.length < 30) return null;

    const cpvThreshold = REF_CPV;
    const aepfThreshold = this.ftp / REF_CPV;

    const points: ScatterPoint[] = [];
    const qCounts = [0, 0, 0, 0];
    let maxAepfData = aepfThreshold * 2.2;

    const quarterSums = [
      { cpv: 0, aepf: 0, n: 0 },
      { cpv: 0, aepf: 0, n: 0 },
      { cpv: 0, aepf: 0, n: 0 },
      { cpv: 0, aepf: 0, n: 0 },
    ];

    const n = this.records.length;
    for (let i = 0; i < n; i++) {
      const r = this.records[i];
      if (r.power <= 0 || r.cadence < 30) continue;

      const omega = (r.cadence * 2 * Math.PI) / 60;
      const cpv = CRANK_LENGTH_M * omega;
      const aepf = r.power / cpv;
      if (aepf > maxAepfData) maxAepfData = aepf;

      const isHighForce = aepf >= aepfThreshold;
      const isHighVel = cpv >= cpvThreshold;
      const q: QuadrantId = isHighForce ? (isHighVel ? 1 : 2) : isHighVel ? 4 : 3;

      points.push({ cpv, aepf, q });
      qCounts[q - 1]++;

      const qi = Math.min(3, Math.floor((i / n) * 4));
      quarterSums[qi].cpv += cpv;
      quarterSums[qi].aepf += aepf;
      quarterSums[qi].n++;
    }

    if (points.length < 30) return null;

    const total = points.length;
    const q1Pct = Math.round((qCounts[0] / total) * 100);
    const q2Pct = Math.round((qCounts[1] / total) * 100);
    const q3Pct = Math.round((qCounts[2] / total) * 100);
    const q4Pct = 100 - q1Pct - q2Pct - q3Pct;

    this.points = points;

    const maxAepf = Math.min(maxAepfData, aepfThreshold * 3.5);
    const yStep = niceStep(maxAepf, 5);
    const yTicks: YTick[] = [];
    for (let v = 0; v <= maxAepf + 0.5; v += yStep) {
      yTicks.push({ value: Math.round(v), topPct: (1 - v / maxAepf) * 100 });
    }
    const ftpPct = (1 - aepfThreshold / maxAepf) * 100;

    const quarters = quarterSums.map((q) =>
      q.n > 0 ? { cpv: q.cpv / q.n, aepf: q.aepf / q.n } : { cpv: 0, aepf: 0 },
    );

    return {
      q1Pct,
      q2Pct,
      q3Pct,
      q4Pct,
      cpvThreshold,
      aepfThreshold,
      maxAepf,
      yTicks,
      ftpPct,
      quarters,
      profile: this.classify(q1Pct, q2Pct, q3Pct, q4Pct),
      sampleCount: total,
    };
  }

  private classify(q1: number, q2: number, q3: number, q4: number): QuadrantProfile {
    if (q3 >= 65) return 'easy';
    const work = q1 + q2 + q4 || 1;
    const q1W = (q1 / work) * 100;
    const q2W = (q2 / work) * 100;
    const q4W = (q4 / work) * 100;
    if (q1W >= 45) return 'punchy';
    if (q2W >= 50) return 'muscular';
    if (q4W >= 55) return 'spinner';
    return 'balanced';
  }

  private draw(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas || !this.result) return;

    const dpr = window.devicePixelRatio || 1;
    const cssW = canvas.clientWidth;
    const cssH = canvas.clientHeight;
    if (cssW <= 0 || cssH <= 0) return;
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssW, cssH);

    // Canvas IS the data area — no internal padding. Tick labels live in HTML
    // around the canvas; FTP / 90rpm reference labels too.
    const w = cssW;
    const h = cssH;
    const maxAepf = this.result.maxAepf;

    const xOf = (cpv: number) =>
      ((Math.max(MIN_CPV, Math.min(cpv, MAX_CPV)) - MIN_CPV) / (MAX_CPV - MIN_CPV)) * w;
    const yOf = (aepf: number) => (1 - Math.min(aepf, maxAepf) / maxAepf) * h;

    const xT = xOf(this.result.cpvThreshold);
    const yT = yOf(this.result.aepfThreshold);

    // Quadrant tints — fill the whole canvas, split at the crossover.
    const tint = (color: string, opacity: number) => color.replace(')', ` / ${opacity})`);
    ctx.fillStyle = tint(Q_COLORS[1], 0.08);
    ctx.fillRect(xT, 0, w - xT, yT);
    ctx.fillStyle = tint(Q_COLORS[2], 0.08);
    ctx.fillRect(0, 0, xT, yT);
    ctx.fillStyle = tint(Q_COLORS[3], 0.05);
    ctx.fillRect(0, yT, xT, h - yT);
    ctx.fillStyle = tint(Q_COLORS[4], 0.08);
    ctx.fillRect(xT, yT, w - xT, h - yT);

    // Y gridlines at each tick.
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.lineWidth = 1;
    for (const t of this.result.yTicks) {
      if (t.value === 0) continue;
      const y = (t.topPct / 100) * h;
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }
    // X gridlines at the inner tick values (skip 0.6 and 2.2 — those are the edges).
    for (const t of X_TICKS) {
      if (t.leftPct === 0 || t.leftPct === 100) continue;
      const x = (t.leftPct / 100) * w;
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    }

    // Scatter — dots colored by quadrant, skipped if outside the visible band.
    const dotAlpha = this.points.length > 3000 ? 0.18 : this.points.length > 1000 ? 0.28 : 0.4;
    const dotRadius = this.points.length > 3000 ? 1.4 : 1.8;
    for (const p of this.points) {
      if (p.cpv < MIN_CPV || p.cpv > MAX_CPV) continue;
      ctx.fillStyle = tint(Q_COLORS[p.q], dotAlpha);
      ctx.beginPath();
      ctx.arc(xOf(p.cpv), yOf(p.aepf), dotRadius, 0, Math.PI * 2);
      ctx.fill();
    }

    // Threshold crosshair at FTP @ 90 rpm.
    ctx.strokeStyle = 'rgba(255,255,255,0.28)';
    ctx.setLineDash([3, 3]);
    ctx.beginPath();
    ctx.moveTo(xT, 0);
    ctx.lineTo(xT, h);
    ctx.moveTo(0, yT);
    ctx.lineTo(w, yT);
    ctx.stroke();
    ctx.setLineDash([]);

    // Reference marker — small target ring at exactly (FTP, 90rpm).
    ctx.strokeStyle = 'rgba(255,255,255,0.55)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(xT, yT, 5, 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = 'rgba(255,255,255,0.55)';
    ctx.beginPath();
    ctx.arc(xT, yT, 1.5, 0, Math.PI * 2);
    ctx.fill();

    this.drawTrail(ctx, xOf, yOf);

    // Axis spines — drawn at the canvas edges (left + bottom).
    ctx.strokeStyle = 'rgba(255,255,255,0.22)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0.5, 0);
    ctx.lineTo(0.5, h);
    ctx.lineTo(w, h - 0.5);
    ctx.stroke();

    this.drawCornerSummaries(ctx, w, h);
  }

  private drawTrail(
    ctx: CanvasRenderingContext2D,
    xOf: (cpv: number) => number,
    yOf: (aepf: number) => number,
  ): void {
    if (!this.result) return;
    const trail = this.result.quarters.filter((q) => q.cpv > 0);
    if (trail.length < 2) return;

    ctx.strokeStyle = 'rgba(255,255,255,0.55)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let i = 0; i < trail.length; i++) {
      const x = xOf(trail[i].cpv);
      const y = yOf(trail[i].aepf);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    for (let i = 0; i < trail.length; i++) {
      const x = xOf(trail[i].cpv);
      const y = yOf(trail[i].aepf);
      const alpha = 0.4 + 0.6 * (i / (trail.length - 1));
      const r = 2.5 + 1.5 * (i / (trail.length - 1));
      ctx.fillStyle = `rgba(255,255,255,${alpha})`;
      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fill();
    }
    const last = trail[trail.length - 1];
    ctx.strokeStyle = 'rgba(255,255,255,0.9)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(xOf(last.cpv), yOf(last.aepf), 6.5, 0, Math.PI * 2);
    ctx.stroke();
  }

  /** In-canvas per-quadrant summary block in each corner. */
  private drawCornerSummaries(ctx: CanvasRenderingContext2D, w: number, h: number): void {
    if (!this.result) return;
    const inset = 12;
    const slots: {
      q: QuadrantId;
      name: string;
      pct: number;
      x: number;
      y: number;
      halign: CanvasTextAlign;
      valign: CanvasTextBaseline;
    }[] = [
      {
        q: 2,
        name: 'MUSCULAR',
        pct: this.result.q2Pct,
        x: inset,
        y: inset,
        halign: 'left',
        valign: 'top',
      },
      {
        q: 1,
        name: 'POWER',
        pct: this.result.q1Pct,
        x: w - inset,
        y: inset,
        halign: 'right',
        valign: 'top',
      },
      {
        q: 3,
        name: 'RECOVERY',
        pct: this.result.q3Pct,
        x: inset,
        y: h - inset,
        halign: 'left',
        valign: 'bottom',
      },
      {
        q: 4,
        name: 'SPINNER',
        pct: this.result.q4Pct,
        x: w - inset,
        y: h - inset,
        halign: 'right',
        valign: 'bottom',
      },
    ];
    for (const s of slots) {
      ctx.textAlign = s.halign;
      ctx.font = '700 9px "Inter", sans-serif';
      ctx.fillStyle = 'rgba(255,255,255,0.55)';
      ctx.textBaseline = s.valign === 'top' ? 'top' : 'bottom';
      const titleY = s.valign === 'top' ? s.y : s.y - 24;
      ctx.fillText(`Q${s.q} · ${s.name}`, s.x, titleY);
      ctx.font = '800 22px "Inter", sans-serif';
      ctx.fillStyle = Q_COLORS[s.q];
      ctx.textBaseline = s.valign === 'top' ? 'top' : 'bottom';
      const pctY = s.valign === 'top' ? s.y + 12 : s.y;
      ctx.fillText(`${s.pct}%`, s.x, pctY);
    }
  }
}

/** Pick a clean tick step (1/2/5 × 10ⁿ) for ~`targetTicks` divisions. */
function niceStep(range: number, targetTicks: number): number {
  if (range <= 0) return 1;
  const rough = range / targetTicks;
  const pow10 = Math.pow(10, Math.floor(Math.log10(rough)));
  const norm = rough / pow10;
  const nice = norm < 1.5 ? 1 : norm < 3.5 ? 2 : norm < 7.5 ? 5 : 10;
  return nice * pow10;
}

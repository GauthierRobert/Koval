import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {VolumeEntry} from '../../../../services/analytics.service';

type VolumeMetric = 'time' | 'tss' | 'distance';

const SPORT_STACK = ['SWIMMING', 'CYCLING', 'RUNNING'] as const;

const SPORT_COLORS: Record<string, string> = {
  SWIMMING: '#00a0e9',
  CYCLING: '#34d399',
  RUNNING: '#f87171',
};

const SPORT_LABELS: Record<string, string> = {
  SWIMMING: 'Swim',
  CYCLING: 'Bike',
  RUNNING: 'Run',
};

@Component({
  selector: 'app-dashboard-volume-chart',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="volume-chart-wrap">
      <canvas #canvas class="volume-canvas"
        (mousemove)="onMouseMove($event)"
        (mouseleave)="onMouseLeave()"
        (touchstart)="onTouchStart($event)"
        (touchmove)="onTouchMove($event)"
        (touchend)="onTouchEnd()">
      </canvas>
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; height: 100%; overflow: hidden; }
    .volume-chart-wrap { display: flex; flex-direction: column; width: 100%; height: 100%; overflow: hidden; flex: 1; }
    .volume-canvas { width: 100%; flex: 1; min-height: 0; display: block; cursor: crosshair; }
  `],
})
export class DashboardVolumeChartComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() data: VolumeEntry[] = [];
  @Input() metric: VolumeMetric = 'time';

  @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;
  private ready = false;
  private ro: ResizeObserver | null = null;
  private themeObserver: MutationObserver | null = null;
  private hoverIdx: number | null = null;

  private readonly ML = 50;
  private readonly MR = 16;
  private readonly MT = 8;
  private readonly MB = 24;

  ngAfterViewInit(): void {
    this.ready = true;
    this.ro = new ResizeObserver(() => this.draw());
    this.ro.observe(this.canvasRef.nativeElement);
    this.themeObserver = new MutationObserver(() => this.draw());
    this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    this.draw();
  }

  ngOnChanges(): void {
    if (this.ready) this.draw();
  }

  ngOnDestroy(): void {
    this.ro?.disconnect();
    this.themeObserver?.disconnect();
  }

  onMouseMove(event: MouseEvent): void {
    const idx = this.hitTestWeek(event.clientX);
    if (idx !== this.hoverIdx) { this.hoverIdx = idx; this.draw(); }
  }

  onMouseLeave(): void {
    if (this.hoverIdx !== null) { this.hoverIdx = null; this.draw(); }
  }

  onTouchStart(event: TouchEvent): void {
    if (event.touches.length === 1) {
      const idx = this.hitTestWeek(event.touches[0].clientX);
      if (idx !== this.hoverIdx) { this.hoverIdx = idx; this.draw(); }
    }
  }

  onTouchMove(event: TouchEvent): void {
    if (event.touches.length === 1) {
      const idx = this.hitTestWeek(event.touches[0].clientX);
      if (idx !== this.hoverIdx) { this.hoverIdx = idx; this.draw(); }
    }
  }

  onTouchEnd(): void {}

  private getSportValue(entry: VolumeEntry, sport: string): number {
    switch (this.metric) {
      case 'tss': return entry.sportTss?.[sport] ?? 0;
      case 'time': return (entry.sportDurationSeconds?.[sport] ?? 0) / 3600; // hours
      case 'distance': return (entry.sportDistanceMeters?.[sport] ?? 0) / 1000; // km
    }
  }

  private getTotalValue(entry: VolumeEntry): number {
    switch (this.metric) {
      case 'tss': return entry.totalTss;
      case 'time': return entry.totalDurationSeconds / 3600;
      case 'distance': return entry.totalDistanceMeters / 1000;
    }
  }

  private formatValue(val: number): string {
    switch (this.metric) {
      case 'tss': return `${Math.round(val)}`;
      case 'time': {
        const h = Math.floor(val);
        const m = Math.round((val - h) * 60);
        return h > 0 ? `${h}h${m > 0 ? String(m).padStart(2, '0') : ''}` : `${m}m`;
      }
      case 'distance': return `${val.toFixed(1)}km`;
    }
  }

  private formatTooltipValue(val: number): string {
    switch (this.metric) {
      case 'tss': return `${Math.round(val)} TSS`;
      case 'time': {
        const h = Math.floor(val);
        const m = Math.round((val - h) * 60);
        return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}m`;
      }
      case 'distance': return `${val.toFixed(1)} km`;
    }
  }

  private hitTestWeek(clientX: number): number | null {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas || !this.data || this.data.length === 0) return null;
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const mouseX = (clientX - rect.left) * scaleX;
    const cW = canvas.width - this.ML - this.MR;
    const entries = this.getLast10Weeks();
    const n = entries.length;
    const step = n > 1 ? cW / (n - 1) : cW;

    let bestIdx = 0;
    let bestDist = Infinity;
    for (let i = 0; i < n; i++) {
      const dist = Math.abs(mouseX - (this.ML + i * step));
      if (dist < bestDist) { bestDist = dist; bestIdx = i; }
    }
    return bestDist < step * 0.7 ? bestIdx : null;
  }

  private themeRgb: [number, number, number] = [255, 255, 255];
  private themeBgRgb: [number, number, number] = [16, 16, 22];

  private refreshThemeColors(): void {
    const styles = getComputedStyle(this.canvasRef.nativeElement);
    this.themeRgb = this.parseColor(styles.getPropertyValue('--text-color').trim()) ?? [255, 255, 255];
    this.themeBgRgb = this.parseColor(styles.getPropertyValue('--bg-color').trim()) ?? [16, 16, 22];
  }

  private fg(alpha: number): string {
    const [r, g, b] = this.themeRgb;
    return `rgba(${r},${g},${b},${alpha})`;
  }

  private parseColor(raw: string): [number, number, number] | null {
    if (!raw) return null;
    const hex = raw.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
    if (hex) {
      const h = hex[1].length === 3
        ? hex[1].split('').map(c => c + c).join('')
        : hex[1];
      return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
    }
    const rgb = raw.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
    if (rgb) return [parseInt(rgb[1]), parseInt(rgb[2]), parseInt(rgb[3])];
    return null;
  }

  private draw(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    this.refreshThemeColors();

    const dpr = window.devicePixelRatio || 1;
    const cssW = canvas.offsetWidth;
    const cssH = canvas.offsetHeight;
    canvas.width = cssW * dpr;
    canvas.height = cssH * dpr;

    const ctx = canvas.getContext('2d')!;
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, cssW, cssH);

    if (!this.data || this.data.length === 0) {
      this.drawEmpty(ctx, cssW, cssH);
      return;
    }

    const entries = this.getLast10Weeks();

    const {ML, MR, MT, MB} = this;
    const cW = cssW - ML - MR;
    const cH = cssH - MT - MB;
    const n = entries.length;
    const step = n > 1 ? cW / (n - 1) : cW;

    // Compute stacks
    const stacks = entries.map((e) => {
      let cum = 0;
      return SPORT_STACK.map((sport) => {
        const val = this.getSportValue(e, sport);
        const bottom = cum;
        cum += val;
        return {sport, bottom, top: cum, val};
      });
    });

    const maxY = Math.max(...stacks.map((s) => s[s.length - 1].top), 0.1);
    const niceMax = this.niceNum(maxY);

    const toX = (i: number) => ML + i * step;
    const toY = (val: number) => MT + cH - (val / niceMax) * cH;

    // Grid lines
    const gridCount = 4;
    ctx.strokeStyle = this.fg(0.12);
    ctx.lineWidth = 1;
    ctx.setLineDash([]);
    for (let g = 0; g <= gridCount; g++) {
      const val = (niceMax / gridCount) * g;
      const y = toY(val);
      ctx.beginPath();
      ctx.moveTo(ML, y);
      ctx.lineTo(cssW - MR, y);
      ctx.stroke();

      ctx.fillStyle = this.fg(0.55);
      ctx.font = '10px monospace';
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      ctx.fillText(this.formatValue(val), ML - 8, y);
    }

    // Stacked areas
    for (let si = 0; si < SPORT_STACK.length; si++) {
      const color = SPORT_COLORS[SPORT_STACK[si]];

      ctx.beginPath();
      for (let i = 0; i < n; i++) {
        const x = toX(i);
        const y = toY(stacks[i][si].top);
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      }
      for (let i = n - 1; i >= 0; i--) {
        ctx.lineTo(toX(i), toY(stacks[i][si].bottom));
      }
      ctx.closePath();
      ctx.fillStyle = this.hexToRgba(color, 0.55);
      ctx.fill();

      // Stroke top line
      ctx.beginPath();
      for (let i = 0; i < n; i++) {
        const x = toX(i);
        const y = toY(stacks[i][si].top);
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      }
      ctx.strokeStyle = this.hexToRgba(color, 0.7);
      ctx.lineWidth = 1;
      ctx.stroke();
    }

    // X labels
    ctx.fillStyle = this.fg(0.6);
    ctx.font = '10px monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let i = 0; i < n; i++) {
      ctx.fillText(this.formatWeekLabel(entries[i].period), toX(i), cssH - MB + 6);
    }

    // Hover
    if (this.hoverIdx !== null && this.hoverIdx < n) {
      const hi = this.hoverIdx;
      const x = toX(hi);

      ctx.strokeStyle = this.fg(0.4);
      ctx.lineWidth = 1;
      ctx.setLineDash([4, 4]);
      ctx.beginPath();
      ctx.moveTo(x, MT);
      ctx.lineTo(x, cssH - MB);
      ctx.stroke();
      ctx.setLineDash([]);

      for (let si = 0; si < SPORT_STACK.length; si++) {
        const y = toY(stacks[hi][si].top);
        ctx.fillStyle = SPORT_COLORS[SPORT_STACK[si]];
        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = 'rgba(0,0,0,0.3)';
        ctx.lineWidth = 1;
        ctx.stroke();
      }

      this.drawTooltip(ctx, cssW, cssH, x, stacks[hi], entries[hi]);
    }
  }

  private drawTooltip(
    ctx: CanvasRenderingContext2D, cssW: number, cssH: number, anchorX: number,
    stack: {sport: string; bottom: number; top: number; val: number}[],
    entry: VolumeEntry,
  ): void {
    const padding = 10;
    const lineH = 18;
    const dotR = 4;
    const weekLabel = this.formatWeekLabel(entry.period);
    const totalVal = this.getTotalValue(entry);

    const lines = [
      {label: weekLabel, value: this.formatTooltipValue(totalVal), color: ''},
      ...stack.filter((s) => s.val > 0).reverse().map((s) => ({
        label: SPORT_LABELS[s.sport] || s.sport,
        value: this.formatTooltipValue(s.val),
        color: SPORT_COLORS[s.sport],
      })),
    ];

    ctx.font = 'bold 11px monospace';
    const maxTextW = Math.max(...lines.map((l) => ctx.measureText(`${l.label}  ${l.value}`).width));
    const boxW = maxTextW + padding * 2 + (dotR * 2 + 6);
    const boxH = lines.length * lineH + padding * 2;

    let tx = anchorX + 14;
    if (tx + boxW > cssW - 8) tx = anchorX - boxW - 14;
    let ty = this.MT + 8;
    if (ty + boxH > cssH - this.MB) ty = cssH - this.MB - boxH - 4;

    const [br, bg, bb] = this.themeBgRgb;
    ctx.fillStyle = `rgba(${br},${bg},${bb},0.95)`;
    ctx.strokeStyle = this.fg(0.2);
    ctx.lineWidth = 1;
    this.roundRect(ctx, tx, ty, boxW, boxH, 8);
    ctx.fill();
    ctx.stroke();

    let curY = ty + padding;
    for (const line of lines) {
      const textX = tx + padding + (line.color ? dotR * 2 + 6 : 0);
      if (line.color) {
        ctx.fillStyle = line.color;
        ctx.beginPath();
        ctx.arc(tx + padding + dotR, curY + lineH / 2, dotR, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.fillStyle = line.color ? this.fg(0.75) : this.fg(0.95);
      ctx.font = line.color ? '11px monospace' : 'bold 11px monospace';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'middle';
      ctx.fillText(line.label, textX, curY + lineH / 2);
      ctx.textAlign = 'right';
      ctx.fillText(line.value, tx + boxW - padding, curY + lineH / 2);
      curY += lineH;
    }
  }

  private drawEmpty(ctx: CanvasRenderingContext2D, w: number, h: number): void {
    ctx.fillStyle = this.fg(0.35);
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('No volume data', w / 2, h / 2);
  }

  private formatWeekLabel(period: string): string {
    const match = period.match(/W(\d+)$/);
    return match ? `W${match[1]}` : period;
  }

  /** Round up to a clean grid-friendly number just above val. */
  private niceNum(val: number): number {
    if (val <= 0) return 1;
    const gridCount = 4;
    const raw = val * 1.2; // 20% headroom
    const step = raw / gridCount;
    const orderStep = Math.pow(10, Math.floor(Math.log10(step)));
    const fracStep = step / orderStep;
    let niceStep: number;
    if (fracStep <= 1) niceStep = 1;
    else if (fracStep <= 1.5) niceStep = 1.5;
    else if (fracStep <= 2) niceStep = 2;
    else if (fracStep <= 2.5) niceStep = 2.5;
    else if (fracStep <= 5) niceStep = 5;
    else niceStep = 10;
    return niceStep * orderStep * gridCount;
  }

  /** Always returns exactly 10 weeks (current + 9 prior), filling gaps with empty entries. */
  private getLast10Weeks(): VolumeEntry[] {
    const weeks: string[] = [];
    const now = new Date();
    for (let i = 9; i >= 0; i--) {
      const d = new Date(now);
      d.setDate(d.getDate() - i * 7);
      weeks.push(this.isoWeekKey(d));
    }

    const dataMap = new Map<string, VolumeEntry>();
    for (const e of (this.data ?? [])) {
      dataMap.set(e.period, e);
    }

    const empty: VolumeEntry = {
      period: '', totalTss: 0, totalDurationSeconds: 0, totalDistanceMeters: 0,
      sportTss: {}, sportDurationSeconds: {}, sportDistanceMeters: {},
    };

    return weeks.map((w) => dataMap.get(w) ?? {...empty, period: w});
  }

  private isoWeekKey(d: Date): string {
    // Compute ISO week number and year
    const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    date.setUTCDate(date.getUTCDate() + 4 - (date.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil(((date.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
    return `${date.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
  }

  private hexToRgba(hex: string, alpha: number): string {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r},${g},${b},${alpha})`;
  }

  private roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }
}

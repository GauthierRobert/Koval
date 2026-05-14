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
import { VolumeEntry } from '../../../../services/analytics.service';
import {
  SPORT_COLORS,
  SPORT_LABELS,
  SPORT_STACK,
  StackSegment,
  VolumeMetric,
  buildStacks,
  formatAxisValue,
  formatTooltipValue,
  formatWeekLabel,
  getLast10Weeks,
  getTotalValue,
  hexToRgba,
  niceMaxY,
  parseColor,
  roundRectPath,
} from './dashboard-volume-chart.utils';

@Component({
  selector: 'app-dashboard-volume-chart',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="volume-chart-wrap">
      <canvas
        #canvas
        class="volume-canvas"
        (mousemove)="onMouseMove($event)"
        (mouseleave)="onMouseLeave()"
        (touchstart)="onTouchStart($event)"
        (touchmove)="onTouchMove($event)"
        (touchend)="onTouchEnd()"
      >
      </canvas>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
        height: 100%;
        overflow: hidden;
      }
      .volume-chart-wrap {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100%;
        overflow: hidden;
        flex: 1;
      }
      .volume-canvas {
        width: 100%;
        flex: 1;
        min-height: 0;
        display: block;
        cursor: crosshair;
      }
    `,
  ],
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

  private themeRgb: [number, number, number] = [255, 255, 255];
  private themeBgRgb: [number, number, number] = [16, 16, 22];

  ngAfterViewInit(): void {
    this.ready = true;
    this.ro = new ResizeObserver(() => this.draw());
    this.ro.observe(this.canvasRef.nativeElement);
    this.themeObserver = new MutationObserver(() => this.draw());
    this.themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme'],
    });
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
    this.updateHover(this.hitTestWeek(event.clientX));
  }

  onMouseLeave(): void {
    this.updateHover(null);
  }

  onTouchStart(event: TouchEvent): void {
    if (event.touches.length === 1) {
      this.updateHover(this.hitTestWeek(event.touches[0].clientX));
    }
  }

  onTouchMove(event: TouchEvent): void {
    if (event.touches.length === 1) {
      this.updateHover(this.hitTestWeek(event.touches[0].clientX));
    }
  }

  onTouchEnd(): void {}

  private updateHover(idx: number | null): void {
    if (idx !== this.hoverIdx) {
      this.hoverIdx = idx;
      this.draw();
    }
  }

  private hitTestWeek(clientX: number): number | null {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas || !this.data || this.data.length === 0) return null;
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const mouseX = (clientX - rect.left) * scaleX;
    const cW = canvas.width - this.ML - this.MR;
    const entries = getLast10Weeks(this.data);
    const n = entries.length;
    const step = n > 1 ? cW / (n - 1) : cW;

    let bestIdx = 0;
    let bestDist = Infinity;
    for (let i = 0; i < n; i++) {
      const dist = Math.abs(mouseX - (this.ML + i * step));
      if (dist < bestDist) {
        bestDist = dist;
        bestIdx = i;
      }
    }
    return bestDist < step * 0.7 ? bestIdx : null;
  }

  private refreshThemeColors(): void {
    const styles = getComputedStyle(this.canvasRef.nativeElement);
    this.themeRgb = parseColor(styles.getPropertyValue('--text-color').trim()) ?? [255, 255, 255];
    this.themeBgRgb = parseColor(styles.getPropertyValue('--bg-color').trim()) ?? [16, 16, 22];
  }

  private fg(alpha: number): string {
    const [r, g, b] = this.themeRgb;
    return `rgba(${r},${g},${b},${alpha})`;
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

    const entries = getLast10Weeks(this.data);
    const { ML, MR, MT, MB } = this;
    const cW = cssW - ML - MR;
    const cH = cssH - MT - MB;
    const n = entries.length;
    const step = n > 1 ? cW / (n - 1) : cW;

    const stacks = buildStacks(entries, this.metric);
    const maxY = Math.max(...stacks.map((s) => s[s.length - 1].top), 0.1);
    const niceMax = niceMaxY(maxY);

    const toX = (i: number) => ML + i * step;
    const toY = (val: number) => MT + cH - (val / niceMax) * cH;

    this.drawGrid(ctx, cssW, niceMax, toY);
    this.drawStacks(ctx, stacks, n, toX, toY);
    this.drawXLabels(ctx, entries, n, toX, cssH);

    if (this.hoverIdx !== null && this.hoverIdx < n) {
      this.drawHover(ctx, cssW, cssH, this.hoverIdx, stacks, entries, toX, toY);
    }
  }

  private drawGrid(
    ctx: CanvasRenderingContext2D,
    cssW: number,
    niceMax: number,
    toY: (val: number) => number,
  ): void {
    const gridCount = 4;
    ctx.strokeStyle = this.fg(0.12);
    ctx.lineWidth = 1;
    ctx.setLineDash([]);
    for (let g = 0; g <= gridCount; g++) {
      const val = (niceMax / gridCount) * g;
      const y = toY(val);
      ctx.beginPath();
      ctx.moveTo(this.ML, y);
      ctx.lineTo(cssW - this.MR, y);
      ctx.stroke();

      ctx.fillStyle = this.fg(0.55);
      ctx.font = '10px monospace';
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      ctx.fillText(formatAxisValue(val, this.metric), this.ML - 8, y);
    }
  }

  private drawStacks(
    ctx: CanvasRenderingContext2D,
    stacks: StackSegment[][],
    n: number,
    toX: (i: number) => number,
    toY: (val: number) => number,
  ): void {
    for (let si = 0; si < SPORT_STACK.length; si++) {
      const color = SPORT_COLORS[SPORT_STACK[si]];

      ctx.beginPath();
      for (let i = 0; i < n; i++) {
        const x = toX(i);
        const y = toY(stacks[i][si].top);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      for (let i = n - 1; i >= 0; i--) {
        ctx.lineTo(toX(i), toY(stacks[i][si].bottom));
      }
      ctx.closePath();
      ctx.fillStyle = hexToRgba(color, 0.55);
      ctx.fill();

      ctx.beginPath();
      for (let i = 0; i < n; i++) {
        const x = toX(i);
        const y = toY(stacks[i][si].top);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.strokeStyle = hexToRgba(color, 0.7);
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }

  private drawXLabels(
    ctx: CanvasRenderingContext2D,
    entries: VolumeEntry[],
    n: number,
    toX: (i: number) => number,
    cssH: number,
  ): void {
    ctx.fillStyle = this.fg(0.6);
    ctx.font = '10px monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let i = 0; i < n; i++) {
      ctx.fillText(formatWeekLabel(entries[i].period), toX(i), cssH - this.MB + 6);
    }
  }

  private drawHover(
    ctx: CanvasRenderingContext2D,
    cssW: number,
    cssH: number,
    hi: number,
    stacks: StackSegment[][],
    entries: VolumeEntry[],
    toX: (i: number) => number,
    toY: (val: number) => number,
  ): void {
    const x = toX(hi);

    ctx.strokeStyle = this.fg(0.4);
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(x, this.MT);
    ctx.lineTo(x, cssH - this.MB);
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

  private drawTooltip(
    ctx: CanvasRenderingContext2D,
    cssW: number,
    cssH: number,
    anchorX: number,
    stack: StackSegment[],
    entry: VolumeEntry,
  ): void {
    const padding = 10;
    const lineH = 18;
    const dotR = 4;
    const weekLabel = formatWeekLabel(entry.period);
    const totalVal = getTotalValue(entry, this.metric);

    const lines = [
      { label: weekLabel, value: formatTooltipValue(totalVal, this.metric), color: '' },
      ...stack
        .filter((s) => s.val > 0)
        .reverse()
        .map((s) => ({
          label: SPORT_LABELS[s.sport] || s.sport,
          value: formatTooltipValue(s.val, this.metric),
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
    roundRectPath(ctx, tx, ty, boxW, boxH, 8);
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
}

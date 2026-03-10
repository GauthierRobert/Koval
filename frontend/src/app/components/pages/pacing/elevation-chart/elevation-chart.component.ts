import {
  Component,
  Input,
  OnChanges,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  AfterViewInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { PacingSegment } from '../../../../services/pacing.service';

@Component({
  selector: 'app-elevation-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chart-wrapper">
      <canvas
        #chartCanvas
        (mousemove)="onMouseMove($event)"
        (mouseleave)="onMouseLeave()"
      ></canvas>
      <div
        class="tooltip"
        *ngIf="tooltipVisible"
        [style.left.px]="tooltipX"
        [style.top.px]="tooltipY"
      >
        <div class="tooltip-line">
          <span class="tooltip-label">Distance:</span>
          <span>{{ tooltipData.distance }}</span>
        </div>
        <div class="tooltip-line">
          <span class="tooltip-label">Elevation:</span>
          <span>{{ tooltipData.elevation }}</span>
        </div>
        <div class="tooltip-line">
          <span class="tooltip-label">Gradient:</span>
          <span>{{ tooltipData.gradient }}</span>
        </div>
        <div class="tooltip-line" *ngIf="tooltipData.power">
          <span class="tooltip-label">Power:</span>
          <span>{{ tooltipData.power }}</span>
        </div>
        <div class="tooltip-line" *ngIf="tooltipData.pace">
          <span class="tooltip-label">Pace:</span>
          <span>{{ tooltipData.pace }}</span>
        </div>
        <div class="tooltip-line">
          <span class="tooltip-label">Fatigue:</span>
          <span>{{ tooltipData.fatigue }}</span>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .chart-wrapper {
        position: relative;
        width: 100%;
        height: 300px;
        background: var(--surface-color, #1a1a2e);
        border-radius: 12px;
        overflow: hidden;
      }
      canvas {
        width: 100%;
        height: 100%;
        cursor: crosshair;
      }
      .tooltip {
        position: absolute;
        background: rgba(0, 0, 0, 0.85);
        border: 1px solid var(--primary-color, #ff9d00);
        border-radius: 8px;
        padding: 8px 12px;
        pointer-events: none;
        z-index: 10;
        font-size: 12px;
        min-width: 160px;
      }
      .tooltip-line {
        display: flex;
        justify-content: space-between;
        gap: 12px;
        padding: 1px 0;
      }
      .tooltip-label {
        color: rgba(255, 255, 255, 0.6);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ElevationChartComponent implements OnChanges, AfterViewInit {
  @Input() segments: PacingSegment[] = [];
  @ViewChild('chartCanvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  tooltipVisible = false;
  tooltipX = 0;
  tooltipY = 0;
  tooltipData: any = {};

  private ctx!: CanvasRenderingContext2D;
  private padding = { top: 30, right: 60, bottom: 40, left: 60 };
  private resizeObserver!: ResizeObserver;

  ngAfterViewInit(): void {
    const canvas = this.canvasRef.nativeElement;
    this.ctx = canvas.getContext('2d')!;

    this.resizeObserver = new ResizeObserver(() => this.render());
    this.resizeObserver.observe(canvas.parentElement!);

    this.render();
  }

  ngOnChanges(): void {
    if (this.ctx) {
      this.render();
    }
  }

  private render(): void {
    if (!this.segments.length) return;

    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.parentElement!.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    canvas.style.width = rect.width + 'px';
    canvas.style.height = rect.height + 'px';
    this.ctx.scale(dpr, dpr);

    const w = rect.width;
    const h = rect.height;
    const p = this.padding;
    const plotW = w - p.left - p.right;
    const plotH = h - p.top - p.bottom;

    // Clear
    this.ctx.clearRect(0, 0, w, h);

    // Data ranges
    const maxDist = Math.max(...this.segments.map((s) => s.endDistance));
    const elevations = this.segments.map((s) => s.elevation);
    const minElev = Math.min(...elevations) - 20;
    const maxElev = Math.max(...elevations) + 20;

    const xScale = (d: number) => p.left + (d / maxDist) * plotW;
    const yElevScale = (e: number) => p.top + plotH - ((e - minElev) / (maxElev - minElev)) * plotH;

    // Draw elevation fill
    this.ctx.beginPath();
    this.ctx.moveTo(xScale(this.segments[0].startDistance), yElevScale(this.segments[0].elevation));
    for (const seg of this.segments) {
      this.ctx.lineTo(xScale(seg.endDistance), yElevScale(seg.elevation));
    }
    this.ctx.lineTo(xScale(maxDist), p.top + plotH);
    this.ctx.lineTo(p.left, p.top + plotH);
    this.ctx.closePath();

    // Gradient fill colored by gradient severity
    const grad = this.ctx.createLinearGradient(0, p.top, 0, p.top + plotH);
    grad.addColorStop(0, 'rgba(255, 157, 0, 0.4)');
    grad.addColorStop(0.5, 'rgba(255, 157, 0, 0.15)');
    grad.addColorStop(1, 'rgba(255, 157, 0, 0.05)');
    this.ctx.fillStyle = grad;
    this.ctx.fill();

    // Elevation line
    this.ctx.beginPath();
    this.ctx.moveTo(xScale(this.segments[0].startDistance), yElevScale(this.segments[0].elevation));
    for (const seg of this.segments) {
      this.ctx.lineTo(xScale(seg.endDistance), yElevScale(seg.elevation));
    }
    this.ctx.strokeStyle = '#ff9d00';
    this.ctx.lineWidth = 2;
    this.ctx.stroke();

    // Draw gradient color bands behind elevation
    for (const seg of this.segments) {
      const x1 = xScale(seg.startDistance);
      const x2 = xScale(seg.endDistance);
      const g = Math.abs(seg.gradient);
      let color = 'rgba(52, 211, 153, 0.15)'; // green: flat
      if (g > 6) color = 'rgba(239, 68, 68, 0.2)'; // red: steep
      else if (g > 3) color = 'rgba(255, 157, 0, 0.2)'; // orange: moderate
      this.ctx.fillStyle = color;
      this.ctx.fillRect(x1, p.top, x2 - x1, plotH);
    }

    // Overlay power line (bike) or pace line (run) on secondary axis
    const hasPower = this.segments.some((s) => s.targetPower != null);
    const hasPace = this.segments.some((s) => s.targetPace != null);

    if (hasPower) {
      const powers = this.segments.filter((s) => s.targetPower != null).map((s) => s.targetPower!);
      const minP = Math.min(...powers) * 0.9;
      const maxP = Math.max(...powers) * 1.1;
      const yPowerScale = (pw: number) => p.top + plotH - ((pw - minP) / (maxP - minP)) * plotH;

      this.ctx.beginPath();
      let started = false;
      for (const seg of this.segments) {
        if (seg.targetPower == null) continue;
        const x = xScale((seg.startDistance + seg.endDistance) / 2);
        const y = yPowerScale(seg.targetPower);
        if (!started) {
          this.ctx.moveTo(x, y);
          started = true;
        } else {
          this.ctx.lineTo(x, y);
        }
      }
      this.ctx.strokeStyle = '#60a5fa';
      this.ctx.lineWidth = 2;
      this.ctx.stroke();

      // Power axis labels (right side)
      this.ctx.fillStyle = '#60a5fa';
      this.ctx.font = '11px monospace';
      this.ctx.textAlign = 'left';
      this.ctx.fillText(`${Math.round(maxP)}W`, w - p.right + 8, p.top + 12);
      this.ctx.fillText(`${Math.round(minP)}W`, w - p.right + 8, p.top + plotH);
    }

    if (hasPace) {
      const paces = this.segments
        .filter((s) => s.targetPace != null)
        .map((s) => this.parsePace(s.targetPace!));
      const minPace = Math.min(...paces) * 0.9;
      const maxPace = Math.max(...paces) * 1.1;
      // Pace: lower is faster, so invert Y
      const yPaceScale = (pc: number) => p.top + ((pc - minPace) / (maxPace - minPace)) * plotH;

      this.ctx.beginPath();
      let started = false;
      for (const seg of this.segments) {
        if (seg.targetPace == null) continue;
        const x = xScale((seg.startDistance + seg.endDistance) / 2);
        const y = yPaceScale(this.parsePace(seg.targetPace));
        if (!started) {
          this.ctx.moveTo(x, y);
          started = true;
        } else {
          this.ctx.lineTo(x, y);
        }
      }
      this.ctx.strokeStyle = '#34d399';
      this.ctx.lineWidth = 2;
      this.ctx.stroke();

      // Pace axis labels (right side)
      this.ctx.fillStyle = '#34d399';
      this.ctx.font = '11px monospace';
      this.ctx.textAlign = 'left';
      const fastPace = Math.round(minPace);
      const slowPace = Math.round(maxPace);
      this.ctx.fillText(
        `${Math.floor(fastPace / 60)}:${String(fastPace % 60).padStart(2, '0')}`,
        w - p.right + 8,
        p.top + 12,
      );
      this.ctx.fillText(
        `${Math.floor(slowPace / 60)}:${String(slowPace % 60).padStart(2, '0')}`,
        w - p.right + 8,
        p.top + plotH,
      );
    }

    // Fatigue overlay (increasing red tint from left to right)
    if (this.segments.length > 1) {
      const maxFatigue = Math.max(...this.segments.map((s) => s.cumulativeFatigue));
      if (maxFatigue > 0) {
        for (const seg of this.segments) {
          const x1 = xScale(seg.startDistance);
          const x2 = xScale(seg.endDistance);
          const alpha = Math.min((seg.cumulativeFatigue / maxFatigue) * 0.15, 0.15);
          this.ctx.fillStyle = `rgba(239, 68, 68, ${alpha})`;
          this.ctx.fillRect(x1, p.top, x2 - x1, plotH);
        }
      }
    }

    // Nutrition markers
    for (const seg of this.segments) {
      if (seg.nutritionSuggestion) {
        const x = xScale((seg.startDistance + seg.endDistance) / 2);
        this.ctx.fillStyle = '#fbbf24';
        this.ctx.beginPath();
        this.ctx.arc(x, p.top + 12, 5, 0, Math.PI * 2);
        this.ctx.fill();
        this.ctx.fillStyle = '#000';
        this.ctx.font = 'bold 8px monospace';
        this.ctx.textAlign = 'center';
        this.ctx.fillText('N', x, p.top + 15);
      }
    }

    // X-axis labels
    this.ctx.fillStyle = 'rgba(255,255,255,0.6)';
    this.ctx.font = '11px monospace';
    this.ctx.textAlign = 'center';
    const numLabels = Math.min(8, this.segments.length);
    const step = Math.ceil(this.segments.length / numLabels);
    for (let i = 0; i < this.segments.length; i += step) {
      const s = this.segments[i];
      const x = xScale(s.startDistance);
      const label = (s.startDistance / 1000).toFixed(1) + 'km';
      this.ctx.fillText(label, x, h - p.bottom + 20);
    }

    // Y-axis labels (elevation, left side)
    this.ctx.fillStyle = '#ff9d00';
    this.ctx.textAlign = 'right';
    this.ctx.fillText(`${Math.round(maxElev)}m`, p.left - 8, p.top + 12);
    this.ctx.fillText(`${Math.round(minElev)}m`, p.left - 8, p.top + plotH);

    // Legend
    this.ctx.font = '10px monospace';
    this.ctx.textAlign = 'left';
    let legendX = p.left;
    this.ctx.fillStyle = '#ff9d00';
    this.ctx.fillText('— Elevation', legendX, p.top - 10);
    legendX += 90;
    if (hasPower) {
      this.ctx.fillStyle = '#60a5fa';
      this.ctx.fillText('— Power', legendX, p.top - 10);
      legendX += 70;
    }
    if (hasPace) {
      this.ctx.fillStyle = '#34d399';
      this.ctx.fillText('— Pace', legendX, p.top - 10);
    }
  }

  onMouseMove(event: MouseEvent): void {
    if (!this.segments.length) return;

    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const p = this.padding;
    const plotW = rect.width - p.left - p.right;
    const maxDist = Math.max(...this.segments.map((s) => s.endDistance));
    const dist = ((x - p.left) / plotW) * maxDist;

    const seg = this.segments.find((s) => dist >= s.startDistance && dist <= s.endDistance);
    if (!seg) {
      this.tooltipVisible = false;
      return;
    }

    this.tooltipVisible = true;
    this.tooltipX = Math.min(x + 15, rect.width - 180);
    this.tooltipY = Math.max(event.clientY - rect.top - 80, 10);
    this.tooltipData = {
      distance: (seg.startDistance / 1000).toFixed(1) + ' - ' + (seg.endDistance / 1000).toFixed(1) + ' km',
      elevation: Math.round(seg.elevation) + ' m',
      gradient: seg.gradient.toFixed(1) + '%',
      power: seg.targetPower ? seg.targetPower + ' W' : null,
      pace: seg.targetPace || null,
      fatigue: (seg.cumulativeFatigue * 100).toFixed(0) + '%',
    };
  }

  onMouseLeave(): void {
    this.tooltipVisible = false;
  }

  private parsePace(paceStr: string): number {
    const cleaned = paceStr.replace(' /km', '');
    const [min, sec] = cleaned.split(':').map(Number);
    return min * 60 + sec;
  }
}

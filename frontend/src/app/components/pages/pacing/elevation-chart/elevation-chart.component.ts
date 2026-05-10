import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import {PacingSegment, SegmentRange} from '../../../../services/pacing.service';

@Component({
  selector: 'app-elevation-chart',
  standalone: true,
  template: `
    <div class="chart-wrapper">
      <canvas
        #chartCanvas
        (mousemove)="onMouseMove($event)"
        (mouseleave)="onMouseLeave()"
      ></canvas>
      @if (tooltipVisible) {
        <div
          class="tooltip"
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
          @if (tooltipData.power) {
            <div class="tooltip-line">
              <span class="tooltip-label">Power:</span>
              <span>{{ tooltipData.power }}</span>
            </div>
          }
          @if (tooltipData.speed) {
            <div class="tooltip-line">
              <span class="tooltip-label">Speed:</span>
              <span>{{ tooltipData.speed }}</span>
            </div>
          }
          @if (tooltipData.pace) {
            <div class="tooltip-line">
              <span class="tooltip-label">Pace:</span>
              <span>{{ tooltipData.pace }}</span>
            </div>
          }
          <div class="tooltip-line">
            <span class="tooltip-label">Fatigue:</span>
            <span>{{ tooltipData.fatigue }}</span>
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
      .chart-wrapper {
        position: relative;
        width: 100%;
        height: 100%;
        background: var(--surface-color);
        border-radius: var(--radius-lg);
        overflow: hidden;
      }
      canvas {
        width: 100%;
        height: 100%;
        cursor: crosshair;
      }
      .tooltip {
        position: absolute;
        background: var(--glass-bg);
        border: 1px solid var(--accent-color);
        border-radius: var(--radius-md);
        padding: 8px 12px;
        pointer-events: none;
        z-index: 10;
        font-size: var(--text-sm);
        min-width: 160px;
        color: var(--text-color);
      }
      .tooltip-line {
        display: flex;
        justify-content: space-between;
        gap: 12px;
        padding: 1px 0;
      }
      .tooltip-label {
        color: var(--text-muted);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ElevationChartComponent implements OnChanges, AfterViewInit {
  @Input() segments: PacingSegment[] = [];
  @Input() highlightedRange: SegmentRange | null = null;
  @Input() showSpeed = false;
  @Input() groupMeanPowers: number[] | null = null;
  @Output() segmentHovered = new EventEmitter<number | null>();
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

  ngOnChanges(changes: SimpleChanges): void {
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

    // Split into two vertical zones
    const dataZoneH = plotH * 0.40;   // top 40%: power/pace
    const gapH = plotH * 0.08;        // 8% gap
    const elevZoneH = plotH * 0.52;   // bottom 52%: elevation
    const dataTop = p.top;
    const elevTop = p.top + dataZoneH + gapH;

    // Clear
    this.ctx.clearRect(0, 0, w, h);

    // Data ranges
    const maxDist = Math.max(...this.segments.map((s) => s.endDistance));
    const elevations = this.segments.map((s) => s.elevation);
    const minElev = Math.min(...elevations) - 20;
    const maxElev = Math.max(...elevations) + 20;

    const xScale = (d: number) => p.left + (d / maxDist) * plotW;
    const yElevScale = (e: number) =>
      elevTop + elevZoneH - ((e - minElev) / (maxElev - minElev)) * elevZoneH;

    // Per-segment elevation fill and line, colored by slope
    const baseY = elevTop + elevZoneH;
    for (let i = 0; i < this.segments.length; i++) {
      const seg = this.segments[i];
      const prevSeg = i > 0 ? this.segments[i - 1] : null;
      const x1 = xScale(seg.startDistance);
      const x2 = xScale(seg.endDistance);
      const y1 = prevSeg ? yElevScale(prevSeg.elevation) : yElevScale(seg.elevation);
      const y2 = yElevScale(seg.elevation);

      const slopeColor = this.getSlopeColor(seg.gradient);
      const slopeFill = this.getSlopeFill(seg.gradient);

      // Fill area under this segment
      this.ctx.beginPath();
      this.ctx.moveTo(x1, y1);
      this.ctx.lineTo(x2, y2);
      this.ctx.lineTo(x2, baseY);
      this.ctx.lineTo(x1, baseY);
      this.ctx.closePath();
      const grad = this.ctx.createLinearGradient(0, Math.min(y1, y2), 0, baseY);
      grad.addColorStop(0, slopeFill);
      grad.addColorStop(1, 'rgba(0, 0, 0, 0.02)');
      this.ctx.fillStyle = grad;
      this.ctx.fill();

      // Elevation line for this segment
      this.ctx.beginPath();
      this.ctx.moveTo(x1, y1);
      this.ctx.lineTo(x2, y2);
      this.ctx.strokeStyle = slopeColor;
      this.ctx.lineWidth = 2.5;
      this.ctx.stroke();
    }

    // Overlay power line (bike) or pace line (run) in top data zone
    const hasPower = this.segments.some((s) => s.targetPower != null);
    const hasPace = this.segments.some((s) => s.targetPace != null);

    const hasSpeed = this.segments.some((s) => s.estimatedSpeedKmh != null && s.estimatedSpeedKmh > 0);

    if (hasPower && !this.showSpeed) {
      // Determine which power values to use for scaling
      const useGroupMean = this.groupMeanPowers != null && this.groupMeanPowers.length === this.segments.length;
      const powerValues = useGroupMean
        ? this.groupMeanPowers!.filter((p) => p > 0)
        : this.segments.filter((s) => s.targetPower != null).map((s) => s.targetPower!);
      const minP = Math.min(...powerValues) * 0.9;
      const maxP = Math.max(...powerValues) * 1.1;
      const yPowerScale = (pw: number) =>
        dataTop + dataZoneH - ((pw - minP) / (maxP - minP)) * dataZoneH;

      if (useGroupMean) {
        // Draw stepped horizontal line per group
        this.ctx.beginPath();
        let started = false;
        for (let i = 0; i < this.segments.length; i++) {
          const seg = this.segments[i];
          const gp = this.groupMeanPowers![i];
          if (gp <= 0) continue;
          const x1 = xScale(seg.startDistance);
          const x2 = xScale(seg.endDistance);
          const y = yPowerScale(gp);
          if (!started) {
            this.ctx.moveTo(x1, y);
            started = true;
          } else {
            this.ctx.lineTo(x1, y);
          }
          this.ctx.lineTo(x2, y);
        }
        this.ctx.strokeStyle = '#60a5fa';
        this.ctx.lineWidth = 2.5;
        this.ctx.stroke();
      } else {
        // Per-segment power line
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
      }

      // Power axis labels (right side, data zone)
      this.ctx.fillStyle = '#60a5fa';
      this.ctx.font = '11px monospace';
      this.ctx.textAlign = 'left';
      this.ctx.fillText(`${Math.round(maxP)}W`, w - p.right + 8, dataTop + 12);
      this.ctx.fillText(`${Math.round(minP)}W`, w - p.right + 8, dataTop + dataZoneH);
    }

    if (this.showSpeed && hasSpeed) {
      const speeds = this.segments
        .filter((s) => s.estimatedSpeedKmh != null && s.estimatedSpeedKmh > 0)
        .map((s) => s.estimatedSpeedKmh!);
      const minS = Math.min(...speeds) * 0.9;
      const maxS = Math.max(...speeds) * 1.1;
      const ySpeedScale = (sp: number) =>
        dataTop + dataZoneH - ((sp - minS) / (maxS - minS)) * dataZoneH;

      this.ctx.beginPath();
      let started = false;
      for (const seg of this.segments) {
        if (seg.estimatedSpeedKmh == null || seg.estimatedSpeedKmh <= 0) continue;
        const x = xScale((seg.startDistance + seg.endDistance) / 2);
        const y = ySpeedScale(seg.estimatedSpeedKmh);
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

      // Speed axis labels (right side, data zone)
      this.ctx.fillStyle = '#34d399';
      this.ctx.font = '11px monospace';
      this.ctx.textAlign = 'left';
      this.ctx.fillText(`${maxS.toFixed(0)} km/h`, w - p.right + 8, dataTop + 12);
      this.ctx.fillText(`${minS.toFixed(0)} km/h`, w - p.right + 8, dataTop + dataZoneH);
    }

    if (hasPace) {
      const paces = this.segments
        .filter((s) => s.targetPace != null)
        .map((s) => this.parsePace(s.targetPace!));
      const minPace = Math.min(...paces) * 0.9;
      const maxPace = Math.max(...paces) * 1.1;
      // Pace: lower is faster, so invert Y
      const yPaceScale = (pc: number) =>
        dataTop + ((pc - minPace) / (maxPace - minPace)) * dataZoneH;

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

      // Pace axis labels (right side, data zone)
      this.ctx.fillStyle = '#34d399';
      this.ctx.font = '11px monospace';
      this.ctx.textAlign = 'left';
      const fastPace = Math.round(minPace);
      const slowPace = Math.round(maxPace);
      this.ctx.fillText(
        `${Math.floor(fastPace / 60)}:${String(fastPace % 60).padStart(2, '0')}`,
        w - p.right + 8,
        dataTop + 12,
      );
      this.ctx.fillText(
        `${Math.floor(slowPace / 60)}:${String(slowPace % 60).padStart(2, '0')}`,
        w - p.right + 8,
        dataTop + dataZoneH,
      );
    }

    // Separator line between data zone and elevation zone
    this.ctx.beginPath();
    this.ctx.moveTo(p.left, dataTop + dataZoneH + gapH / 2);
    this.ctx.lineTo(w - p.right, dataTop + dataZoneH + gapH / 2);
    this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    this.ctx.lineWidth = 1;
    this.ctx.stroke();

    // Fatigue overlay (only on elevation zone)
    if (this.segments.length > 1) {
      const maxFatigue = Math.max(...this.segments.map((s) => s.cumulativeFatigue));
      if (maxFatigue > 0) {
        for (const seg of this.segments) {
          const x1 = xScale(seg.startDistance);
          const x2 = xScale(seg.endDistance);
          const alpha = Math.min((seg.cumulativeFatigue / maxFatigue) * 0.15, 0.15);
          this.ctx.fillStyle = `rgba(239, 68, 68, ${alpha})`;
          this.ctx.fillRect(x1, elevTop, x2 - x1, elevZoneH);
        }
      }
    }

    // Highlighted segment range overlay (spans full plotH)
    if (this.highlightedRange != null) {
      const startSeg = this.segments[this.highlightedRange.start];
      const endSeg = this.segments[Math.min(this.highlightedRange.end, this.segments.length - 1)];
      if (startSeg && endSeg) {
        const hx1 = xScale(startSeg.startDistance);
        const hx2 = xScale(endSeg.endDistance);
        this.ctx.fillStyle = 'rgba(255, 255, 255, 0.12)';
        this.ctx.fillRect(hx1, p.top, hx2 - hx1, plotH);
        this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.4)';
        this.ctx.lineWidth = 1;
        this.ctx.strokeRect(hx1, p.top, hx2 - hx1, plotH);
      }
    }

    // Nutrition markers (in data zone)
    for (const seg of this.segments) {
      if (seg.nutritionSuggestion) {
        const x = xScale((seg.startDistance + seg.endDistance) / 2);
        this.ctx.fillStyle = '#fbbf24';
        this.ctx.beginPath();
        this.ctx.arc(x, dataTop + 12, 5, 0, Math.PI * 2);
        this.ctx.fill();
        this.ctx.fillStyle = '#000';
        this.ctx.font = 'bold 8px monospace';
        this.ctx.textAlign = 'center';
        this.ctx.fillText('N', x, dataTop + 15);
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

    // Y-axis labels (elevation, left side — in elevation zone)
    this.ctx.fillStyle = 'rgba(255, 255, 255, 0.6)';
    this.ctx.textAlign = 'right';
    this.ctx.fillText(`${Math.round(maxElev)}m`, p.left - 8, elevTop + 12);
    this.ctx.fillText(`${Math.round(minElev)}m`, p.left - 8, elevTop + elevZoneH);

    // Legend — slope colors
    this.ctx.font = '10px monospace';
    this.ctx.textAlign = 'left';
    let legendX = p.left;
    const slopeLegend: Array<{ label: string; color: string }> = [
      { label: 'Steep ▲', color: '#dc2626' },
      { label: 'Climb', color: '#f97316' },
      { label: 'Flat', color: '#a0a0a0' },
      { label: 'Descent', color: '#22c55e' },
      { label: 'Steep ▼', color: '#2563eb' },
    ];
    for (const item of slopeLegend) {
      this.ctx.fillStyle = item.color;
      this.ctx.fillRect(legendX, p.top - 16, 12, 4);
      this.ctx.fillStyle = 'rgba(255,255,255,0.7)';
      this.ctx.fillText(item.label, legendX + 16, p.top - 10);
      legendX += this.ctx.measureText(item.label).width + 28;
    }
    if (this.showSpeed && hasSpeed) {
      this.ctx.fillStyle = '#34d399';
      this.ctx.fillText('— Speed', legendX, p.top - 10);
      legendX += 70;
    } else if (hasPower) {
      this.ctx.fillStyle = '#60a5fa';
      const powerLabel = this.groupMeanPowers != null ? '-- Group W' : '— Power';
      this.ctx.fillText(powerLabel, legendX, p.top - 10);
      legendX += this.ctx.measureText(powerLabel).width + 12;
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

    const segIndex = this.segments.findIndex((s) => dist >= s.startDistance && dist <= s.endDistance);
    const seg = segIndex >= 0 ? this.segments[segIndex] : null;
    if (!seg) {
      this.tooltipVisible = false;
      this.segmentHovered.emit(null);
      return;
    }

    this.segmentHovered.emit(segIndex);

    this.tooltipVisible = true;
    this.tooltipX = Math.min(x + 15, rect.width - 180);
    this.tooltipY = Math.max(event.clientY - rect.top - 80, 10);
    this.tooltipData = {
      distance: (seg.startDistance / 1000).toFixed(1) + ' - ' + (seg.endDistance / 1000).toFixed(1) + ' km',
      elevation: Math.round(seg.elevation) + ' m',
      gradient: seg.gradient.toFixed(1) + '%',
      power: seg.targetPower ? seg.targetPower + ' W' : null,
      speed: seg.estimatedSpeedKmh ? seg.estimatedSpeedKmh.toFixed(1) + ' km/h' : null,
      pace: seg.targetPace || null,
      fatigue: (seg.cumulativeFatigue * 100).toFixed(0) + '%',
    };
  }

  onMouseLeave(): void {
    this.tooltipVisible = false;
    this.segmentHovered.emit(null);
  }

  private getSlopeColor(gradient: number): string {
    const g = gradient;
    if (g > 12) return '#7c3aed';       // extreme climb — deep purple
    if (g > 8) return '#c026d3';        // very steep climb — red-purple
    if (g > 6) return '#dc2626';        // steep climb — red
    if (g > 3) return '#ea580c';        // moderate climb — red-orange
    if (g > 1) return '#f97316';        // slight climb — orange
    if (g >= -1) return '#a0a0a0';      // flat — grey
    if (g >= -3) return '#22c55e';      // slight descent — green
    if (g >= -6) return '#0d9488';      // moderate descent — teal
    if (g >= -10) return '#2563eb';     // steep descent — blue
    return '#1e3a5f';                   // very steep descent — dark blue
  }

  private getSlopeFill(gradient: number): string {
    const g = gradient;
    if (g > 12) return 'rgba(124, 58, 237, 0.35)';
    if (g > 8) return 'rgba(192, 38, 211, 0.3)';
    if (g > 6) return 'rgba(220, 38, 38, 0.3)';
    if (g > 3) return 'rgba(234, 88, 12, 0.25)';
    if (g > 1) return 'rgba(249, 115, 22, 0.2)';
    if (g >= -1) return 'rgba(160, 160, 160, 0.15)';
    if (g >= -3) return 'rgba(34, 197, 94, 0.2)';
    if (g >= -6) return 'rgba(13, 148, 136, 0.2)';
    if (g >= -10) return 'rgba(37, 99, 235, 0.25)';
    return 'rgba(30, 58, 95, 0.3)';
  }

  private parsePace(paceStr: string): number {
    const cleaned = paceStr.replace(' /km', '');
    const [min, sec] = cleaned.split(':').map(Number);
    return min * 60 + sec;
  }
}

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
import { PacingSegment, SegmentRange } from '../../../../services/pacing.service';
import { renderElevationChart } from './elevation-chart-renderer';

interface ElevationTooltipData {
  distance?: string;
  elevation?: string;
  gradient?: string;
  power?: string | null;
  speed?: string | null;
  pace?: string | null;
  fatigue?: string;
}

@Component({
  selector: 'app-elevation-chart',
  standalone: true,
  templateUrl: './elevation-chart.component.html',
  styleUrl: './elevation-chart.component.css',
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
  tooltipData: ElevationTooltipData = {};

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

    renderElevationChart({
      ctx: this.ctx,
      segments: this.segments,
      highlightedRange: this.highlightedRange,
      showSpeed: this.showSpeed,
      groupMeanPowers: this.groupMeanPowers,
      width: rect.width,
      height: rect.height,
      padding: this.padding,
    });
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

    const segIndex = this.segments.findIndex(
      (s) => dist >= s.startDistance && dist <= s.endDistance,
    );
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
      distance:
        (seg.startDistance / 1000).toFixed(1) + ' - ' + (seg.endDistance / 1000).toFixed(1) + ' km',
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
}

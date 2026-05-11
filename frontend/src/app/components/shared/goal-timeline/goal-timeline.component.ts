import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  TemplateRef,
  ViewChild,
  contentChild,
  inject,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {SportIconComponent} from '../sport-icon/sport-icon.component';
import {
  AxisTick,
  buildGridLines,
  buildMarkers,
  buildTicks,
  clamp01,
  DAY_MS,
  formatDateShort,
  formatDayMonthYear,
  formatToday,
  GridLine,
  LaneKey,
  MAX_SPAN_MS,
  MIN_SPAN_MS,
  monthLong,
  TimelineItem,
  TimelineMarker,
} from './goal-timeline.utils';

export type {TimelineItem, TimelineMarker, TimelineSport, TimelinePriority} from './goal-timeline.utils';

interface LaneDef {
  key: LaneKey;
  label: string;
  icon: 'RUNNING' | 'CYCLING' | 'SWIMMING' | 'BRICK';
}

const WHEEL_SENSITIVITY = 0.0015;

@Component({
  selector: 'app-goal-timeline',
  standalone: true,
  imports: [CommonModule, SportIconComponent],
  templateUrl: './goal-timeline.component.html',
  styleUrl: './goal-timeline.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalTimelineComponent<T = unknown> implements AfterViewInit, OnDestroy {
  @Input({required: true}) items: TimelineItem<T>[] = [];
  @Input() panelTitle = 'Roadmap';
  @Input() showHeader = true;
  @Input() showLegend = true;
  @Input() emptyLaneLabel = 'Aucun objectif sur cette voie';

  @Output() itemClick = new EventEmitter<TimelineItem<T>>();

  cardFooterTpl = contentChild<TemplateRef<{$implicit: TimelineItem<T>}>>('cardFooter');

  @ViewChild('trackRef', {read: ElementRef}) trackRef?: ElementRef<HTMLElement>;
  @ViewChild('canvasRef', {read: ElementRef}) canvasRef?: ElementRef<HTMLElement>;

  readonly lanes: LaneDef[] = [
    {key: 'run', label: 'RUN', icon: 'RUNNING'},
    {key: 'tri', label: 'TRI', icon: 'BRICK'},
    {key: 'bike', label: 'BIKE', icon: 'CYCLING'},
  ];

  private windowStart: Date;
  private windowEndDate: Date;

  constructor() {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    this.windowStart = start;
    this.windowEndDate = new Date(start.getFullYear() + 1, start.getMonth(), 1);
  }

  isDragging = false;

  private readonly cdr = inject(ChangeDetectorRef);
  private resizeObserver?: ResizeObserver;
  private trackWidthPx = 800;

  private readonly wheelHandler = (e: WheelEvent) => this.onWheel(e);

  private drag: {
    pointerId: number;
    startClientX: number;
    startWindowStartMs: number;
    startTrackWidthPx: number;
    spanMsAtStart: number;
    crossedThreshold: boolean;
  } | null = null;

  private activePointers = new Map<number, {x: number; y: number}>();
  private pinch: {
    startDist: number;
    startMidpointFrac: number;
    startSpanMs: number;
    startWindowStartMs: number;
  } | null = null;

  get windowEnd(): Date {
    return this.windowEndDate;
  }

  private get spanMs(): number {
    return this.windowEndDate.getTime() - this.windowStart.getTime();
  }

  get months(): AxisTick[] {
    return buildTicks(this.windowStart, this.windowEndDate, this.spanMs);
  }

  get gridLines(): GridLine[] {
    return buildGridLines(this.windowStart, this.windowEndDate, this.spanMs);
  }

  get windowStartLabel(): string {
    return this.spanMs >= 150 * DAY_MS ? monthLong(this.windowStart) : formatDayMonthYear(this.windowStart);
  }

  get windowEndLabel(): string {
    return this.spanMs >= 150 * DAY_MS ? monthLong(this.windowEndDate) : formatDayMonthYear(this.windowEndDate);
  }

  get todayX(): number {
    return ((Date.now() - this.windowStart.getTime()) / this.spanMs) * 100;
  }

  get todayInWindow(): boolean {
    const now = Date.now();
    return now >= this.windowStart.getTime() && now <= this.windowEnd.getTime();
  }

  get todayShort(): string {
    return formatToday(new Date());
  }

  get markersByLane(): Record<LaneKey, TimelineMarker<T>[]> {
    return buildMarkers(this.items, this.windowStart, this.windowEnd, this.collisionThresholdPct);
  }

  onMarkerClick(marker: TimelineMarker<T>): void {
    this.itemClick.emit(marker);
  }

  ngAfterViewInit(): void {
    const canvasEl = this.canvasRef?.nativeElement;
    const trackEl = this.trackRef?.nativeElement;
    if (canvasEl) {
      canvasEl.addEventListener('wheel', this.wheelHandler, {passive: false});
    }
    if (typeof ResizeObserver !== 'undefined' && trackEl) {
      this.resizeObserver = new ResizeObserver((entries) => {
        const w = entries[0]?.contentRect.width ?? 0;
        if (w > 0 && Math.abs(w - this.trackWidthPx) > 4) {
          this.trackWidthPx = w;
          this.cdr.markForCheck();
        }
      });
      this.resizeObserver.observe(trackEl);
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.canvasRef?.nativeElement.removeEventListener('wheel', this.wheelHandler);
  }

  private get collisionThresholdPct(): number {
    const cardMaxPx = this.trackWidthPx < 480 ? 110 : 150;
    const buffer = 12;
    return ((cardMaxPx + buffer) / Math.max(this.trackWidthPx, 1)) * 100;
  }

  private onWheel(e: WheelEvent): void {
    e.preventDefault();

    const trackEl = this.trackRef?.nativeElement;
    if (!trackEl) return;
    const rect = trackEl.getBoundingClientRect();
    if (rect.width <= 0) return;

    const frac = clamp01((e.clientX - rect.left) / rect.width);
    const oldSpan = this.spanMs;
    const anchorMs = this.windowStart.getTime() + frac * oldSpan;

    // Exponential zoom keeps trackpad pinches subtle and wheel ticks crisp.
    const factor = Math.exp(e.deltaY * WHEEL_SENSITIVITY);
    const newSpan = Math.max(MIN_SPAN_MS, Math.min(MAX_SPAN_MS, oldSpan * factor));
    if (newSpan === oldSpan) return;

    const newStartMs = anchorMs - frac * newSpan;
    this.windowStart = new Date(newStartMs);
    this.windowEndDate = new Date(newStartMs + newSpan);
    this.cdr.markForCheck();
  }

  onPointerDown(e: PointerEvent): void {
    const target = e.target as Element | null;
    if (target?.closest('.rm-marker')) return;
    this.activePointers.set(e.pointerId, {x: e.clientX, y: e.clientY});

    if (this.activePointers.size === 1) {
      const trackEl = this.trackRef?.nativeElement;
      const widthPx = trackEl?.getBoundingClientRect().width ?? this.trackWidthPx;
      this.drag = {
        pointerId: e.pointerId,
        startClientX: e.clientX,
        startWindowStartMs: this.windowStart.getTime(),
        startTrackWidthPx: widthPx > 0 ? widthPx : this.trackWidthPx,
        spanMsAtStart: this.spanMs,
        crossedThreshold: false,
      };
      try {
        (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
      } catch {
        // Some browsers reject capture on already-captured pointers; safe to ignore.
      }
    } else if (this.activePointers.size === 2) {
      this.drag = null;
      this.pinch = this.startPinch();
    }
  }

  onPointerMove(e: PointerEvent): void {
    if (!this.activePointers.has(e.pointerId)) return;
    this.activePointers.set(e.pointerId, {x: e.clientX, y: e.clientY});

    if (this.pinch && this.activePointers.size === 2) {
      this.applyPinch(e);
      return;
    }

    if (this.drag && e.pointerId === this.drag.pointerId) {
      const dx = e.clientX - this.drag.startClientX;
      if (!this.drag.crossedThreshold && Math.abs(dx) > 4) {
        this.drag.crossedThreshold = true;
        this.isDragging = true;
        this.cdr.markForCheck();
      }
      if (this.drag.crossedThreshold) {
        const ratio = dx / this.drag.startTrackWidthPx;
        const newStartMs = this.drag.startWindowStartMs - ratio * this.drag.spanMsAtStart;
        this.windowStart = new Date(newStartMs);
        this.windowEndDate = new Date(newStartMs + this.drag.spanMsAtStart);
        e.preventDefault();
        this.cdr.markForCheck();
      }
    }
  }

  onPointerUp(e: PointerEvent): void {
    this.activePointers.delete(e.pointerId);

    if (this.drag && e.pointerId === this.drag.pointerId) {
      const wasDragging = this.drag.crossedThreshold;
      try {
        (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
      } catch {
        // Capture may have already been released — safe to ignore.
      }
      this.drag = null;
      this.isDragging = false;
      if (wasDragging) {
        this.swallowNextClick();
      }
      this.cdr.markForCheck();
    }

    if (this.pinch && this.activePointers.size < 2) {
      this.pinch = null;
    }
  }

  onPointerCancel(e: PointerEvent): void {
    this.onPointerUp(e);
  }

  private startPinch(): GoalTimelineComponent<T>['pinch'] {
    const pts = Array.from(this.activePointers.values());
    if (pts.length < 2) return null;
    const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
    const midX = (pts[0].x + pts[1].x) / 2;
    const rect = this.trackRef?.nativeElement.getBoundingClientRect();
    const frac = rect && rect.width > 0 ? clamp01((midX - rect.left) / rect.width) : 0.5;
    return {
      startDist: Math.max(dist, 1),
      startMidpointFrac: frac,
      startSpanMs: this.spanMs,
      startWindowStartMs: this.windowStart.getTime(),
    };
  }

  private applyPinch(e: PointerEvent): void {
    if (!this.pinch) return;
    const pts = Array.from(this.activePointers.values());
    if (pts.length < 2) return;
    const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
    const ratio = Math.max(dist / this.pinch.startDist, 0.0001);
    const effectiveSpan = Math.max(
      MIN_SPAN_MS,
      Math.min(MAX_SPAN_MS, this.pinch.startSpanMs / ratio),
    );

    const anchorMs =
      this.pinch.startWindowStartMs + this.pinch.startMidpointFrac * this.pinch.startSpanMs;
    const newStartMs = anchorMs - this.pinch.startMidpointFrac * effectiveSpan;
    this.windowStart = new Date(newStartMs);
    this.windowEndDate = new Date(newStartMs + effectiveSpan);
    this.cdr.markForCheck();
    e.preventDefault();
  }

  private swallowNextClick(): void {
    const swallow = (ev: Event) => {
      ev.preventDefault();
      ev.stopPropagation();
      ev.stopImmediatePropagation();
    };
    document.addEventListener('click', swallow, {capture: true, once: true});
    // Safety net: if no click fires (drag ended on empty space), remove the listener anyway.
    setTimeout(() => document.removeEventListener('click', swallow, true), 0);
  }

  formatDateShort = formatDateShort;
}

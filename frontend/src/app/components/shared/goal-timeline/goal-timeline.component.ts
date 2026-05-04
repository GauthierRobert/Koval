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

export type TimelineSport =
  | 'CYCLING'
  | 'RUNNING'
  | 'SWIMMING'
  | 'TRIATHLON'
  | 'OTHER'
  | string;
export type TimelinePriority = 'A' | 'B' | 'C';

export interface TimelineItem<T = unknown> {
  id: string;
  title: string;
  sport: TimelineSport;
  raceDate?: string;
  priority?: TimelinePriority;
  isPrimary?: boolean;
  /** Structured race-distance enum from the backend (e.g. TRI_OLYMPIC). When present,
   * drives the short marker label exactly; otherwise we fall back to title-text inference. */
  distanceCategory?: string | null;
  data?: T;
}

type LaneKey = 'run' | 'tri' | 'bike';

interface TimelineMarker<T> extends TimelineItem<T> {
  _x: number;
  _lane: LaneKey;
  _statusKey: 'A' | 'B' | 'C' | 'PASSED';
  _statusLabel: string;
  _short: string;
  _dateShort: string;
  _passed: boolean;
  _above: boolean;
}

interface LaneDef {
  key: LaneKey;
  label: string;
  icon: 'RUNNING' | 'CYCLING' | 'SWIMMING' | 'BRICK';
}

interface AxisTick {
  label: string;
  x: number;
}

interface GridLine {
  x: number;
  type: 'day' | 'week' | 'month';
}

const DAY_MS = 86_400_000;
const MIN_SPAN_MS = 30 * DAY_MS;
const MAX_SPAN_MS = 366 * DAY_MS;
const WEEK_GRID_THRESHOLD = 120 * DAY_MS;
const DAY_GRID_THRESHOLD = 45 * DAY_MS;
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

  // ── Window getters ─────────────────────────────────────────────────

  get windowEnd(): Date {
    return this.windowEndDate;
  }

  private get spanMs(): number {
    return this.windowEndDate.getTime() - this.windowStart.getTime();
  }

  get months(): AxisTick[] {
    return this.buildTicks(this.windowStart, this.windowEndDate, this.spanMs);
  }

  get gridLines(): GridLine[] {
    return this.buildGridLines(this.windowStart, this.windowEndDate, this.spanMs);
  }

  get windowStartLabel(): string {
    return this.spanMs >= 150 * DAY_MS
      ? this.monthLong(this.windowStart)
      : this.formatDayMonthYear(this.windowStart);
  }

  get windowEndLabel(): string {
    return this.spanMs >= 150 * DAY_MS
      ? this.monthLong(this.windowEndDate)
      : this.formatDayMonthYear(this.windowEndDate);
  }

  get todayX(): number {
    const span = this.spanMs;
    const now = Date.now();
    return ((now - this.windowStart.getTime()) / span) * 100;
  }

  get todayInWindow(): boolean {
    const now = Date.now();
    return now >= this.windowStart.getTime() && now <= this.windowEnd.getTime();
  }

  get todayShort(): string {
    return this.formatToday(new Date());
  }

  get markersByLane(): Record<LaneKey, TimelineMarker<T>[]> {
    return this.buildMarkers(this.items, this.windowStart, this.windowEnd);
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
    const canvasEl = this.canvasRef?.nativeElement;
    if (canvasEl) {
      canvasEl.removeEventListener('wheel', this.wheelHandler);
    }
  }

  private get collisionThresholdPct(): number {
    const cardMaxPx = this.trackWidthPx < 480 ? 110 : 150;
    const buffer = 12;
    return ((cardMaxPx + buffer) / Math.max(this.trackWidthPx, 1)) * 100;
  }

  // ── Wheel zoom ─────────────────────────────────────────────────────

  private onWheel(e: WheelEvent): void {
    e.preventDefault();

    const trackEl = this.trackRef?.nativeElement;
    if (!trackEl) return;
    const rect = trackEl.getBoundingClientRect();
    if (rect.width <= 0) return;

    const frac = clamp01((e.clientX - rect.left) / rect.width);
    const oldSpan = this.spanMs;
    const anchorMs = this.windowStart.getTime() + frac * oldSpan;

    // Wheel up (deltaY < 0) → zoom in (smaller span). Exponential scales smoothly with
    // any input — small trackpad deltas are barely noticeable, mouse-wheel ticks are crisp.
    const factor = Math.exp(e.deltaY * WHEEL_SENSITIVITY);
    const newSpan = Math.max(MIN_SPAN_MS, Math.min(MAX_SPAN_MS, oldSpan * factor));
    if (newSpan === oldSpan) return;

    const newStartMs = anchorMs - frac * newSpan;
    this.windowStart = new Date(newStartMs);
    this.windowEndDate = new Date(newStartMs + newSpan);
    this.cdr.markForCheck();
  }

  // ── Pointer / drag / pinch ─────────────────────────────────────────

  onPointerDown(e: PointerEvent): void {
    const target = e.target as Element | null;
    if (target?.closest('.rm-marker')) {
      // Let marker buttons handle their own clicks; don't initiate drag from them.
      return;
    }
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
        // Ignore: capture may have been released already.
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

  // ── Marker building ────────────────────────────────────────────────

  private buildMarkers(
    items: TimelineItem<T>[],
    start: Date,
    end: Date,
  ): Record<LaneKey, TimelineMarker<T>[]> {
    const span = end.getTime() - start.getTime();
    const lanes: Record<LaneKey, TimelineMarker<T>[]> = {run: [], tri: [], bike: []};

    for (const item of items) {
      const d = this.parseDate(item.raceDate);
      if (!d) continue;
      if (d < start || d > end) continue;
      const x = ((d.getTime() - start.getTime()) / span) * 100;
      const lane = this.laneOfSport(item.sport);
      const passed = !this.isUpcoming(d);
      lanes[lane].push({
        ...item,
        _x: x,
        _lane: lane,
        _passed: passed,
        _statusKey: passed ? 'PASSED' : (item.priority ?? 'C'),
        _statusLabel: passed ? '✓' : (item.priority ?? 'C'),
        _short: this.shortLabelForCategory(item.distanceCategory) ?? this.shortLabelFor(item),
        _dateShort: this.formatDateShort(item.raceDate),
        _above: false,
      });
    }

    const threshold = this.collisionThresholdPct;
    for (const key of Object.keys(lanes) as LaneKey[]) {
      lanes[key] = this.resolveOverlap(lanes[key], threshold);
    }
    return lanes;
  }

  private resolveOverlap(
    markers: TimelineMarker<T>[],
    thresholdPct: number,
  ): TimelineMarker<T>[] {
    if (markers.length === 0) return markers;
    const sorted = [...markers].sort((a, b) => a._x - b._x);
    let lastBelow = -Infinity;
    let lastAbove = -Infinity;
    for (const m of sorted) {
      const distBelow = m._x - lastBelow;
      const distAbove = m._x - lastAbove;
      if (distBelow >= thresholdPct) {
        m._above = false;
        lastBelow = m._x;
      } else if (distAbove >= thresholdPct) {
        m._above = true;
        lastAbove = m._x;
      } else {
        m._above = distAbove > distBelow;
        if (m._above) lastAbove = m._x;
        else lastBelow = m._x;
      }
    }
    return sorted;
  }

  private laneOfSport(sport: TimelineSport): LaneKey {
    switch (sport) {
      case 'RUNNING':
        return 'run';
      case 'CYCLING':
        return 'bike';
      case 'TRIATHLON':
      case 'SWIMMING':
        return 'tri';
      default:
        return 'bike';
    }
  }

  /** Short marker label derived from the structured DistanceCategory enum.
   * Returns null when the input is empty or unknown so callers can fall back to title-text inference. */
  private shortLabelForCategory(category: string | null | undefined): string | null {
    if (!category) return null;
    switch (category) {
      // Triathlon
      case 'TRI_PROMO': return 'PRO';
      case 'TRI_SUPER_SPRINT': return 'SSP';
      case 'TRI_SPRINT': return 'SPR';
      case 'TRI_OLYMPIC': return 'OLY';
      case 'TRI_HALF': return '70.3';
      case 'TRI_IRONMAN': return 'IM';
      case 'TRI_ULTRA': return 'XXL';
      case 'TRI_AQUATHLON': return 'AQT';
      case 'TRI_DUATHLON': return 'DUA';
      case 'TRI_AQUABIKE': return 'AQB';
      case 'TRI_CROSS': return 'XTR';
      // Running
      case 'RUN_5K': return '5K';
      case 'RUN_10K': return '10K';
      case 'RUN_HALF_MARATHON': return '21K';
      case 'RUN_MARATHON': return 'MAR';
      case 'RUN_ULTRA': return 'ULT';
      // Cycling
      case 'BIKE_GRAN_FONDO': return 'GRF';
      case 'BIKE_MEDIO_FONDO': return 'MDF';
      case 'BIKE_TT': return 'TT';
      case 'BIKE_ULTRA': return 'ULT';
      // Swimming
      case 'SWIM_1500M': return '1.5K';
      case 'SWIM_5K': return '5K';
      case 'SWIM_10K': return '10K';
      case 'SWIM_MARATHON': return '25K';
      case 'SWIM_ULTRA': return 'ULT';
      default: return null;
    }
  }

  private shortLabelFor(item: TimelineItem<T>): string {
    const text = `${item.title ?? ''}`.toLowerCase();
    if (item.sport === 'RUNNING') {
      if (/marathon|42/.test(text)) return 'MAR';
      if (/semi|21|half/.test(text)) return '21K';
      if (/10\s?k|10km/.test(text)) return '10K';
      if (/5\s?k|5km/.test(text)) return '5K';
      return 'RUN';
    }
    if (item.sport === 'CYCLING') {
      if (/etape|granfondo|gravel|cyclo/.test(text)) return 'GRF';
      return 'BIKE';
    }
    if (item.sport === 'SWIMMING') return 'SWIM';
    if (item.sport === 'TRIATHLON') {
      if (/ironman|140\.6/.test(text)) return 'IM';
      if (/70\.3|half/.test(text)) return '70.3';
      if (/olympic|olympique/.test(text)) return 'OLY';
      if (/sprint/.test(text)) return 'SPR';
      return 'TRI';
    }
    return '—';
  }

  private parseDate(dateStr: string | undefined | null): Date | null {
    if (!dateStr) return null;
    const direct = new Date(dateStr);
    if (!isNaN(direct.getTime())) return direct;
    const padded = new Date(dateStr + 'T00:00:00');
    if (!isNaN(padded.getTime())) return padded;
    return null;
  }

  private isUpcoming(d: Date): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(d);
    target.setHours(0, 0, 0, 0);
    return target.getTime() >= today.getTime();
  }

  // ── Window helpers ─────────────────────────────────────────────────

  private buildGridLines(start: Date, end: Date, spanMs: number): GridLine[] {
    if (spanMs <= 0) return [];
    const startMs = start.getTime();
    const endMs = end.getTime();
    const lines: GridLine[] = [];
    const placed = new Set<number>();

    const place = (t: number, type: GridLine['type']) => {
      if (t < startMs || t > endMs) return;
      if (placed.has(t)) return;
      placed.add(t);
      lines.push({x: ((t - startMs) / spanMs) * 100, type});
    };

    const monthCursor = new Date(start.getFullYear(), start.getMonth(), 1);
    if (monthCursor.getTime() < startMs) {
      monthCursor.setMonth(monthCursor.getMonth() + 1);
    }
    while (monthCursor.getTime() <= endMs) {
      place(monthCursor.getTime(), 'month');
      monthCursor.setMonth(monthCursor.getMonth() + 1);
    }

    if (spanMs <= WEEK_GRID_THRESHOLD) {
      const weekCursor = new Date(start);
      weekCursor.setHours(0, 0, 0, 0);
      const dow = weekCursor.getDay(); // 0=Sun, 1=Mon
      const offset = ((8 - dow) % 7) || 7;
      weekCursor.setDate(weekCursor.getDate() + offset);
      if (weekCursor.getTime() < startMs) {
        weekCursor.setDate(weekCursor.getDate() + 7);
      }
      while (weekCursor.getTime() <= endMs) {
        place(weekCursor.getTime(), 'week');
        weekCursor.setDate(weekCursor.getDate() + 7);
      }
    }

    if (spanMs <= DAY_GRID_THRESHOLD) {
      const dayCursor = new Date(start);
      dayCursor.setHours(0, 0, 0, 0);
      if (dayCursor.getTime() < startMs) {
        dayCursor.setDate(dayCursor.getDate() + 1);
      }
      while (dayCursor.getTime() <= endMs) {
        place(dayCursor.getTime(), 'day');
        dayCursor.setDate(dayCursor.getDate() + 1);
      }
    }

    return lines;
  }

  private buildTicks(start: Date, end: Date, spanMs: number): AxisTick[] {
    if (spanMs <= 0) return [];
    const ticks: AxisTick[] = [];

    // Pick monthly ticks once the window is wider than ~75 days, else weekly.
    if (spanMs >= 75 * DAY_MS) {
      const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
      if (cursor.getTime() < start.getTime()) {
        cursor.setMonth(cursor.getMonth() + 1);
      }
      while (cursor.getTime() <= end.getTime()) {
        const x = ((cursor.getTime() - start.getTime()) / spanMs) * 100;
        if (x >= 0 && x <= 100) {
          ticks.push({label: this.monthShort(cursor), x});
        }
        cursor.setMonth(cursor.getMonth() + 1);
      }
    } else {
      // Weekly ticks for short windows. Anchor to the start so labels are stable while panning.
      for (let i = 0; ; i++) {
        const t = new Date(start.getTime() + i * 7 * DAY_MS);
        if (t.getTime() > end.getTime()) break;
        const x = ((t.getTime() - start.getTime()) / spanMs) * 100;
        ticks.push({label: this.formatDayMonth(t), x});
      }
    }

    return ticks;
  }

  // ── Formatting ─────────────────────────────────────────────────────

  private monthShort(d: Date): string {
    return d
      .toLocaleDateString('fr-FR', {month: 'short'})
      .replace('.', '')
      .toUpperCase()
      .slice(0, 3);
  }

  private monthLong(d: Date): string {
    return d.toLocaleDateString('fr-FR', {month: 'long', year: 'numeric'});
  }

  private formatDayMonth(d: Date): string {
    return `${String(d.getDate()).padStart(2, '0')} ${this.monthShort(d)}`;
  }

  private formatDayMonthYear(d: Date): string {
    return `${this.formatDayMonth(d)} ${d.getFullYear()}`;
  }

  formatDateShort(dateStr: string | undefined | null): string {
    const d = this.parseDate(dateStr);
    if (!d) return 'Date à définir';
    const day = String(d.getDate()).padStart(2, '0');
    const month = this.monthShort(d);
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
  }

  private formatToday(d: Date): string {
    const day = String(d.getDate()).padStart(2, '0');
    const month = this.monthShort(d);
    return `${day} ${month}`;
  }
}

function clamp01(n: number): number {
  return Math.max(0, Math.min(1, n));
}

import {
  AfterViewChecked,
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FitRecord } from '../../../../services/metrics.service';
import { BlockSummary } from '../../../../services/workout-execution.service';
import { ZoneBlock } from '../../../../services/zone';
import {
  computeDriftCurves,
  computeSelectionStats,
  downsample,
  DriftCurves,
  marginsForWidth,
  resolveThemeColors,
  SelectionStats,
  ThemeColors,
} from './fit-timeseries-chart.utils';
import {
  buildTooltipContent,
  HoverContext,
  hoverPrimaryValue,
} from './fit-timeseries-chart-tooltip';
import { drawAll } from './fit-timeseries-chart-renderer';
import {
  attachTouchMoveListeners,
  detachTouchMoveListeners,
  syncObservedCanvases,
  TouchScrubGesture,
} from './fit-timeseries-chart-touch';

@Component({
  selector: 'app-fit-timeseries-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './fit-timeseries-chart.component.html',
  styleUrl: './fit-timeseries-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FitTimeseriesChartComponent
  implements OnChanges, AfterViewInit, AfterViewChecked, OnDestroy
{
  @Input() records: FitRecord[] = [];
  @Input() ftp: number | null = null;
  @Input() sportType = 'CYCLING';
  @Input() blockSummaries: BlockSummary[] = [];
  @Input() blockColors: string[] = [];
  @Input() zoneBlocks: ZoneBlock[] = [];
  /** When non-empty, zones not in this set render dimmed (grey). Empty/null = no filter. */
  @Input() zoneFilters: Set<string> | null = null;
  @Input() showToggles = true;
  @Input() showXAxis = true;
  @Input() compact = false;
  @Input() showTooltip = true;
  @Input() showPrimary = true;
  @Input() showHR = true;
  @Input() showCadence = false;
  @Input() showBlocks = false;
  @Input() showSpeed = true;
  @Input() showDrift = true;
  @Input() showElevation = true;
  /** When false, the cycling speed sub-chart is shown only as a fallback when no power is present. */
  @Input() showSpeedWithPower = true;
  /** Enable mouse drag-to-select range stats (desktop only). Off by default. */
  @Input() enableBrush = false;

  /** Drives the crosshair from an outside source (e.g. hovering the route map). */
  private _externalHoverIdx: number | null = null;
  @Input() set externalHoverIdx(v: number | null) {
    const n = v ?? null;
    if (n === this._externalHoverIdx) return;
    this._externalHoverIdx = n;
    // Only redraw for externally-driven hovers; internal hover redraws on its own.
    if (this.ready && this.hoverIdx === null) this.drawAll();
  }

  /** Emits when the brush selection settles (mouse up after drag) or is cleared. */
  @Output() selectionChange = new EventEmitter<{ startIdx: number; endIdx: number } | null>();

  /** Emits the live selection stats so a sibling panel can render them outside the chart. */
  @Output() selectionStatsChange = new EventEmitter<SelectionStats | null>();

  /** Emits the hovered record index (or null) so a sibling view can mirror the cursor. */
  @Output() hoverIndexChange = new EventEmitter<number | null>();
  private lastEmittedHoverIdx: number | null = null;
  private emitHoverIdx(idx: number | null): void {
    if (idx === this.lastEmittedHoverIdx) return;
    this.lastEmittedHoverIdx = idx;
    this.hoverIndexChange.emit(idx);
  }

  @ViewChild('stack') stackRef!: ElementRef<HTMLDivElement>;
  @ViewChild('primaryCanvas') pRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('speedCanvas') spRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('hrCanvas') hrRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cadCanvas') cadRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('driftCanvas') driftRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('elevCanvas') elRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('xCanvas') xRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('ttEl') ttElRef?: ElementRef<HTMLDivElement>;

  hoverIdx: number | null = null;
  ttX = 0;
  ttY = 0;
  ttShift = 0;
  ttHeader = '';
  ttRows: Array<{ label: string; value: string; color: string }> = [];
  private ttShiftRaf: number | null = null;

  // ── Brush selection (desktop only) ────────────────────────────────────
  private isDesktop = false;
  private dragging = false;
  selectionStartIdx: number | null = null;
  selectionEndIdx: number | null = null;
  selectionStats: SelectionStats | null = null;
  selectionLeftPx = 0;
  selectionWidthPx = 0;
  private prevSelectionStartIdx: number | null = null;
  private prevSelectionEndIdx: number | null = null;
  private readonly windowMouseUpListener = () => this.onWindowMouseUp();
  private readonly keyDownListener = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && this.selectionStartIdx !== null) {
      this.clearSelection();
      this.cdr.detectChanges();
    }
  };

  // ── Horizontal zoom (shared across every stacked panel) ───────────────
  // Visible time window in elapsed seconds from session start. null = full range.
  // Ctrl/⌘ + wheel zooms on desktop; two-finger pinch zooms on touch.
  viewStartSec: number | null = null;
  viewEndSec: number | null = null;
  private readonly wheelListener = (e: WheelEvent) => this.handleWheel(e);
  private pinch: {
    startDist: number;
    startSpanSec: number;
    anchorSec: number;
  } | null = null;

  // ── Pan (Ctrl/⌘ + click-drag, desktop only) ───────────────────────────
  private panning = false;
  private panStartClientX = 0;
  private panStartViewStart = 0;
  private panStartViewEnd = 0;

  _hasElevation = false;
  _hasPower = false;
  _hasDrift = false;
  private _driftCurves: DriftCurves | null = null;
  private _primaryMax = 0;
  private _primaryMin = 0;
  /** Downsampled records (30s buckets) used for raw-mode line drawing to avoid canvas perf issues. */
  private _ds: FitRecord[] = [];
  private ready = false;
  private readonly zone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private resizeObserver: ResizeObserver | null = null;
  private observedCanvases = new Set<HTMLCanvasElement>();

  private theme: ThemeColors = resolveThemeColors();

  get isSwimming(): boolean {
    return this.sportType === 'SWIMMING';
  }
  get isCycling(): boolean {
    return this.sportType === 'CYCLING';
  }
  get primaryLabel(): string {
    if (this.sportType === 'CYCLING') return 'Power';
    if (this.isSwimming) return 'Pace';
    return 'Speed';
  }
  /** Show a dedicated speed sub-chart below the primary panel — cycling only. */
  get showSpeedPanel(): boolean {
    return (
      this.isCycling &&
      this.showSpeed &&
      this._hasSpeed &&
      (this.showSpeedWithPower || !this._hasPower)
    );
  }
  _hasSpeed = false;
  _hasCadence = false;

  ngAfterViewInit(): void {
    this.ready = true;
    this.isDesktop =
      typeof window !== 'undefined' &&
      window.matchMedia('(hover: hover) and (pointer: fine)').matches;
    this.resizeObserver = new ResizeObserver(() => {
      if (this.ready) {
        this.drawAll();
        this.recomputeSelectionRect();
      }
    });
    this.syncObservedCanvases();
    this.registerTouchMoveListeners();
    if (this.isDesktop) {
      // mouseup also ends a Ctrl-drag pan, which is enabled independently of brush.
      window.addEventListener('mouseup', this.windowMouseUpListener);
      if (this.enableBrush) {
        window.addEventListener('keydown', this.keyDownListener);
      }
    }
    // Wheel zoom must be a non-passive listener so we can preventDefault the
    // browser's ctrl+wheel page zoom / scroll. Register outside Angular.
    this.zone.runOutsideAngular(() =>
      this.stackRef?.nativeElement.addEventListener('wheel', this.wheelListener, {
        passive: false,
      }),
    );
    this.drawAll();
    // Re-draw after the first paint so canvases pick up their flex-resolved size.
    requestAnimationFrame(() => this.drawAll());
  }

  ngAfterViewChecked(): void {
    // Conditional canvases (@if showHR / showCadence / _hasElevation) mount/unmount;
    // re-attach the ResizeObserver so size changes on any visible canvas trigger a redraw.
    if (this.ready) {
      this.syncObservedCanvases();
      this.registerTouchMoveListeners();
    }
  }

  private syncObservedCanvases(): void {
    if (!this.resizeObserver) return;
    syncObservedCanvases(this.resizeObserver, this.observedCanvases, [
      this.pRef?.nativeElement,
      this.spRef?.nativeElement,
      this.hrRef?.nativeElement,
      this.cadRef?.nativeElement,
      this.driftRef?.nativeElement,
      this.elRef?.nativeElement,
      this.xRef?.nativeElement,
    ]);
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.unregisterTouchMoveListeners();
    this.stackRef?.nativeElement.removeEventListener('wheel', this.wheelListener);
    if (this.ttShiftRaf !== null) cancelAnimationFrame(this.ttShiftRaf);
    if (this.isDesktop) {
      window.removeEventListener('mouseup', this.windowMouseUpListener);
      if (this.enableBrush) {
        window.removeEventListener('keydown', this.keyDownListener);
      }
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    // New session loaded: drop any zoom window so the new ride shows in full,
    // and discard any stale brush selection from the previous session.
    if (changes['records']) {
      this.viewStartSec = null;
      this.viewEndSec = null;
      if (this.selectionStartIdx !== null) this.clearSelection();
    }
    this.updateHasElevation();
    this.updateHasPower();
    this.updateHasSpeed();
    this.updateHasCadence();
    this.updateDrift();
    this._ds = downsample(this.records, this.pickBucketSec(this.records));
    // Cycling without any power data: don't render the power chart or the
    // block overlays (zone or planned). Fall back to the speed sub-chart.
    if (this.isCycling && !this._hasPower) {
      this.showPrimary = false;
      this.showBlocks = false;
    }
    if (!this._hasCadence) {
      this.showCadence = false;
    }
    if (this.ready) setTimeout(() => this.drawAll(), 0);
  }

  private pickBucketSec(records: FitRecord[]): number {
    if (records.length < 2) return 1;
    const durationSec = records[records.length - 1].timestamp - records[0].timestamp;
    const hours = Math.floor(durationSec / 3600);
    return Math.min(30, Math.max(1, hours));
  }

  onHover(event: MouseEvent): void {
    const canvas = event.target as HTMLCanvasElement;
    this.isTouchHover = false;
    if (this.panning) {
      this.applyPanFromClientX(event.clientX);
      this.hoverIdx = null;
      this.ttRows = [];
      return;
    }
    if (this.dragging) {
      // Drag-to-select: extend the range; suppress tooltip while drag is active.
      this.computeHoverAt(canvas, event.clientX, event.clientY, /*silent*/ true);
      if (this.hoverIdx !== null) {
        this.selectionEndIdx = this.hoverIdx;
        this.updateSelectionStats();
        this.recomputeSelectionRect();
      }
      this.hoverIdx = null;
      this.ttRows = [];
      this.drawAll();
      return;
    }
    if (!this.showTooltip) return;
    this.computeHoverAt(canvas, event.clientX, event.clientY);
  }

  onMouseDown(event: MouseEvent): void {
    if (!this.isDesktop) return;
    if (event.button !== 0) return;
    if (!this.records.length) return;
    // Ctrl/⌘ + drag = pan the visible window (independent of brush mode).
    if (event.ctrlKey || event.metaKey) {
      const full = this.fullDurationSec();
      if (full <= 0) return;
      this.panning = true;
      this.panStartClientX = event.clientX;
      this.panStartViewStart = this.viewStartSec ?? 0;
      this.panStartViewEnd = this.viewEndSec ?? full;
      this.hoverIdx = null;
      this.ttRows = [];
      event.preventDefault();
      return;
    }
    if (!this.enableBrush) return;
    const canvas = event.currentTarget as HTMLCanvasElement;
    this.computeHoverAt(canvas, event.clientX, event.clientY, /*silent*/ true);
    if (this.hoverIdx === null) return;
    this.dragging = true;
    this.prevSelectionStartIdx = this.selectionStartIdx;
    this.prevSelectionEndIdx = this.selectionEndIdx;
    this.selectionStartIdx = this.hoverIdx;
    this.selectionEndIdx = this.hoverIdx;
    this.hoverIdx = null;
    this.ttRows = [];
    this.updateSelectionStats();
    this.recomputeSelectionRect();
    this.drawAll();
    event.preventDefault();
  }

  private applyPanFromClientX(clientX: number): void {
    const geom = this.plotGeometry();
    if (!geom || geom.cW <= 0) return;
    const span = this.panStartViewEnd - this.panStartViewStart;
    if (span <= 0) return;
    const dxPx = clientX - this.panStartClientX;
    const shift = -(dxPx / geom.cW) * span;
    this.applyView(this.panStartViewStart + shift, this.panStartViewEnd + shift);
    this.zone.run(() => this.cdr.detectChanges());
  }

  private onWindowMouseUp(): void {
    if (this.panning) {
      this.panning = false;
      return;
    }
    if (!this.dragging) return;
    this.dragging = false;
    const moved = Math.abs((this.selectionEndIdx ?? 0) - (this.selectionStartIdx ?? 0));
    if (moved === 0) {
      // Click without drag — clear any pinned selection.
      this.clearSelection();
      this.drawAll();
      this.cdr.detectChanges();
      return;
    }
    this.updateSelectionStats();
    this.recomputeSelectionRect();
    this.selectionChange.emit({
      startIdx: Math.min(this.selectionStartIdx!, this.selectionEndIdx!),
      endIdx: Math.max(this.selectionStartIdx!, this.selectionEndIdx!),
    });
    this.cdr.detectChanges();
  }

  clearSelection(): void {
    const had = this.selectionStartIdx !== null;
    this.selectionStartIdx = null;
    this.selectionEndIdx = null;
    this.selectionStats = null;
    this.selectionLeftPx = 0;
    this.selectionWidthPx = 0;
    if (had) {
      this.selectionChange.emit(null);
      this.selectionStatsChange.emit(null);
    }
  }

  private updateSelectionStats(): void {
    if (this.selectionStartIdx === null || this.selectionEndIdx === null) {
      this.selectionStats = null;
    } else {
      this.selectionStats = computeSelectionStats(
        this.records,
        this.selectionStartIdx,
        this.selectionEndIdx,
      );
    }
    this.selectionStatsChange.emit(this.selectionStats);
  }

  private recomputeSelectionRect(): void {
    if (
      this.selectionStartIdx === null ||
      this.selectionEndIdx === null ||
      !this.records.length ||
      !this.stackRef?.nativeElement
    ) {
      this.selectionLeftPx = 0;
      this.selectionWidthPx = 0;
      return;
    }
    const W = this.stackRef.nativeElement.offsetWidth;
    const { mL, mR } = marginsForWidth(W);
    const cW = W - mL - mR;
    const t0 = this.records[0].timestamp;
    const fullSec = this.records[this.records.length - 1].timestamp - t0 || this.records.length;
    const vStart = this.viewStartSec ?? 0;
    const vEnd = this.viewEndSec ?? fullSec;
    const span = vEnd - vStart || fullSec;
    const a = Math.min(this.selectionStartIdx, this.selectionEndIdx);
    const b = Math.max(this.selectionStartIdx, this.selectionEndIdx);
    const xOfT = (sec: number) => mL + ((sec - vStart) / span) * cW;
    // Clamp to the visible plot so a partly off-screen selection doesn't bleed
    // into the axis margins while zoomed.
    const left = mL,
      right = mL + cW;
    const xA = Math.max(left, Math.min(right, xOfT(this.records[a].timestamp - t0)));
    const xB = Math.max(left, Math.min(right, xOfT(this.records[b].timestamp - t0)));
    this.selectionLeftPx = xA;
    this.selectionWidthPx = Math.max(0, xB - xA);
  }

  // ── Horizontal zoom (Ctrl/⌘ + wheel · two-finger pinch) ───────────────
  get isZoomed(): boolean {
    return this.viewStartSec !== null || this.viewEndSec !== null;
  }

  private fullDurationSec(): number {
    const n = this.records.length;
    if (n < 2) return 0;
    return this.records[n - 1].timestamp - this.records[0].timestamp || n;
  }

  /** Smallest window we allow, so the view always keeps a handful of samples. */
  private minSpanSec(full: number): number {
    return Math.min(full, Math.max(5, full / 2000));
  }

  /** Apply a [start, end] window (elapsed seconds), clamped to the session.
   *  Snaps back to the full range (null/null) once the window spans the whole ride. */
  private applyView(startSec: number, endSec: number): void {
    const full = this.fullDurationSec();
    if (full <= 0) return;
    const span = Math.min(full, Math.max(this.minSpanSec(full), endSec - startSec));
    const start = Math.max(0, Math.min(startSec, full - span));
    const end = start + span;
    if (start <= 0.5 && end >= full - 0.5) {
      this.viewStartSec = null;
      this.viewEndSec = null;
    } else {
      this.viewStartSec = start;
      this.viewEndSec = end;
    }
    this.drawAll();
    this.recomputeSelectionRect();
  }

  resetZoom(): void {
    if (!this.isZoomed) return;
    this.viewStartSec = null;
    this.viewEndSec = null;
    this.drawAll();
    this.recomputeSelectionRect();
    this.cdr.detectChanges();
  }

  /** Plot geometry in stack-local CSS pixels. Canvases are full-width children
   *  of the stack, so its width matches every canvas. */
  private plotGeometry(clientX?: number): { mL: number; cW: number; cursorX: number } | null {
    const el = this.stackRef?.nativeElement;
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    const { mL, mR } = marginsForWidth(rect.width);
    const cW = rect.width - mL - mR;
    const cursorX = clientX != null ? clientX - rect.left : mL + cW / 2;
    return { mL, cW, cursorX };
  }

  private handleWheel(e: WheelEvent): void {
    if (this.records.length < 2) return;
    const full = this.fullDurationSec();
    if (full <= 0) return;
    const zoomGesture = e.ctrlKey || e.metaKey;
    const panGesture = !zoomGesture && (e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY));
    if (!zoomGesture && !panGesture) return;
    e.preventDefault();

    const geom = this.plotGeometry(e.clientX);
    if (!geom || geom.cW <= 0) return;
    const { mL, cW } = geom;
    const vStart = this.viewStartSec ?? 0;
    const vEnd = this.viewEndSec ?? full;
    const span = vEnd - vStart;

    if (zoomGesture) {
      const fx = Math.max(0, Math.min(1, (geom.cursorX - mL) / cW));
      const anchorSec = vStart + fx * span;
      const factor = e.deltaY < 0 ? 0.82 : 1.22;
      const newSpan = Math.max(this.minSpanSec(full), Math.min(full, span * factor));
      const newStart = anchorSec - fx * newSpan;
      this.applyView(newStart, newStart + newSpan);
    } else {
      const delta = e.shiftKey ? e.deltaY || e.deltaX : e.deltaX;
      const shift = (delta / cW) * span;
      this.applyView(vStart + shift, vEnd + shift);
    }
    this.zone.run(() => this.cdr.detectChanges());
  }

  private touchDist(a: Touch, b: Touch): number {
    return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
  }

  private beginPinch(event: TouchEvent): void {
    const t1 = event.touches[0];
    const t2 = event.touches[1];
    if (!t1 || !t2 || this.records.length < 2) return;
    const full = this.fullDurationSec();
    const geom = this.plotGeometry();
    if (!geom || full <= 0 || geom.cW <= 0) return;
    const rect = this.stackRef.nativeElement.getBoundingClientRect();
    const midX = (t1.clientX + t2.clientX) / 2 - rect.left;
    const vStart = this.viewStartSec ?? 0;
    const vEnd = this.viewEndSec ?? full;
    const span = vEnd - vStart;
    const fx = Math.max(0, Math.min(1, (midX - geom.mL) / geom.cW));
    this.pinch = {
      startDist: this.touchDist(t1, t2),
      startSpanSec: span,
      anchorSec: vStart + fx * span,
    };
    this.onMouseLeave();
  }

  private handlePinchMove(event: TouchEvent): void {
    if (!this.pinch) {
      this.beginPinch(event);
      return;
    }
    const t1 = event.touches[0];
    const t2 = event.touches[1];
    if (!t1 || !t2) return;
    if (event.cancelable) event.preventDefault();
    const full = this.fullDurationSec();
    const geom = this.plotGeometry();
    if (!geom || full <= 0 || geom.cW <= 0 || this.pinch.startDist <= 0) return;
    const rect = this.stackRef.nativeElement.getBoundingClientRect();
    const midX = (t1.clientX + t2.clientX) / 2 - rect.left;
    const scale = this.touchDist(t1, t2) / this.pinch.startDist;
    const newSpan = Math.max(
      this.minSpanSec(full),
      Math.min(full, this.pinch.startSpanSec / (scale || 1)),
    );
    const fx = Math.max(0, Math.min(1, (midX - geom.mL) / geom.cW));
    const newStart = this.pinch.anchorSec - fx * newSpan;
    this.zone.run(() => {
      this.applyView(newStart, newStart + newSpan);
      this.cdr.detectChanges();
    });
  }

  private isTouchHover = false;
  private gesture = new TouchScrubGesture();
  private readonly touchMoveListener = (e: TouchEvent) => this.handleTouchMove(e);
  /** Long-press (1s hold) arms the tooltip on touch; a plain horizontal drag pans when zoomed. */
  private static readonly LONG_PRESS_MS = 1000;
  private longPressTimer: ReturnType<typeof setTimeout> | null = null;
  private lastTouchX = 0;
  private lastTouchY = 0;
  // Touch pan anchors (same shape as the desktop Ctrl-drag pan).
  private touchPan: { startClientX: number; startViewStart: number; startViewEnd: number } | null =
    null;

  onTouchStart(event: TouchEvent): void {
    if (event.touches.length >= 2) {
      this.cancelLongPress();
      this.beginPinch(event);
      return;
    }
    const touch = event.touches[0];
    if (!touch) return;
    const canvas = event.currentTarget as HTMLCanvasElement;
    this.gesture.begin(canvas, touch.clientX, touch.clientY);
    this.lastTouchX = touch.clientX;
    this.lastTouchY = touch.clientY;
    // Tooltip only appears after a 1s hold; until then a horizontal drag pans (when zoomed).
    if (this.showTooltip) this.scheduleLongPress(canvas);
  }

  onTouchEnd(event?: TouchEvent): void {
    // End the pinch once fewer than two fingers remain on the surface.
    if (!event || event.touches.length < 2) this.pinch = null;
    this.cancelLongPress();
    this.touchPan = null;
    this.gesture.end();
    this.isTouchHover = false;
    this.onMouseLeave();
  }

  private scheduleLongPress(canvas: HTMLCanvasElement): void {
    this.cancelLongPress();
    this.longPressTimer = setTimeout(() => {
      this.longPressTimer = null;
      // Only arm the tooltip if the finger hasn't committed to a pan/scroll.
      if (!this.gesture.forceScrub()) return;
      navigator.vibrate?.(15);
      this.zone.run(() => {
        this.isTouchHover = true;
        this.computeHoverAt(canvas, this.lastTouchX, this.lastTouchY);
        this.cdr.detectChanges();
      });
    }, FitTimeseriesChartComponent.LONG_PRESS_MS);
  }

  private cancelLongPress(): void {
    if (this.longPressTimer !== null) {
      clearTimeout(this.longPressTimer);
      this.longPressTimer = null;
    }
  }

  private applyTouchPan(clientX: number): void {
    if (!this.touchPan) {
      const full = this.fullDurationSec();
      if (full <= 0) return;
      this.touchPan = {
        startClientX: clientX,
        startViewStart: this.viewStartSec ?? 0,
        startViewEnd: this.viewEndSec ?? full,
      };
      return;
    }
    const geom = this.plotGeometry();
    if (!geom || geom.cW <= 0) return;
    const span = this.touchPan.startViewEnd - this.touchPan.startViewStart;
    if (span <= 0) return;
    const shift = -((clientX - this.touchPan.startClientX) / geom.cW) * span;
    this.zone.run(() => {
      this.applyView(this.touchPan!.startViewStart + shift, this.touchPan!.startViewEnd + shift);
      this.cdr.detectChanges();
    });
  }

  private handleTouchMove(event: TouchEvent): void {
    if (event.touches.length >= 2) {
      this.cancelLongPress();
      this.handlePinchMove(event);
      return;
    }
    if (this.pinch) return;
    const touch = event.touches[0];
    const canvas = this.gesture.activeCanvas;
    if (!touch || !canvas) return;
    this.lastTouchX = touch.clientX;
    this.lastTouchY = touch.clientY;

    const resolved = this.gesture.classify(touch.clientX, touch.clientY, this.isZoomed);
    if (resolved === 'undecided') return; // jitter — keep waiting for the long-press
    if (resolved !== 'scrub') this.cancelLongPress();

    if (resolved === 'pan') {
      if (event.cancelable) event.preventDefault();
      this.applyTouchPan(touch.clientX);
      return;
    }
    if (resolved === 'scroll') {
      this.onMouseLeave();
      this.cdr.detectChanges();
      return;
    }

    // Scrub (long-press armed): the finger drives the tooltip crosshair.
    this.isTouchHover = true;
    this.computeHoverAt(canvas, touch.clientX, touch.clientY);
    this.cdr.detectChanges();
    if (event.cancelable) event.preventDefault();
  }

  private touchCanvases(): (HTMLCanvasElement | undefined)[] {
    return [
      this.pRef?.nativeElement,
      this.spRef?.nativeElement,
      this.hrRef?.nativeElement,
      this.cadRef?.nativeElement,
      this.driftRef?.nativeElement,
      this.elRef?.nativeElement,
    ];
  }

  private registerTouchMoveListeners(): void {
    this.zone.runOutsideAngular(() =>
      attachTouchMoveListeners(this.touchCanvases(), this.touchMoveListener),
    );
  }

  private unregisterTouchMoveListeners(): void {
    detachTouchMoveListeners(this.touchCanvases(), this.touchMoveListener);
  }

  private computeHoverAt(
    canvas: HTMLCanvasElement | null,
    clientX: number,
    clientY: number,
    silent = false,
  ): void {
    if (!canvas || this.records.length < 2) return;

    const rect = canvas.getBoundingClientRect();
    // Coordinates are in CSS pixels; initCanvas() draws in CSS pixels via setTransform(dpr,...).
    const cssW = rect.width;
    const x = clientX - rect.left;

    const n = this.records.length;
    const t0 = this.records[0].timestamp;
    const fullSec = this.records[n - 1].timestamp - t0 || n;
    const vStart = this.viewStartSec ?? 0;
    const vEnd = this.viewEndSec ?? fullSec;
    const span = vEnd - vStart || fullSec;
    const { mL, mR } = marginsForWidth(cssW);
    const cW = cssW - mL - mR;

    const targetT = t0 + vStart + ((x - mL) / cW) * span;
    let lo = 0,
      hi = n - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (this.records[mid].timestamp < targetT) lo = mid + 1;
      else hi = mid;
    }
    const idx =
      lo > 0 &&
      Math.abs(this.records[lo - 1].timestamp - targetT) <
        Math.abs(this.records[lo].timestamp - targetT)
        ? lo - 1
        : lo;
    this.hoverIdx = idx;

    // Tooltip position relative to stack: anchor horizontally to the scrub line
    // (the matched sample's x in stack-local coordinates) and vertically just above
    // the power curve/block at the hovered sample.
    const stackRect = this.stackRef.nativeElement.getBoundingClientRect();
    const stackW = stackRect.width;
    const sampleX = mL + ((this.records[idx].timestamp - t0 - vStart) / span) * cW;
    const lineXInStack = rect.left - stackRect.left + sampleX;
    // The .tt element uses transform: translate(-50%, -100%), so ttX/ttY mark the
    // anchor point (bottom-center of the tooltip). Clamp X to keep it on-screen.
    this.ttX = Math.max(8, Math.min(stackW - 8, lineXInStack));
    if (this.showPrimary && this.pRef?.nativeElement && this._primaryMax > 0) {
      // Anchor 10px above the bar/curve top at this sample, matching drawPrimary's yOf.
      const pRect = this.pRef.nativeElement.getBoundingClientRect();
      const mT = 6,
        mB = 6;
      const chartH = pRect.height - mT - mB;
      const val = hoverPrimaryValue(this.hoverContext()!, idx, t0);
      const yLocal = this.isSwimming
        ? mT + chartH * ((val - this._primaryMin) / (this._primaryMax - this._primaryMin || 1))
        : mT + chartH * (1 - val / this._primaryMax);
      this.ttY = pRect.top - stackRect.top + yLocal - 50;
    } else {
      const anchorRect =
        this.showPrimary && this.pRef?.nativeElement
          ? this.pRef.nativeElement.getBoundingClientRect()
          : rect;
      this.ttY = anchorRect.top - stackRect.top - 8;
    }

    if (silent) return;
    this.buildTooltip();
    this.drawAll();
    this.scheduleTooltipShiftUpdate();
    this.emitHoverIdx(this.hoverIdx);
  }

  onMouseLeave(): void {
    this.hoverIdx = null;
    this.emitHoverIdx(null);
    this.ttRows = [];
    this.ttShift = 0;
    if (this.ttShiftRaf !== null) {
      cancelAnimationFrame(this.ttShiftRaf);
      this.ttShiftRaf = null;
    }
    this.drawAll();
  }

  /**
   * Anchor sits at the scrub line, but on mobile the tooltip body can extend past
   * the chart edges. Measure the rendered tooltip and shift it horizontally so
   * both edges stay inside the stack while the anchor visually stays put.
   */
  private scheduleTooltipShiftUpdate(): void {
    if (this.ttShiftRaf !== null) cancelAnimationFrame(this.ttShiftRaf);
    this.ttShiftRaf = requestAnimationFrame(() => {
      this.ttShiftRaf = null;
      const tt = this.ttElRef?.nativeElement;
      if (!tt || this.hoverIdx === null) return;
      const stackW = this.stackRef.nativeElement.getBoundingClientRect().width;
      const halfW = tt.offsetWidth / 2;
      const margin = 8;
      const leftEdge = this.ttX - halfW;
      const rightEdge = this.ttX + halfW;
      let shift = 0;
      if (leftEdge < margin) shift = margin - leftEdge;
      else if (rightEdge > stackW - margin) shift = stackW - margin - rightEdge;
      if (this.ttShift !== shift) {
        this.ttShift = shift;
        this.cdr.detectChanges();
      }
    });
  }

  private updateHasPower(): void {
    this._hasPower = this.records.some((r) => r.power > 0);
  }

  private updateHasSpeed(): void {
    this._hasSpeed = this.records.some((r) => r.speed > 0);
  }

  private updateHasCadence(): void {
    this._hasCadence = this.records.some((r) => r.cadence > 0);
  }

  private updateDrift(): void {
    this._driftCurves = computeDriftCurves(this.records, this.sportType);
    this._hasDrift = this._driftCurves !== null;
    if (!this._hasDrift) this.showDrift = false;
  }

  private updateHasElevation(): void {
    if (!this.records.length) {
      this._hasElevation = false;
      return;
    }
    const vals = this.records.filter((r) => r.elevation != null).map((r) => r.elevation!);
    this._hasElevation = vals.length >= 2 && vals.some((v) => v !== vals[0]);
  }

  drawAll(): void {
    this.theme = resolveThemeColors();
    const result = drawAll(
      {
        primary: this.pRef?.nativeElement,
        speed: this.spRef?.nativeElement,
        hr: this.hrRef?.nativeElement,
        cad: this.cadRef?.nativeElement,
        drift: this.driftRef?.nativeElement,
        elev: this.elRef?.nativeElement,
        xAxis: this.xRef?.nativeElement,
      },
      {
        records: this.records,
        downsampled: this._ds,
        sportType: this.sportType,
        ftp: this.ftp,
        zoneBlocks: this.zoneBlocks,
        zoneFilters: this.zoneFilters,
        blockSummaries: this.blockSummaries,
        blockColors: this.blockColors,
        showBlocks: this.showBlocks,
        showPrimary: this.showPrimary,
        showSpeed: this.showSpeedPanel,
        showHR: this.showHR,
        showCadence: this.showCadence,
        showDrift: this._hasDrift && this.showDrift,
        hasElevation: this._hasElevation && this.showElevation,
        driftCurves: this._driftCurves,
        hoverIdx: this.hoverIdx ?? this._externalHoverIdx,
        theme: this.theme,
        viewStartSec: this.viewStartSec,
        viewEndSec: this.viewEndSec,
      },
    );
    this._primaryMin = result.primaryMin;
    this._primaryMax = result.primaryMax;
  }

  // ── Tooltip / hover delegates ─────────────────────────────────────────

  private hoverContext(): HoverContext | null {
    if (this.hoverIdx === null) return null;
    return {
      records: this.records,
      downsampled: this._ds,
      sportType: this.sportType,
      zoneBlocks: this.zoneBlocks,
      blockSummaries: this.blockSummaries,
      showBlocks: this.showBlocks,
      primaryMax: this._primaryMax,
      showPrimary: this.showPrimary,
      showHR: this.showHR,
      showCadence: this.showCadence,
      hasElevation: this._hasElevation && this.showElevation,
      showDrift: this._hasDrift && this.showDrift,
      driftCurves: this._driftCurves,
      accentHex: this.theme.accentHex,
      hoverIdx: this.hoverIdx,
    };
  }

  private buildTooltip(): void {
    const ctx = this.hoverContext();
    if (!ctx) {
      this.ttRows = [];
      return;
    }
    const content = buildTooltipContent(ctx);
    this.ttHeader = content.header;
    this.ttRows = content.rows;
  }
}

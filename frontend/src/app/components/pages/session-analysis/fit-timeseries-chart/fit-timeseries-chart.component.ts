import {
  AfterViewChecked,
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  inject,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  ViewChild
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FitRecord} from '../../../../services/metrics.service';
import {BlockSummary} from '../../../../services/workout-execution.service';
import {ZoneBlock} from '../../../../services/zone';
import {
    downsample,
    marginsForWidth,
    resolveThemeColors,
    ThemeColors,
} from './fit-timeseries-chart.utils';
import {
    buildTooltipContent,
    HoverContext,
    hoverCadence,
    hoverEfficiency,
    hoverHR,
    hoverPrimaryValue,
} from './fit-timeseries-chart-tooltip';
import {drawAll} from './fit-timeseries-chart-renderer';

@Component({
    selector: 'app-fit-timeseries-chart',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="chart-wrap" [class.compact]="compact">
            @if (showToggles) {
                <div class="chart-toggles">
                    <button class="toggle-btn" [class.active]="showPrimary" (click)="toggle('showPrimary')">
                        <span class="dot power"></span> {{ primaryLabel }}
                    </button>
                    <button class="toggle-btn" [class.active]="showHR" (click)="toggle('showHR')">
                        <span class="dot hr"></span> Heart Rate
                    </button>
                    <button class="toggle-btn" [class.active]="showCadence" (click)="toggle('showCadence')">
                        <span class="dot cad"></span> Cadence
                    </button>
                    @if (sportType !== 'SWIMMING') {
                        <button class="toggle-btn" [class.active]="showEfficiency" (click)="toggle('showEfficiency')">
                            <span class="dot eff"></span> Efficiency
                        </button>
                    }
                    @if (zoneBlocks.length > 0 || blockSummaries.length > 0) {
                        <span class="toggle-sep"></span>
                        <button class="toggle-btn" [class.active]="showBlocks" (click)="toggle('showBlocks')">
                            <span class="dot blocks"></span> Blocks
                        </button>
                    }
                </div>
            }
            <div class="charts-stack" #stack (mouseleave)="onMouseLeave()">
                @if (showPrimary) {
                    <canvas #primaryCanvas class="mc primary-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouchStart($event)"
                        (touchend)="onTouchEnd()"
                        (touchcancel)="onTouchEnd()"></canvas>
                }
                @if (showHR) {
                    <canvas #hrCanvas class="mc hr-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouchStart($event)"
                        (touchend)="onTouchEnd()"
                        (touchcancel)="onTouchEnd()"></canvas>
                }
                @if (showEfficiency) {
                    <canvas #effCanvas class="mc eff-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouchStart($event)"
                        (touchend)="onTouchEnd()"
                        (touchcancel)="onTouchEnd()"></canvas>
                }
                @if (showCadence) {
                    <canvas #cadCanvas class="mc cad-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouchStart($event)"
                        (touchend)="onTouchEnd()"
                        (touchcancel)="onTouchEnd()"></canvas>
                }
                @if (_hasElevation) {
                    <canvas #elevCanvas class="mc elev-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouchStart($event)"
                        (touchend)="onTouchEnd()"
                        (touchcancel)="onTouchEnd()"></canvas>
                }
                @if (showXAxis) {
                    <canvas #xCanvas class="mc xaxis-h"></canvas>
                }
                @if (hoverIdx !== null && showTooltip) {
                    <div #ttEl class="tt"
                        [style.left.px]="ttX"
                        [style.top.px]="ttY"
                        [style.transform]="'translate(calc(-50% + ' + ttShift + 'px), -100%)'">
                        <div class="tt-hdr">{{ ttHeader }}</div>
                        <div class="tt-sep"></div>
                        @for (r of ttRows; track r.label) {
                            <div class="tt-row">
                                <span class="tt-lbl">{{ r.label }}</span>
                                <span class="tt-val" [style.color]="r.color">{{ r.value }}</span>
                            </div>
                        }
                    </div>
                }
            </div>
        </div>
    `,
    styles: [`
        .chart-wrap { display: flex; flex-direction: column; gap: 8px; }
        .chart-toggles { display: flex; gap: 8px; padding: 0 var(--page-padding); flex-wrap: wrap; }
        .toggle-btn {
            display: flex; align-items: center; gap: 5px;
            background: var(--overlay-5);
            border: none;
            color: var(--text-muted);
            padding: 4px 10px; border-radius: 6px;
            font-size: 10px; font-weight: 600; letter-spacing: 0.05em;
            cursor: pointer; transition: all 0.15s;
        }
        .toggle-btn.active {
            background: var(--overlay-10, rgba(0,0,0,0.1));
            color: var(--text-color);
        }
        .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
        .dot.power { background: var(--accent-color, #ff9d00); }
        .dot.hr { background: #e74c3c; }
        .dot.cad { background: #3b82f6; }
        .dot.eff { background: #a855f7; }
        .dot.blocks { background: #2ecc71; }
        .toggle-sep { width: 1px; height: 16px; background: var(--overlay-10); margin: 0 2px; }
        .charts-stack { position: relative; display: flex; flex-direction: column; gap: 4px; min-height: 0; }
        .mc {
            width: 100%; display: block; cursor: crosshair; min-height: 0;
            touch-action: pan-y;
            -webkit-user-select: none; user-select: none;
            -webkit-touch-callout: none;
        }
        .primary-h { flex: 6 1 0; min-height: 250px; }
        .hr-h { flex: 2.8 1 0; min-height: 100px; }
        .cad-h { flex: 2.8 1 0; min-height: 100px; }
        .eff-h { flex: 2 1 0; min-height: 80px; }
        .elev-h { flex: 1.4 1 0; min-height: 80px; }
        .xaxis-h { flex: 0 0 22px; height: 22px; cursor: default; }
        .tt {
            position: absolute; z-index: 10; pointer-events: none;
            background: var(--surface-card, rgba(32, 34, 52, 0.97));
            border: 1px solid var(--glass-border, rgba(255,255,255,0.22));
            border-radius: 8px; padding: 8px 10px;
            min-width: 120px; white-space: nowrap;
        }
        .tt-hdr { color: var(--text-80); font: 9px monospace; }
        .tt-sep { height: 1px; background: var(--overlay-15); margin: 5px 0; }
        .tt-row { display: flex; justify-content: space-between; gap: 16px; line-height: 17px; }
        .tt-lbl { color: var(--text-muted); font: 9px monospace; }
        .tt-val { font: bold 10px monospace; text-align: right; }

        @media (max-width: 768px) {
            .chart-toggles { gap: 4px; }
            .toggle-btn { padding: 3px 8px; font-size: 9px; }
            .primary-h { min-height: 200px; }
            .hr-h { min-height: 120px; }
            .cad-h { min-height: 120px; }
            .eff-h { min-height: 80px; }
            .elev-h { min-height: 70px; }
        }

        :host { display: block; }
        .chart-wrap.compact {
            gap: 0;
            height: 100%;
        }
        .chart-wrap.compact .charts-stack { flex: 1 1 0; min-height: 0; }
        .chart-wrap.compact .chart-toggles { padding: 0; }
        .chart-wrap.compact .primary-h { min-height: 0; flex: 1 1 0; }
        .chart-wrap.compact .hr-h { min-height: 0; flex: 1 1 0; }
        .chart-wrap.compact .cad-h { min-height: 0; flex: 1 1 0; }
        .chart-wrap.compact .eff-h { min-height: 0; flex: 1 1 0; }
        .chart-wrap.compact .elev-h { min-height: 0; flex: 1 1 0; }
        .chart-wrap.compact .xaxis-h { flex: 0 0 16px; height: 16px; }
        .chart-wrap.compact .mc { cursor: default; }
    `],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FitTimeseriesChartComponent implements OnChanges, AfterViewInit, AfterViewChecked, OnDestroy {
    @Input() records: FitRecord[] = [];
    @Input() ftp: number | null = null;
    @Input() sportType = 'CYCLING';
    @Input() blockSummaries: BlockSummary[] = [];
    @Input() blockColors: string[] = [];
    @Input() zoneBlocks: ZoneBlock[] = [];
    @Input() showToggles = true;
    @Input() showXAxis = true;
    @Input() compact = false;
    @Input() showTooltip = true;
    @Input() showPrimary = true;
    @Input() showHR = true;
    @Input() showCadence = false;
    @Input() showEfficiency = false;
    @Input() showBlocks = false;

    @ViewChild('stack') stackRef!: ElementRef<HTMLDivElement>;
    @ViewChild('primaryCanvas') pRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('hrCanvas') hrRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('cadCanvas') cadRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('effCanvas') effRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('elevCanvas') elRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('xCanvas') xRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('ttEl') ttElRef?: ElementRef<HTMLDivElement>;

    private blocksDefaultApplied = false;

    hoverIdx: number | null = null;
    ttX = 0;
    ttY = 0;
    ttShift = 0;
    ttHeader = '';
    ttRows: Array<{ label: string; value: string; color: string }> = [];
    private ttShiftRaf: number | null = null;

    _hasElevation = false;
    private _primaryMax = 0;
    private _primaryMin = 0;
    /** Downsampled records (30s buckets) used for raw-mode line drawing to avoid canvas perf issues. */
    private _ds: FitRecord[] = [];
    /** Per-bucket efficiency values populated by the renderer; tooltip uses the nearest bucket. */
    private _effSmoothed: number[] = [];
    private ready = false;
    private readonly zone = inject(NgZone);
    private readonly cdr = inject(ChangeDetectorRef);
    private resizeObserver: ResizeObserver | null = null;
    private observedCanvases = new Set<HTMLCanvasElement>();

    private theme: ThemeColors = resolveThemeColors();

    get isSwimming(): boolean { return this.sportType === 'SWIMMING'; }
    get primaryLabel(): string {
        if (this.sportType === 'CYCLING') return 'Power';
        if (this.isSwimming) return 'Pace';
        return 'Speed';
    }

    ngAfterViewInit(): void {
        this.ready = true;
        this.resizeObserver = new ResizeObserver(() => {
            if (this.ready) this.drawAll();
        });
        this.syncObservedCanvases();
        this.registerTouchMoveListeners();
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
        const current: HTMLCanvasElement[] = [
            this.pRef?.nativeElement,
            this.hrRef?.nativeElement,
            this.effRef?.nativeElement,
            this.cadRef?.nativeElement,
            this.elRef?.nativeElement,
            this.xRef?.nativeElement,
        ].filter((c): c is HTMLCanvasElement => !!c);

        const currentSet = new Set(current);
        for (const c of this.observedCanvases) {
            if (!currentSet.has(c)) {
                this.resizeObserver.unobserve(c);
                this.observedCanvases.delete(c);
            }
        }
        for (const c of current) {
            if (!this.observedCanvases.has(c)) {
                this.resizeObserver.observe(c);
                this.observedCanvases.add(c);
            }
        }
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        this.unregisterTouchMoveListeners();
        if (this.ttShiftRaf !== null) cancelAnimationFrame(this.ttShiftRaf);
    }

    ngOnChanges(): void {
        this.updateHasElevation();
        this._ds = downsample(this.records, 30);
        if (!this.blocksDefaultApplied && (this.zoneBlocks.length > 0 || this.blockSummaries.length > 0)) {
            this.showBlocks = true;
            this.blocksDefaultApplied = true;
        }
        if (this.ready) setTimeout(() => this.drawAll(), 0);
    }

    toggle(prop: 'showPrimary' | 'showHR' | 'showCadence' | 'showEfficiency' | 'showBlocks'): void {
        this[prop] = !this[prop];
        setTimeout(() => this.drawAll(), 0);
    }

    onHover(event: MouseEvent): void {
        if (!this.showTooltip) return;
        const canvas = event.target as HTMLCanvasElement;
        this.isTouchHover = false;
        this.computeHoverAt(canvas, event.clientX, event.clientY);
    }

    private touchStartX = 0;
    private touchStartY = 0;
    private touchGesture: 'undecided' | 'scrub' | 'scroll' = 'undecided';
    private touchCanvas: HTMLCanvasElement | null = null;
    private isTouchHover = false;
    private readonly touchMoveListener = (e: TouchEvent) => this.handleTouchMove(e);

    onTouchStart(event: TouchEvent): void {
        if (!this.showTooltip) return;
        const touch = event.touches[0];
        if (!touch) return;
        const canvas = event.currentTarget as HTMLCanvasElement;
        this.touchStartX = touch.clientX;
        this.touchStartY = touch.clientY;
        this.touchGesture = 'undecided';
        this.touchCanvas = canvas;
        this.isTouchHover = true;
        // Show tooltip immediately on tap.
        this.computeHoverAt(canvas, touch.clientX, touch.clientY);
    }

    onTouchEnd(): void {
        this.touchGesture = 'undecided';
        this.touchCanvas = null;
        this.isTouchHover = false;
        this.onMouseLeave();
    }

    private handleTouchMove(event: TouchEvent): void {
        const touch = event.touches[0];
        if (!touch || !this.touchCanvas) return;

        if (this.touchGesture === 'undecided') {
            const dx = touch.clientX - this.touchStartX;
            const dy = touch.clientY - this.touchStartY;
            const adx = Math.abs(dx);
            const ady = Math.abs(dy);
            if (adx >= 4 || ady >= 4) {
                if (adx >= ady) {
                    this.touchGesture = 'scrub';
                } else {
                    this.touchGesture = 'scroll';
                    this.onMouseLeave();
                    this.cdr.detectChanges();
                    return;
                }
            }
        }

        // touchmove is registered via native addEventListener (passive: false) to allow
        // preventDefault during scrub. The app is zoneless, so NgZone.run() does NOT
        // trigger CD — we must call detectChanges() explicitly so ttX/ttY/ttRows render.
        // Update on every event (even while undecided) so tiny movements still track.
        this.isTouchHover = true;
        this.computeHoverAt(this.touchCanvas, touch.clientX, touch.clientY);
        this.cdr.detectChanges();

        if (this.touchGesture === 'scrub' && event.cancelable) {
            event.preventDefault();
        }
    }

    private registerTouchMoveListeners(): void {
        const canvases: (HTMLCanvasElement | undefined)[] = [
            this.pRef?.nativeElement,
            this.hrRef?.nativeElement,
            this.effRef?.nativeElement,
            this.cadRef?.nativeElement,
            this.elRef?.nativeElement,
        ];
        // Outside the zone: we re-enter explicitly in handleTouchMove only when scrubbing.
        this.zone.runOutsideAngular(() => {
            for (const c of canvases) {
                if (!c) continue;
                c.removeEventListener('touchmove', this.touchMoveListener);
                c.addEventListener('touchmove', this.touchMoveListener, { passive: false });
            }
        });
    }

    private unregisterTouchMoveListeners(): void {
        const canvases: (HTMLCanvasElement | undefined)[] = [
            this.pRef?.nativeElement,
            this.hrRef?.nativeElement,
            this.effRef?.nativeElement,
            this.cadRef?.nativeElement,
            this.elRef?.nativeElement,
        ];
        for (const c of canvases) {
            c?.removeEventListener('touchmove', this.touchMoveListener);
        }
    }

    private computeHoverAt(canvas: HTMLCanvasElement | null, clientX: number, clientY: number): void {
        if (!canvas || this.records.length < 2) return;

        const rect = canvas.getBoundingClientRect();
        // Coordinates are in CSS pixels; initCanvas() draws in CSS pixels via setTransform(dpr,...).
        const cssW = rect.width;
        const x = clientX - rect.left;

        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const { mL, mR } = marginsForWidth(cssW);
        const cW = cssW - mL - mR;

        const targetT = t0 + ((x - mL) / cW) * totalSec;
        let lo = 0, hi = n - 1;
        while (lo < hi) {
            const mid = (lo + hi) >> 1;
            if (this.records[mid].timestamp < targetT) lo = mid + 1;
            else hi = mid;
        }
        const idx = (lo > 0 && Math.abs(this.records[lo - 1].timestamp - targetT) < Math.abs(this.records[lo].timestamp - targetT))
            ? lo - 1 : lo;

        // Tooltip position relative to stack: anchor horizontally to the scrub line
        // (the matched sample's x in stack-local coordinates) and vertically just above
        // the power curve/block at the hovered sample.
        const stackRect = this.stackRef.nativeElement.getBoundingClientRect();
        const stackW = stackRect.width;
        const sampleX = mL + ((this.records[idx].timestamp - t0) / totalSec) * cW;
        const lineXInStack = (rect.left - stackRect.left) + sampleX;
        // The .tt element uses transform: translate(-50%, -100%), so ttX/ttY mark the
        // anchor point (bottom-center of the tooltip). Clamp X to keep it on-screen.
        this.ttX = Math.max(8, Math.min(stackW - 8, lineXInStack));
        if (this.showPrimary && this.pRef?.nativeElement && this._primaryMax > 0) {
            // Anchor 10px above the bar/curve top at this sample, matching drawPrimary's yOf.
            const pRect = this.pRef.nativeElement.getBoundingClientRect();
            const mT = 6, mB = 6;
            const chartH = pRect.height - mT - mB;
            const val = this.hoverPrimaryValue(idx, t0);
            const yLocal = this.isSwimming
                ? mT + chartH * ((val - this._primaryMin) / (this._primaryMax - this._primaryMin || 1))
                : mT + chartH * (1 - val / this._primaryMax);
            this.ttY = (pRect.top - stackRect.top) + yLocal - 50;
        } else {
            const anchorRect = (this.showPrimary && this.pRef?.nativeElement)
                ? this.pRef.nativeElement.getBoundingClientRect()
                : rect;
            this.ttY = (anchorRect.top - stackRect.top) - 8;
        }

        this.hoverIdx = idx;
        this.buildTooltip();
        this.drawAll();
        this.scheduleTooltipShiftUpdate();
    }

    onMouseLeave(): void {
        this.hoverIdx = null;
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
            else if (rightEdge > stackW - margin) shift = (stackW - margin) - rightEdge;
            if (this.ttShift !== shift) {
                this.ttShift = shift;
                this.cdr.detectChanges();
            }
        });
    }

    private updateHasElevation(): void {
        if (!this.records.length) { this._hasElevation = false; return; }
        const vals = this.records.filter(r => r.elevation != null).map(r => r.elevation!);
        this._hasElevation = vals.length >= 2 && vals.some(v => v !== vals[0]);
    }

    drawAll(): void {
        this.theme = resolveThemeColors();
        const result = drawAll(
            {
                primary: this.pRef?.nativeElement,
                hr: this.hrRef?.nativeElement,
                cad: this.cadRef?.nativeElement,
                eff: this.effRef?.nativeElement,
                elev: this.elRef?.nativeElement,
                xAxis: this.xRef?.nativeElement,
            },
            {
                records: this.records,
                downsampled: this._ds,
                sportType: this.sportType,
                ftp: this.ftp,
                zoneBlocks: this.zoneBlocks,
                blockSummaries: this.blockSummaries,
                blockColors: this.blockColors,
                showBlocks: this.showBlocks,
                showPrimary: this.showPrimary,
                showHR: this.showHR,
                showCadence: this.showCadence,
                showEfficiency: this.showEfficiency,
                hasElevation: this._hasElevation,
                hoverIdx: this.hoverIdx,
                theme: this.theme,
            },
        );
        this._primaryMin = result.primaryMin;
        this._primaryMax = result.primaryMax;
        this._effSmoothed = result.effSmoothed;
    }

    // ── Tooltip / hover delegates ─────────────────────────────────────────

    private hoverContext(): HoverContext | null {
        if (this.hoverIdx === null) return null;
        return {
            records: this.records,
            downsampled: this._ds,
            effSmoothed: this._effSmoothed,
            sportType: this.sportType,
            zoneBlocks: this.zoneBlocks,
            blockSummaries: this.blockSummaries,
            showBlocks: this.showBlocks,
            primaryMax: this._primaryMax,
            showPrimary: this.showPrimary,
            showHR: this.showHR,
            showCadence: this.showCadence,
            showEfficiency: this.showEfficiency,
            hasElevation: this._hasElevation,
            accentHex: this.theme.accentHex,
            hoverIdx: this.hoverIdx,
        };
    }

    private buildTooltip(): void {
        const ctx = this.hoverContext();
        if (!ctx) { this.ttRows = []; return; }
        const content = buildTooltipContent(ctx);
        this.ttHeader = content.header;
        this.ttRows = content.rows;
    }

    private hoverPrimaryValue(idx: number, t0: number): number {
        return hoverPrimaryValue(this.hoverContext()!, idx, t0);
    }

    private hoverHR(idx: number, t0: number): number {
        return hoverHR(this.hoverContext()!, idx, t0);
    }

    private hoverCadence(idx: number, t0: number): number {
        return hoverCadence(this.hoverContext()!, idx, t0);
    }

    private hoverEfficiency(idx: number, t0: number): number {
        return hoverEfficiency(this.hoverContext()!, idx, t0);
    }
}

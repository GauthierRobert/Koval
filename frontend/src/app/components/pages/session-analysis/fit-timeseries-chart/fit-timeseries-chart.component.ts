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
    hoverPrimaryValue,
} from './fit-timeseries-chart-tooltip';
import {drawAll} from './fit-timeseries-chart-renderer';
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
        syncObservedCanvases(this.resizeObserver, this.observedCanvases, [
            this.pRef?.nativeElement,
            this.hrRef?.nativeElement,
            this.effRef?.nativeElement,
            this.cadRef?.nativeElement,
            this.elRef?.nativeElement,
            this.xRef?.nativeElement,
        ]);
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

    private isTouchHover = false;
    private gesture = new TouchScrubGesture();
    private readonly touchMoveListener = (e: TouchEvent) => this.handleTouchMove(e);

    onTouchStart(event: TouchEvent): void {
        if (!this.showTooltip) return;
        const touch = event.touches[0];
        if (!touch) return;
        const canvas = event.currentTarget as HTMLCanvasElement;
        this.gesture.begin(canvas, touch.clientX, touch.clientY);
        this.isTouchHover = true;
        this.computeHoverAt(canvas, touch.clientX, touch.clientY);
    }

    onTouchEnd(): void {
        this.gesture.end();
        this.isTouchHover = false;
        this.onMouseLeave();
    }

    private handleTouchMove(event: TouchEvent): void {
        const touch = event.touches[0];
        const canvas = this.gesture.activeCanvas;
        if (!touch || !canvas) return;

        const resolved = this.gesture.classify(touch.clientX, touch.clientY);
        if (resolved === 'scroll') {
            this.onMouseLeave();
            this.cdr.detectChanges();
            return;
        }

        this.isTouchHover = true;
        this.computeHoverAt(canvas, touch.clientX, touch.clientY);
        this.cdr.detectChanges();

        if (resolved === 'scrub' && event.cancelable) {
            event.preventDefault();
        }
    }

    private touchCanvases(): (HTMLCanvasElement | undefined)[] {
        return [
            this.pRef?.nativeElement,
            this.hrRef?.nativeElement,
            this.effRef?.nativeElement,
            this.cadRef?.nativeElement,
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
            const val = hoverPrimaryValue(this.hoverContext()!, idx, t0);
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
}

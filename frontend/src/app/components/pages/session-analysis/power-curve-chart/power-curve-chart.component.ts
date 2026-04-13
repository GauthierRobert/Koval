import {
    afterNextRender,
    ChangeDetectionStrategy,
    Component,
    computed,
    effect,
    ElementRef,
    inject,
    Injector,
    input,
    NgZone,
    OnDestroy,
    signal,
    viewChild,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {of, switchMap} from 'rxjs';
import {catchError, map, startWith} from 'rxjs/operators';
import {AnalyticsService, DURATION_LABELS} from '../../../../services/analytics.service';

interface CurvePoint {
    duration: number;
    label: string;
    power: number;
}

interface CurveState {
    loading: boolean;
    points: CurvePoint[];
}

/**
 * Mean-maximal power curve for a single completed session.
 *
 * <p>Renders a canvas line chart over a logarithmic duration axis. Behavior mirrors
 * {@link FitTimeseriesChartComponent}: hover/touch scrubbing snaps to the nearest point,
 * an absolutely-positioned tooltip follows the cursor, and a native (passive: false)
 * touchmove listener supports finger scrub on mobile.
 *
 * <p>The chart is hidden entirely when the backend returns no data — non-cycling sessions,
 * sessions without a FIT file, or FIT files without a power channel.
 */
@Component({
    selector: 'app-power-curve-chart',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        @if (hasData()) {
            <div class="power-curve glass">
                <div class="pc-header">
                    <div class="section-label">{{ 'SESSION_ANALYSIS.POWER_CURVE_TITLE' | translate }}</div>
                </div>
                <div class="pc-stack" #stack (mouseleave)="onMouseLeave()">
                    <canvas #canvas class="pc-canvas"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouchStart($event)"
                        (touchend)="onTouchEnd()"
                        (touchcancel)="onTouchEnd()"></canvas>
                    @if (hoverIdx() !== null) {
                        <div class="tt" [style.left.px]="ttX()" [style.top.px]="ttY()">
                            <div class="tt-hdr">{{ ttHeader() }}</div>
                            <div class="tt-sep"></div>
                            <div class="tt-row">
                                <span class="tt-lbl">{{ 'SESSION_ANALYSIS.STAT_AVG_POWER' | translate }}</span>
                                <span class="tt-val">{{ ttPower() }}W</span>
                            </div>
                        </div>
                    }
                </div>
            </div>
        }
    `,
    styles: [`
        .power-curve { padding: var(--space-md) var(--page-padding); }
        .pc-header {
            display: flex; align-items: center; justify-content: space-between;
            margin-bottom: var(--space-md);
        }
        .pc-stack { position: relative; min-height: 0; }
        .pc-canvas {
            width: 100%; display: block; cursor: crosshair;
            height: 220px;
            touch-action: pan-y;
            -webkit-user-select: none; user-select: none;
            -webkit-touch-callout: none;
        }
        .tt {
            position: absolute; z-index: 10; pointer-events: none;
            background: var(--surface-card, rgba(32, 34, 52, 0.97));
            border: 1px solid var(--glass-border, rgba(255,255,255,0.22));
            border-radius: 8px; padding: 8px 10px;
            min-width: 110px; white-space: nowrap;
            transform: translate(var(--tt-tx, -50%), -100%);
        }
        .tt-hdr { color: var(--text-80); font: 9px monospace; }
        .tt-sep { height: 1px; background: var(--overlay-15); margin: 5px 0; }
        .tt-row { display: flex; justify-content: space-between; gap: 16px; line-height: 17px; }
        .tt-lbl { color: var(--text-muted); font: 9px monospace; }
        .tt-val { font: bold 10px monospace; text-align: right; color: var(--accent-color); }

        @media (max-width: 768px) {
            .power-curve { padding: var(--space-sm) var(--space-md); }
            .pc-canvas { height: 180px; }
        }
    `],
})
export class PowerCurveChartComponent implements OnDestroy {
    private analytics = inject(AnalyticsService);
    private zone = inject(NgZone);
    private injector = inject(Injector);

    /** Session ID to fetch curve from backend. Ignored when `data` is provided. */
    sessionId = input<string | null>(null);
    sportType = input<string | null>(null);
    /** Direct data injection — when provided, the component renders this instead of fetching. */
    data = input<Record<number, number> | null>(null);

    canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('canvas');
    stackRef = viewChild<ElementRef<HTMLDivElement>>('stack');

    // ── Hover/tooltip state (signals → auto-tracked in template, zoneless-safe) ──
    hoverIdx = signal<number | null>(null);
    ttX = signal(0);
    ttY = signal(0);
    ttHeader = signal('');
    ttPower = signal('');

    // ── Data ─────────────────────────────────────────────────────────────
    private state$ = toObservable(computed(() => ({
        id: this.sessionId(),
        sport: this.sportType(),
        direct: this.data(),
    }))).pipe(
        switchMap(({id, sport, direct}) => {
            // Direct data takes precedence over session fetch.
            if (direct) {
                return of<CurveState>({loading: false, points: this.toPoints(direct)});
            }
            if (!id || (sport && sport !== 'CYCLING')) {
                return of<CurveState>({loading: false, points: []});
            }
            return this.analytics.getSessionPowerCurve(id).pipe(
                map((data): CurveState => ({loading: false, points: this.toPoints(data)})),
                catchError(() => of<CurveState>({loading: false, points: []})),
                startWith<CurveState>({loading: true, points: []}),
            );
        }),
    );
    state = toSignal(this.state$, {initialValue: {loading: false, points: []} as CurveState});
    hasData = computed(() => this.state().points.length > 0);

    // ── Theme ────────────────────────────────────────────────────────────
    private accentRgb: [number, number, number] = [255, 157, 0];
    private accentHex = 'rgb(255,157,0)';
    private gridColor = 'rgba(255,255,255,0.10)';
    private textColor = 'rgba(255,255,255,0.55)';
    private crosshair = 'rgba(255,255,255,0.30)';

    private resizeObserver: ResizeObserver | null = null;
    private observedCanvas: HTMLCanvasElement | null = null;
    private readonly touchMoveListener = (e: TouchEvent) => this.handleTouchMove(e);
    private touchCanvas: HTMLCanvasElement | null = null;
    private touchStartX = 0;
    private touchStartY = 0;
    private touchGesture: 'undecided' | 'scrub' | 'scroll' = 'undecided';

    constructor() {
        // Re-run drawing whenever the data or the canvas reference changes (the canvas
        // is wrapped in @if (hasData()), so it appears/disappears with the state).
        effect(() => {
            const canvas = this.canvasRef()?.nativeElement;
            const points = this.state().points;
            if (!canvas || points.length === 0) {
                if (this.observedCanvas) {
                    this.resizeObserver?.unobserve(this.observedCanvas);
                    this.observedCanvas.removeEventListener('touchmove', this.touchMoveListener);
                    this.observedCanvas = null;
                }
                this.hoverIdx.set(null);
                return;
            }
            if (this.observedCanvas !== canvas) {
                if (this.observedCanvas) {
                    this.resizeObserver?.unobserve(this.observedCanvas);
                    this.observedCanvas.removeEventListener('touchmove', this.touchMoveListener);
                }
                if (!this.resizeObserver) {
                    this.resizeObserver = new ResizeObserver(() => this.draw());
                }
                this.resizeObserver.observe(canvas);
                this.zone.runOutsideAngular(() =>
                    canvas.addEventListener('touchmove', this.touchMoveListener, {passive: false}),
                );
                this.observedCanvas = canvas;
            }
            this.resolveThemeColors();
            this.draw();
            // Second tick after layout settles (flex child sizing).
            afterNextRender(() => this.draw(), {injector: this.injector});
        });
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        if (this.observedCanvas) {
            this.observedCanvas.removeEventListener('touchmove', this.touchMoveListener);
        }
    }

    // ── Mouse / touch handlers ──────────────────────────────────────────

    onHover(event: MouseEvent): void {
        const canvas = event.currentTarget as HTMLCanvasElement;
        this.computeHoverAt(canvas, event.clientX);
    }

    onMouseLeave(): void {
        this.hoverIdx.set(null);
        this.draw();
    }

    onTouchStart(event: TouchEvent): void {
        const touch = event.touches[0];
        if (!touch) return;
        const canvas = event.currentTarget as HTMLCanvasElement;
        this.touchStartX = touch.clientX;
        this.touchStartY = touch.clientY;
        this.touchGesture = 'undecided';
        this.touchCanvas = canvas;
        this.computeHoverAt(canvas, touch.clientX);
    }

    onTouchEnd(): void {
        this.touchGesture = 'undecided';
        this.touchCanvas = null;
        this.onMouseLeave();
    }

    private handleTouchMove(event: TouchEvent): void {
        const touch = event.touches[0];
        if (!touch || !this.touchCanvas) return;

        if (this.touchGesture === 'undecided') {
            const dx = Math.abs(touch.clientX - this.touchStartX);
            const dy = Math.abs(touch.clientY - this.touchStartY);
            if (dx >= 4 || dy >= 4) {
                if (dx >= dy) {
                    this.touchGesture = 'scrub';
                } else {
                    this.touchGesture = 'scroll';
                    this.onMouseLeave();
                    return;
                }
            }
        }

        // Native listener fires outside Angular's event system. Signals already trigger
        // CD when set, so no detectChanges() needed — just update state.
        this.computeHoverAt(this.touchCanvas, touch.clientX);

        if (this.touchGesture === 'scrub' && event.cancelable) {
            event.preventDefault();
        }
    }

    // ── Hover computation ───────────────────────────────────────────────

    private computeHoverAt(canvas: HTMLCanvasElement, clientX: number): void {
        const points = this.state().points;
        if (points.length < 2) return;

        const rect = canvas.getBoundingClientRect();
        const cssW = rect.width;
        const x = clientX - rect.left;
        const {mL, mR} = this.margins(cssW);
        const cW = Math.max(1, cssW - mL - mR);

        // Snap to nearest point in pixel space (log-scaled X).
        let bestIdx = 0;
        let bestDist = Infinity;
        for (let i = 0; i < points.length; i++) {
            const px = mL + this.xRatio(points[i].duration, points) * cW;
            const d = Math.abs(px - x);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }

        const p = points[bestIdx];
        const sampleX = mL + this.xRatio(p.duration, points) * cW;

        // Position tooltip relative to the stack so it overlays the canvas correctly.
        const stack = this.stackRef()?.nativeElement;
        if (!stack) return;
        const stackRect = stack.getBoundingClientRect();
        const lineXInStack = (rect.left - stackRect.left) + sampleX;

        // Anchor Y just above the curve at the hovered point.
        const cssH = rect.height;
        const {mT, mB} = this.marginsY();
        const cH = Math.max(1, cssH - mT - mB);
        const maxPower = Math.max(...points.map((q) => q.power));
        const yLocal = mT + cH * (1 - p.power / maxPower);
        const ttYInStack = (rect.top - stackRect.top) + yLocal - 30;

        const ttWidth = 130; // approximate tooltip width
        let posX = lineXInStack;
        let ttTx = '-50%'; // default: centered
        if (posX + ttWidth / 2 > stackRect.width - 4) {
            // Near right edge: anchor right
            posX = Math.min(posX, stackRect.width - 4);
            ttTx = '-100%';
        } else if (posX - ttWidth / 2 < 4) {
            // Near left edge: anchor left
            posX = Math.max(posX, 4);
            ttTx = '0%';
        }
        const stackEl = this.stackRef()?.nativeElement;
        if (stackEl) stackEl.style.setProperty('--tt-tx', ttTx);
        this.ttX.set(posX);
        this.ttY.set(Math.max(8, ttYInStack));
        this.ttHeader.set(p.label);
        this.ttPower.set(Math.round(p.power).toString());
        this.hoverIdx.set(bestIdx);
        this.draw();
    }

    // ── Drawing ─────────────────────────────────────────────────────────

    private draw(): void {
        const canvas = this.canvasRef()?.nativeElement;
        if (!canvas) return;
        const points = this.state().points;
        if (points.length === 0) return;

        const dpr = Math.max(1, window.devicePixelRatio || 1);
        const cssW = canvas.clientWidth;
        const cssH = canvas.clientHeight;
        if (cssW <= 0 || cssH <= 0) return;
        const targetW = Math.round(cssW * dpr);
        const targetH = Math.round(cssH * dpr);
        if (canvas.width !== targetW) canvas.width = targetW;
        if (canvas.height !== targetH) canvas.height = targetH;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, cssW, cssH);

        const {mL, mR} = this.margins(cssW);
        const {mT, mB} = this.marginsY();
        const cW = Math.max(1, cssW - mL - mR);
        const cH = Math.max(1, cssH - mT - mB);

        const maxPower = Math.max(...points.map((p) => p.power));
        const yMax = niceCeil(maxPower * 1.05);

        // ── Y gridlines + labels ─────────────────────────────────────────
        ctx.font = '10px monospace';
        ctx.fillStyle = this.textColor;
        ctx.strokeStyle = this.gridColor;
        ctx.lineWidth = 1;
        const ySteps = 5;
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (let i = 0; i <= ySteps; i++) {
            const v = (yMax * i) / ySteps;
            const y = mT + cH * (1 - i / ySteps);
            ctx.beginPath();
            ctx.moveTo(mL, y);
            ctx.lineTo(mL + cW, y);
            ctx.stroke();
            ctx.fillText(`${Math.round(v)}W`, mL - 6, y);
        }

        // ── X labels (skip overlapping ones) ────────────────────────────
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        const labelPad = 6;
        let lastLabelRight = -Infinity;
        for (const p of points) {
            const x = mL + this.xRatio(p.duration, points) * cW;
            const labelW = ctx.measureText(p.label).width;
            const labelLeft = x - labelW / 2;
            if (labelLeft < lastLabelRight + labelPad) continue;
            ctx.fillText(p.label, x, mT + cH + 4);
            lastLabelRight = x + labelW / 2;
        }

        // ── Filled area under curve ──────────────────────────────────────
        const pathPoints = points.map((p) => ({
            x: mL + this.xRatio(p.duration, points) * cW,
            y: mT + cH * (1 - p.power / yMax),
        }));

        ctx.beginPath();
        ctx.moveTo(pathPoints[0].x, mT + cH);
        for (const pt of pathPoints) ctx.lineTo(pt.x, pt.y);
        ctx.lineTo(pathPoints[pathPoints.length - 1].x, mT + cH);
        ctx.closePath();
        const grad = ctx.createLinearGradient(0, mT, 0, mT + cH);
        grad.addColorStop(0, `rgba(${this.accentRgb.join(',')},0.32)`);
        grad.addColorStop(1, `rgba(${this.accentRgb.join(',')},0.02)`);
        ctx.fillStyle = grad;
        ctx.fill();

        // ── Line on top ──────────────────────────────────────────────────
        ctx.beginPath();
        ctx.moveTo(pathPoints[0].x, pathPoints[0].y);
        for (let i = 1; i < pathPoints.length; i++) {
            ctx.lineTo(pathPoints[i].x, pathPoints[i].y);
        }
        ctx.strokeStyle = this.accentHex;
        ctx.lineWidth = 2;
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';
        ctx.stroke();

        // ── Sample dots ──────────────────────────────────────────────────
        for (const pt of pathPoints) {
            ctx.beginPath();
            ctx.arc(pt.x, pt.y, 2.5, 0, Math.PI * 2);
            ctx.fillStyle = this.accentHex;
            ctx.fill();
        }

        // ── Hover crosshair + highlighted dot ────────────────────────────
        const idx = this.hoverIdx();
        if (idx !== null && idx >= 0 && idx < pathPoints.length) {
            const hp = pathPoints[idx];
            ctx.strokeStyle = this.crosshair;
            ctx.lineWidth = 1;
            ctx.setLineDash([3, 3]);
            ctx.beginPath();
            ctx.moveTo(hp.x, mT);
            ctx.lineTo(hp.x, mT + cH);
            ctx.stroke();
            ctx.setLineDash([]);

            ctx.beginPath();
            ctx.arc(hp.x, hp.y, 5, 0, Math.PI * 2);
            ctx.fillStyle = this.accentHex;
            ctx.fill();
            ctx.lineWidth = 2;
            ctx.strokeStyle = 'rgba(255,255,255,0.85)';
            ctx.stroke();
        }
    }

    private resolveThemeColors(): void {
        const s = getComputedStyle(document.documentElement);
        const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
        const raw = s.getPropertyValue('--accent-color').trim();
        const rgb = cssToRgb(raw);
        if (rgb) {
            this.accentRgb = rgb;
            this.accentHex = `rgb(${rgb.join(',')})`;
        }
        if (isDark) {
            this.gridColor = 'rgba(255,255,255,0.10)';
            this.textColor = 'rgba(255,255,255,0.55)';
            this.crosshair = 'rgba(255,255,255,0.30)';
        } else {
            this.gridColor = 'rgba(0,0,0,0.08)';
            this.textColor = 'rgba(0,0,0,0.55)';
            this.crosshair = 'rgba(0,0,0,0.25)';
        }
    }

    /** Log-scale X position (0..1) for a duration within the current point set's range. */
    private xRatio(duration: number, points: CurvePoint[]): number {
        const minDur = points[0].duration;
        const maxDur = points[points.length - 1].duration;
        if (maxDur === minDur) return 0.5;
        return (Math.log(duration) - Math.log(minDur)) / (Math.log(maxDur) - Math.log(minDur));
    }

    private margins(W: number): {mL: number; mR: number} {
        return W < 500 ? {mL: 36, mR: 24} : {mL: 48, mR: 28};
    }

    private marginsY(): {mT: number; mB: number} {
        return {mT: 14, mB: 22};
    }

    private toPoints(data: Record<number, number>): CurvePoint[] {
        return Object.entries(data ?? {})
            .map(([dur, power]) => ({
                duration: Number(dur),
                power: Number(power),
                label: DURATION_LABELS[Number(dur)] ?? `${dur}s`,
            }))
            .filter((e) => Number.isFinite(e.power) && e.power > 0)
            .sort((a, b) => a.duration - b.duration);
    }
}

// ── Standalone helpers ───────────────────────────────────────────────────

function niceCeil(value: number): number {
    if (value <= 0) return 1;
    const pow = Math.pow(10, Math.floor(Math.log10(value)));
    const norm = value / pow;
    let nice: number;
    if (norm <= 1) nice = 1;
    else if (norm <= 2) nice = 2;
    else if (norm <= 5) nice = 5;
    else nice = 10;
    return nice * pow;
}

function cssToRgb(css: string): [number, number, number] | null {
    if (!css) return null;
    try {
        const ctx = document.createElement('canvas').getContext('2d');
        if (!ctx) return null;
        ctx.fillStyle = css;
        const out = ctx.fillStyle;
        if (out.startsWith('#')) {
            return [
                parseInt(out.slice(1, 3), 16),
                parseInt(out.slice(3, 5), 16),
                parseInt(out.slice(5, 7), 16),
            ];
        }
        const m = out.match(/(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
        return m ? [+m[1], +m[2], +m[3]] : null;
    } catch {
        return null;
    }
}

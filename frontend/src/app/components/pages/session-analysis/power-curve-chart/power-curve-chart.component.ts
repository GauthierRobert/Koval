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
import {AnalyticsService} from '../../../../services/analytics.service';
import {
    CURVE_MARGINS_Y,
    CurvePoint,
    CurveTheme,
    curveMargins,
    curveXRatio,
    drawPowerCurve,
    resolveCurveTheme,
    toCurvePoints,
} from './power-curve-chart.utils';

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
    templateUrl: './power-curve-chart.component.html',
    styleUrl: './power-curve-chart.component.css',
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

    private theme: CurveTheme = {
        accentRgb: [255, 157, 0],
        accentHex: 'rgb(255,157,0)',
        gridColor: 'rgba(255,255,255,0.10)',
        textColor: 'rgba(255,255,255,0.55)',
        crosshair: 'rgba(255,255,255,0.30)',
    };

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
        const {mL, mR} = curveMargins(cssW);
        const cW = Math.max(1, cssW - mL - mR);

        // Snap to nearest point in pixel space (log-scaled X).
        let bestIdx = 0;
        let bestDist = Infinity;
        for (let i = 0; i < points.length; i++) {
            const px = mL + curveXRatio(points[i].duration, points) * cW;
            const d = Math.abs(px - x);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }

        const p = points[bestIdx];
        const sampleX = mL + curveXRatio(p.duration, points) * cW;

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
        drawPowerCurve({
            canvas,
            points: this.state().points,
            theme: this.theme,
            hoverIdx: this.hoverIdx(),
        });
    }

    private resolveThemeColors(): void {
        this.theme = resolveCurveTheme();
    }

    private marginsY = (): {mT: number; mB: number} => CURVE_MARGINS_Y;

    private toPoints = toCurvePoints;
}

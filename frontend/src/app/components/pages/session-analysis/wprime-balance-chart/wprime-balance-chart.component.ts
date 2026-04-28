import {
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    ElementRef,
    inject,
    Input,
    OnChanges,
    OnDestroy,
    SimpleChanges,
    ViewChild,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {FitRecord} from '../../../../services/metrics.service';
import {formatTimeHMS} from '../../../shared/format/format.utils';

export interface WPrimeStats {
    minimum: number;
    minimumPct: number;
    minimumAtSecond: number;
    timeBelow50Pct: number;
    timeBelow25Pct: number;
    matchesBurned: number;
    capacityKj: number;
}

const DEFAULT_WPRIME_J = 20000; // 20 kJ default
const MIN_TAU = 200; // floor for tau (seconds) — Skiba model breaks down very near CP
const MATCH_THRESHOLD_PCT = 0.5; // dropping below 50% is one "match"
const MATCH_RECOVER_PCT = 0.75;

@Component({
    selector: 'app-wprime-balance-chart',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    templateUrl: './wprime-balance-chart.component.html',
    styleUrl: './wprime-balance-chart.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WPrimeBalanceChartComponent implements AfterViewInit, OnChanges, OnDestroy {
    @Input({required: true}) records: FitRecord[] = [];
    @Input({required: true}) sportType = '';
    @Input() ftp: number | null = null;
    @Input() criticalPower: number | null = null;
    @Input() userWPrimeJ: number | null = null;
    @Input() wPrimeJ = DEFAULT_WPRIME_J;

    /** True when the chart is using user-specific CP and W' values from a 3-min test. */
    get usingUserCp(): boolean {
        return this.criticalPower != null && this.criticalPower > 0;
    }
    get usingUserWPrime(): boolean {
        return this.userWPrimeJ != null && this.userWPrimeJ > 0;
    }

    @ViewChild('cv', {static: false}) canvasRef?: ElementRef<HTMLCanvasElement>;

    stats: WPrimeStats | null = null;
    visible = false;
    helpOpen = false;
    private resizeObserver?: ResizeObserver;
    private trace: number[] = [];
    /** Effective W' capacity used by the most recent compute, in joules. Read by draw(). */
    private effectiveWPrime = DEFAULT_WPRIME_J;
    private translate = inject(TranslateService);
    private langSub?: Subscription;
    private axisFull = 'Full';
    private axisHalf = 'Half';
    private axisEmpty = 'Empty';

    ngAfterViewInit(): void {
        this.refreshAxisLabels();
        this.langSub = this.translate.onLangChange.subscribe(() => {
            this.refreshAxisLabels();
            this.draw();
        });
        this.recompute();
        this.attachResize();
    }

    formatTime(seconds: number): string {
        return formatTimeHMS(seconds);
    }

    toggleHelp(): void {
        this.helpOpen = !this.helpOpen;
    }

    closeHelp(): void {
        this.helpOpen = false;
    }

    ngOnChanges(_changes: SimpleChanges): void {
        this.recompute();
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        this.langSub?.unsubscribe();
    }

    private refreshAxisLabels(): void {
        this.axisFull = this.translate.instant('SESSION_ANALYSIS.WPRIME_AXIS_FULL');
        this.axisHalf = this.translate.instant('SESSION_ANALYSIS.WPRIME_AXIS_HALF');
        this.axisEmpty = this.translate.instant('SESSION_ANALYSIS.WPRIME_AXIS_EMPTY');
    }

    private attachResize(): void {
        if (typeof ResizeObserver === 'undefined' || !this.canvasRef) return;
        this.resizeObserver = new ResizeObserver(() => this.draw());
        this.resizeObserver.observe(this.canvasRef.nativeElement);
    }

    private recompute(): void {
        // Prefer the user's measured CP from a 3-min all-out test when available;
        // otherwise fall back to FTP as a rough proxy for critical power.
        const cp = this.criticalPower && this.criticalPower > 0
            ? this.criticalPower
            : (this.ftp && this.ftp > 0 ? this.ftp : null);
        const canShow = this.sportType === 'CYCLING'
            && cp != null
            && this.records?.length >= 60
            && this.records.some(r => r.power > 0);
        this.visible = canShow;
        if (!canShow) {
            this.stats = null;
            this.trace = [];
            return;
        }

        // Same fallback logic for W': user-supplied W' beats the generic 20 kJ default.
        const wp = this.userWPrimeJ && this.userWPrimeJ > 0
            ? this.userWPrimeJ
            : this.wPrimeJ;
        this.effectiveWPrime = wp;
        const recs = this.records;

        // Skiba differential W' balance:
        //   if power > CP: W'bal -= (power - CP) * dt
        //   else:          W'bal += (wp - W'bal) * (1 - exp(-dt / tau))
        //   tau = wp / (CP - avgRecoveryPower) clamped to MIN_TAU
        let bal = wp;
        const trace: number[] = new Array(recs.length);
        let minimum = wp;
        let minIdx = 0;
        let timeBelow50 = 0;
        let timeBelow25 = 0;
        let belowMatch = false;
        let matches = 0;

        for (let i = 0; i < recs.length; i++) {
            const dt = i + 1 < recs.length
                ? Math.max(0, Math.min(recs[i + 1].timestamp - recs[i].timestamp, 30))
                : 1;
            const p = recs[i].power;
            if (p > cp) {
                bal -= (p - cp) * dt;
            } else {
                const recovery = Math.max(MIN_TAU, wp / Math.max(1, cp - p));
                bal += (wp - bal) * (1 - Math.exp(-dt / recovery));
            }
            if (bal < 0) bal = 0;
            if (bal > wp) bal = wp;
            trace[i] = bal;

            if (bal < minimum) {
                minimum = bal;
                minIdx = i;
            }

            const pct = bal / wp;
            if (pct < 0.5) timeBelow50 += dt;
            if (pct < 0.25) timeBelow25 += dt;

            if (!belowMatch && pct < MATCH_THRESHOLD_PCT) {
                matches++;
                belowMatch = true;
            } else if (belowMatch && pct >= MATCH_RECOVER_PCT) {
                belowMatch = false;
            }
        }

        this.trace = trace;
        this.stats = {
            minimum: Math.round(minimum),
            minimumPct: Math.round((minimum / wp) * 100),
            minimumAtSecond: minIdx > 0 ? recs[minIdx].timestamp - recs[0].timestamp : 0,
            timeBelow50Pct: Math.round(timeBelow50),
            timeBelow25Pct: Math.round(timeBelow25),
            matchesBurned: matches,
            capacityKj: Math.round(wp / 100) / 10,
        };
        // requestAnimationFrame in next microtask so canvas has correct sizing
        setTimeout(() => this.draw());
    }

    private draw(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas || this.trace.length === 0) return;

        const dpr = window.devicePixelRatio || 1;
        const cssW = canvas.clientWidth;
        const cssH = canvas.clientHeight;
        if (cssW <= 0 || cssH <= 0) return;
        canvas.width = Math.round(cssW * dpr);
        canvas.height = Math.round(cssH * dpr);

        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, cssW, cssH);

        const padL = 64, padR = 12, padT = 12, padB = 22;
        const w = cssW - padL - padR;
        const h = cssH - padT - padB;
        if (w <= 0 || h <= 0) return;

        const wp = this.effectiveWPrime;
        const n = this.trace.length;
        const xOf = (i: number) => padL + (i / (n - 1)) * w;
        const yOf = (v: number) => padT + (1 - v / wp) * h;

        // Danger zone shading (below 25%)
        ctx.fillStyle = 'oklch(0.55 0.18 25 / 0.08)';
        ctx.fillRect(padL, yOf(wp * 0.25), w, h - (yOf(wp * 0.25) - padT));

        // 25% gridline (faint, marks "near empty" boundary)
        ctx.strokeStyle = 'rgba(255,255,255,0.06)';
        ctx.lineWidth = 1;
        ctx.setLineDash([2, 4]);
        ctx.beginPath();
        ctx.moveTo(padL, yOf(wp * 0.25));
        ctx.lineTo(padL + w, yOf(wp * 0.25));
        ctx.stroke();

        // 50% reference line
        ctx.strokeStyle = 'rgba(255,255,255,0.12)';
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        ctx.moveTo(padL, yOf(wp * 0.5));
        ctx.lineTo(padL + w, yOf(wp * 0.5));
        ctx.stroke();
        ctx.setLineDash([]);

        // Filled area under the curve
        const grad = ctx.createLinearGradient(0, padT, 0, padT + h);
        grad.addColorStop(0, 'oklch(0.72 0.16 200 / 0.5)');
        grad.addColorStop(1, 'oklch(0.72 0.16 200 / 0.05)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.moveTo(xOf(0), padT + h);
        for (let i = 0; i < n; i++) ctx.lineTo(xOf(i), yOf(this.trace[i]));
        ctx.lineTo(xOf(n - 1), padT + h);
        ctx.closePath();
        ctx.fill();

        // Curve
        ctx.strokeStyle = 'oklch(0.78 0.16 200)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        for (let i = 0; i < n; i++) {
            const x = xOf(i);
            const y = yOf(this.trace[i]);
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        }
        ctx.stroke();

        // Y axis labels: kJ value on top line, plain-language word on bottom line
        const kJ = wp / 1000;
        const fullKj = kJ.toFixed(1);
        const halfKj = (kJ / 2).toFixed(1);
        const emptyKj = '0';

        ctx.font = '10px "JetBrains Mono", monospace';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';

        const drawYLabel = (yPx: number, kjText: string, word: string) => {
            ctx.fillStyle = 'rgba(255,255,255,0.7)';
            ctx.fillText(`${kjText} kJ`, padL - 6, yPx - 6);
            ctx.fillStyle = 'rgba(255,255,255,0.4)';
            ctx.fillText(word, padL - 6, yPx + 6);
        };
        drawYLabel(yOf(wp), fullKj, this.axisFull);
        drawYLabel(yOf(wp * 0.5), halfKj, this.axisHalf);
        drawYLabel(yOf(0), emptyKj, this.axisEmpty);

        // X axis (time labels)
        const totalSec = this.records[n - 1].timestamp - this.records[0].timestamp;
        ctx.fillStyle = 'rgba(255,255,255,0.5)';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillText('0:00', padL, padT + h + 4);
        ctx.fillText(formatHM(totalSec), padL + w, padT + h + 4);
    }
}

function formatHM(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}`;
    const s = seconds % 60;
    return `${m}:${String(s).padStart(2, '0')}`;
}

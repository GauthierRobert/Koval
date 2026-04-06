import {AfterViewChecked, AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FitRecord} from '../../../../services/metrics.service';
import {BlockSummary} from '../../../../services/workout-execution.service';
import {ZoneBlock} from '../../../../services/zone';

@Component({
    selector: 'app-fit-timeseries-chart',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="chart-wrap">
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
                @if (zoneBlocks.length > 0 || blockSummaries.length > 0) {
                    <span class="toggle-sep"></span>
                    <button class="toggle-btn" [class.active]="showBlocks" (click)="toggle('showBlocks')">
                        <span class="dot blocks"></span> Blocks
                    </button>
                }
            </div>
            <div class="charts-stack" #stack (mouseleave)="onMouseLeave()">
                @if (showPrimary) {
                    <canvas #primaryCanvas class="mc primary-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouch($event)"
                        (touchmove)="onTouch($event)"
                        (touchend)="onMouseLeave()"
                        (touchcancel)="onMouseLeave()"></canvas>
                }
                @if (showHR) {
                    <canvas #hrCanvas class="mc hr-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouch($event)"
                        (touchmove)="onTouch($event)"
                        (touchend)="onMouseLeave()"
                        (touchcancel)="onMouseLeave()"></canvas>
                }
                @if (showCadence) {
                    <canvas #cadCanvas class="mc cad-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouch($event)"
                        (touchmove)="onTouch($event)"
                        (touchend)="onMouseLeave()"
                        (touchcancel)="onMouseLeave()"></canvas>
                }
                @if (_hasElevation) {
                    <canvas #elevCanvas class="mc elev-h"
                        (mousemove)="onHover($event)"
                        (touchstart)="onTouch($event)"
                        (touchmove)="onTouch($event)"
                        (touchend)="onMouseLeave()"
                        (touchcancel)="onMouseLeave()"></canvas>
                }
                <canvas #xCanvas class="mc xaxis-h"></canvas>
                @if (hoverIdx !== null) {
                    <div class="tt" [style.left.px]="ttX" [style.top.px]="ttY">
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
        .dot.blocks { background: #2ecc71; }
        .toggle-sep { width: 1px; height: 16px; background: var(--overlay-10); margin: 0 2px; }
        .charts-stack { position: relative; display: flex; flex-direction: column; gap: 4px; min-height: 0; }
        .mc {
            width: 100%; display: block; cursor: crosshair; min-height: 0;
            touch-action: none;
            -webkit-user-select: none; user-select: none;
            -webkit-touch-callout: none;
        }
        .primary-h { flex: 6 1 0; min-height: 250px; }
        .hr-h { flex: 2.8 1 0; min-height: 100px; }
        .cad-h { flex: 2.8 1 0; min-height: 100px; }
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
            .chart-wrap { min-height: 90vh; }
            .chart-toggles { gap: 4px; }
            .toggle-btn { padding: 3px 8px; font-size: 9px; }
            .primary-h { min-height: 350px; }
            .hr-h { min-height: 160px; }
            .cad-h { min-height: 160px; }
            .elev-h { min-height: 100px; }
        }
    `],
})
export class FitTimeseriesChartComponent implements OnChanges, AfterViewInit, AfterViewChecked, OnDestroy {
    @Input() records: FitRecord[] = [];
    @Input() ftp: number | null = null;
    @Input() sportType = 'CYCLING';
    @Input() blockSummaries: BlockSummary[] = [];
    @Input() blockColors: string[] = [];
    @Input() zoneBlocks: ZoneBlock[] = [];

    @ViewChild('stack') stackRef!: ElementRef<HTMLDivElement>;
    @ViewChild('primaryCanvas') pRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('hrCanvas') hrRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('cadCanvas') cadRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('elevCanvas') elRef?: ElementRef<HTMLCanvasElement>;
    @ViewChild('xCanvas') xRef?: ElementRef<HTMLCanvasElement>;

    showPrimary = true;
    showHR = true;
    showCadence = false;
    showBlocks = false;

    hoverIdx: number | null = null;
    ttX = 0;
    ttY = 0;
    ttHeader = '';
    ttRows: Array<{ label: string; value: string; color: string }> = [];

    _hasElevation = false;
    private ready = false;
    private resizeObserver: ResizeObserver | null = null;
    private observedCanvases = new Set<HTMLCanvasElement>();

    /** Side margins shrink on narrow viewports so the plot keeps usable width on phones. */
    private margins(W: number): { mL: number; mR: number } {
        return W < 500 ? { mL: 28, mR: 16 } : { mL: 48, mR: 48 };
    }

    // Canvas-safe colors resolved from CSS variables
    private _accentRgb: [number, number, number] = [255, 157, 0];
    private _accentHex = 'rgb(255,157,0)';
    private _textAlpha40 = 'rgba(200,200,200,0.4)';
    private _textAlpha30 = 'rgba(200,200,200,0.3)';
    private _gridAlpha15 = 'rgba(200,200,200,0.15)';
    private _gridAlpha12 = 'rgba(200,200,200,0.12)';
    private _crosshairAlpha = 'rgba(200,200,200,0.25)';
    private _dotStroke = 'rgba(255,255,255,0.8)';

    get isCycling(): boolean { return this.sportType === 'CYCLING'; }
    get primaryLabel(): string { return this.isCycling ? 'Power' : 'Speed'; }
    private get cadUnit(): string { return this.sportType === 'RUNNING' ? 'spm' : 'rpm'; }

    /** Convert any CSS color (including oklch) to [r, g, b]. */
    private cssToRgb(css: string): [number, number, number] {
        const ctx = document.createElement('canvas').getContext('2d')!;
        ctx.fillStyle = css;
        const out = ctx.fillStyle; // '#rrggbb' or 'rgba(r, g, b, a)'
        if (out.startsWith('#')) {
            return [
                parseInt(out.slice(1, 3), 16),
                parseInt(out.slice(3, 5), 16),
                parseInt(out.slice(5, 7), 16),
            ];
        }
        const m = out.match(/(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
        return m ? [+m[1], +m[2], +m[3]] : [255, 157, 0];
    }

    /** Build canvas-safe theme colors from CSS custom properties. */
    private resolveThemeColors(): void {
        const s = getComputedStyle(document.documentElement);
        const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
        const raw = s.getPropertyValue('--accent-color').trim();
        this._accentRgb = raw ? this.cssToRgb(raw) : [255, 157, 0];
        this._accentHex = `rgb(${this._accentRgb.join(',')})`;
        if (isDark) {
            this._textAlpha40 = 'rgba(255,255,255,0.4)';
            this._textAlpha30 = 'rgba(255,255,255,0.3)';
            this._gridAlpha15 = 'rgba(255,255,255,0.15)';
            this._gridAlpha12 = 'rgba(255,255,255,0.12)';
            this._crosshairAlpha = 'rgba(255,255,255,0.25)';
            this._dotStroke = 'rgba(255,255,255,0.8)';
        } else {
            this._textAlpha40 = 'rgba(0,0,0,0.45)';
            this._textAlpha30 = 'rgba(0,0,0,0.35)';
            this._gridAlpha15 = 'rgba(0,0,0,0.12)';
            this._gridAlpha12 = 'rgba(0,0,0,0.08)';
            this._crosshairAlpha = 'rgba(0,0,0,0.2)';
            this._dotStroke = 'rgba(0,0,0,0.6)';
        }
    }

    /** Return the accent color with fractional alpha (0–1). */
    private accentAlpha(a: number): string {
        const [r, g, b] = this._accentRgb;
        return `rgba(${r},${g},${b},${a})`;
    }

    private getCad(r: FitRecord): number {
        return this.sportType === 'RUNNING' ? r.cadence * 2 : r.cadence;
    }

    private getCadBlock(c: number): number {
        return this.sportType === 'RUNNING' ? c * 2 : c;
    }

    ngAfterViewInit(): void {
        this.ready = true;
        this.resizeObserver = new ResizeObserver(() => {
            if (this.ready) this.drawAll();
        });
        this.syncObservedCanvases();
        this.drawAll();
        // Re-draw after the first paint so canvases pick up their flex-resolved size.
        requestAnimationFrame(() => this.drawAll());
    }

    ngAfterViewChecked(): void {
        // Conditional canvases (@if showHR / showCadence / _hasElevation) mount/unmount;
        // re-attach the ResizeObserver so size changes on any visible canvas trigger a redraw.
        if (this.ready) this.syncObservedCanvases();
    }

    private syncObservedCanvases(): void {
        if (!this.resizeObserver) return;
        const current: HTMLCanvasElement[] = [
            this.pRef?.nativeElement,
            this.hrRef?.nativeElement,
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
    }

    ngOnChanges(): void {
        this.updateHasElevation();
        if (this.ready) setTimeout(() => this.drawAll(), 0);
    }

    toggle(prop: 'showPrimary' | 'showHR' | 'showCadence' | 'showBlocks'): void {
        this[prop] = !this[prop];
        setTimeout(() => this.drawAll(), 0);
    }

    onHover(event: MouseEvent): void {
        const canvas = event.target as HTMLCanvasElement;
        this.computeHoverAt(canvas, event.clientX, event.clientY);
    }

    onTouch(event: TouchEvent): void {
        if (event.cancelable) event.preventDefault();
        const touch = event.touches[0];
        if (!touch) return;
        const canvas = event.currentTarget as HTMLCanvasElement;
        this.computeHoverAt(canvas, touch.clientX, touch.clientY);
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
        const { mL, mR } = this.margins(cssW);
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

        // Tooltip position relative to stack
        const stackRect = this.stackRef.nativeElement.getBoundingClientRect();
        const rawX = clientX - stackRect.left + 14;
        const stackW = stackRect.width;
        this.ttX = rawX + 160 > stackW ? rawX - 174 : rawX;
        this.ttY = clientY - stackRect.top - 10;

        this.hoverIdx = idx;
        this.buildTooltip();
        this.drawAll();
    }

    onMouseLeave(): void {
        this.hoverIdx = null;
        this.ttRows = [];
        this.drawAll();
    }

    // ── Shared helpers ───────────────────────────────────────────────────

    private updateHasElevation(): void {
        if (!this.records.length) { this._hasElevation = false; return; }
        const vals = this.records.filter(r => r.elevation != null).map(r => r.elevation!);
        this._hasElevation = vals.length >= 2 && vals.some(v => v !== vals[0]);
    }

    private initCanvas(ref: ElementRef<HTMLCanvasElement> | undefined): {
        ctx: CanvasRenderingContext2D; W: number; H: number; cW: number;
        xOf: (i: number) => number; xOfT: (sec: number) => number;
        mT: number; mB: number; mL: number; mR: number;
    } | null {
        const c = ref?.nativeElement;
        if (!c) return null;
        const dpr = window.devicePixelRatio || 1;
        const W = c.offsetWidth || 600;
        const H = c.offsetHeight || 100;
        c.width = Math.round(W * dpr);
        c.height = Math.round(H * dpr);
        const ctx = c.getContext('2d')!;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);
        if (!this.records.length) return null;

        const { mL, mR } = this.margins(W);
        const mT = 6, mB = 6;
        const cW = W - mL - mR;
        const t0 = this.records[0].timestamp;
        const n = this.records.length;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const xOf = (i: number) => mL + ((this.records[i].timestamp - t0) / totalSec) * cW;
        const xOfT = (sec: number) => mL + (sec / totalSec) * cW;
        return { ctx, W, H, cW, xOf, xOfT, mT, mB, mL, mR };
    }

    private drawCrosshair(ctx: CanvasRenderingContext2D, x: number, top: number, bottom: number): void {
        if (this.hoverIdx === null) return;
        ctx.save();
        ctx.strokeStyle = this._crosshairAlpha;
        ctx.lineWidth = 1;
        ctx.setLineDash([]);
        ctx.beginPath();
        ctx.moveTo(x, top);
        ctx.lineTo(x, bottom);
        ctx.stroke();
        ctx.restore();
    }

    private drawDot(ctx: CanvasRenderingContext2D, x: number, y: number, color: string): void {
        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fillStyle = color;
        ctx.fill();
        ctx.strokeStyle = this._dotStroke;
        ctx.lineWidth = 1.5;
        ctx.stroke();
    }

    private drawBlockBounds(ctx: CanvasRenderingContext2D, xOfT: (s: number) => number, top: number, bottom: number, totalSec: number): void {
        if (!this.blockSummaries.length || this.showBlocks) return;
        ctx.save();
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = this._gridAlpha12;
        ctx.lineWidth = 1;
        let acc = 0;
        for (let i = 0; i < this.blockSummaries.length - 1; i++) {
            acc += this.blockSummaries[i].durationSeconds;
            const x = xOfT(acc);
            ctx.beginPath();
            ctx.moveTo(x, top);
            ctx.lineTo(x, bottom);
            ctx.stroke();
        }
        ctx.restore();
    }

    private get useZB(): boolean { return this.showBlocks && this.zoneBlocks.length > 0; }
    private get usePB(): boolean { return this.showBlocks && !this.useZB && this.blockSummaries.length > 0; }

    // ── Draw All ─────────────────────────────────────────────────────────

    drawAll(): void {
        this.resolveThemeColors();
        this.drawPrimary();
        this.drawHR();
        this.drawCadence();
        this.drawElevation();
        this.drawXAxis();
    }

    // ── Primary (Power / Speed) ──────────────────────────────────────────

    private drawPrimary(): void {
        const s = this.initCanvas(this.pRef);
        if (!s) return;
        const { ctx, W, H, cW, xOf, xOfT, mT, mB, mL, mR } = s;
        const accent = this._accentHex;
        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const chartH = H - mT - mB;
        const top = mT, bottom = mT + chartH;

        let maxP = 1, maxS = 1;
        let yOf: (v: number) => number;
        if (this.isCycling) {
            const sm = this.rollingAvg(this.records.map(r => r.power), 5);
            maxP = Math.max(this.ftp ? this.ftp * 1.5 : 0, ...sm) || 1;
            yOf = (v) => top + chartH * (1 - v / maxP);
        } else {
            const sp = this.records.map(r => (r.speed || 0) * 3.6);
            const sm = this.rollingAvg(sp, 5);
            maxS = Math.max(...sm.filter(v => v > 0), 1);
            yOf = (v) => top + chartH * (1 - v / maxS);
        }

        // ── Render modes ─────────────────────────────────
        if (this.useZB) {
            for (const b of this.zoneBlocks) {
                const x1 = xOf(b.startIndex), x2 = xOf(b.endIndex);
                const val = this.isCycling ? b.avgPower : b.avgSpeed;
                const y = yOf(val);
                const [br, bg, bb] = this.cssToRgb(b.color);
                const hb = `rgb(${br},${bg},${bb})`;
                ctx.fillStyle = `rgba(${br},${bg},${bb},0.25)`;
                ctx.fillRect(x1, y, x2 - x1, bottom - y);
                ctx.strokeStyle = hb;
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(x1, y); ctx.lineTo(x2, y);
                ctx.stroke();
            }
        } else if (this.usePB) {
            let acc = 0;
            for (let bi = 0; bi < this.blockSummaries.length; bi++) {
                const b = this.blockSummaries[bi];
                const x1 = xOfT(acc), x2 = xOfT(acc + b.durationSeconds);
                const val = this.isCycling ? b.actualPower
                    : (b.distanceMeters && b.durationSeconds > 0 ? (b.distanceMeters / b.durationSeconds) * 3.6 : 0);
                const y = yOf(val);
                if (b.targetPower > 0) {
                    const yt = yOf(b.targetPower);
                    ctx.save(); ctx.setLineDash([3, 3]);
                    ctx.strokeStyle = this._gridAlpha15; ctx.lineWidth = 1;
                    ctx.beginPath(); ctx.moveTo(x1, yt); ctx.lineTo(x2, yt); ctx.stroke();
                    ctx.restore();
                }
                const [cr, cg, cb] = this.cssToRgb(this.blockColors[bi] || accent);
                const bColor = `rgb(${cr},${cg},${cb})`;
                ctx.fillStyle = `rgba(${cr},${cg},${cb},0.25)`;
                ctx.fillRect(x1, y, x2 - x1, bottom - y);
                ctx.strokeStyle = bColor; ctx.lineWidth = 2;
                ctx.beginPath(); ctx.moveTo(x1, y); ctx.lineTo(x2, y); ctx.stroke();
                acc += b.durationSeconds;
            }
        } else {
            if (this.isCycling) {
                const sm = this.rollingAvg(this.records.map(r => r.power), 5);
                if (this.ftp) {
                    const fy = yOf(this.ftp);
                    ctx.save(); ctx.setLineDash([4, 4]);
                    ctx.strokeStyle = this._gridAlpha15; ctx.lineWidth = 1;
                    ctx.beginPath(); ctx.moveTo(mL, fy); ctx.lineTo(W - mR, fy); ctx.stroke();
                    ctx.restore();
                    ctx.fillStyle = this._textAlpha30;
                    ctx.font = '9px monospace'; ctx.textAlign = 'left';
                    ctx.fillText('FTP', mL + 2, fy - 3);
                }
                if (sm.length > 1) {
                    ctx.beginPath();
                    ctx.moveTo(xOf(0), bottom);
                    sm.forEach((p, i) => ctx.lineTo(xOf(i), yOf(p)));
                    ctx.lineTo(xOf(n - 1), bottom);
                    ctx.closePath();
                    const g = ctx.createLinearGradient(0, top, 0, bottom);
                    g.addColorStop(0, this.accentAlpha(0.5)); g.addColorStop(1, this.accentAlpha(0.03));
                    ctx.fillStyle = g; ctx.fill();
                    ctx.beginPath();
                    ctx.moveTo(xOf(0), yOf(sm[0]));
                    sm.forEach((p, i) => ctx.lineTo(xOf(i), yOf(p)));
                    ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.stroke();
                }
            } else {
                const sp = this.records.map(r => (r.speed || 0) * 3.6);
                const sm = this.rollingAvg(sp, 5);
                if (sm.length > 1) {
                    ctx.beginPath();
                    ctx.moveTo(xOf(0), bottom);
                    sm.forEach((v, i) => ctx.lineTo(xOf(i), yOf(v)));
                    ctx.lineTo(xOf(n - 1), bottom);
                    ctx.closePath();
                    const g = ctx.createLinearGradient(0, top, 0, bottom);
                    g.addColorStop(0, this.accentAlpha(0.5)); g.addColorStop(1, this.accentAlpha(0.03));
                    ctx.fillStyle = g; ctx.fill();
                    ctx.beginPath();
                    ctx.moveTo(xOf(0), yOf(sm[0]));
                    sm.forEach((v, i) => ctx.lineTo(xOf(i), yOf(v)));
                    ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.stroke();
                }
            }
        }

        // Y-axis labels
        ctx.fillStyle = this._textAlpha40;
        ctx.font = '9px monospace';
        ctx.textAlign = 'right';
        if (this.isCycling) {
            [0, 0.5, 1].forEach(f => {
                const p = Math.round(maxP * f);
                ctx.fillText(String(p), mL - 4, yOf(p) + 4);
            });
        } else {
            [0, 0.5, 1].forEach(f => {
                const v = Math.round(maxS * f * 10) / 10;
                ctx.fillText(v + ' km/h', mL - 4, yOf(v) + 4);
            });
        }

        this.drawBlockBounds(ctx, xOfT, top, bottom, totalSec);

        // Hover
        if (this.hoverIdx !== null) {
            const hx = xOf(this.hoverIdx);
            this.drawCrosshair(ctx, hx, top, bottom);
            const val = this.hoverPrimaryValue(this.hoverIdx, t0);
            this.drawDot(ctx, hx, yOf(val), accent);
        }
    }

    // ── Heart Rate ───────────────────────────────────────────────────────

    private drawHR(): void {
        const s = this.initCanvas(this.hrRef);
        if (!s) return;
        const { ctx, W, H, cW, xOf, xOfT, mT, mB, mL, mR } = s;
        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const chartH = H - mT - mB;
        const top = mT, bottom = mT + chartH;
        const color = '#e74c3c';

        const hrs = this.records.map(r => r.heartRate).filter(v => v > 0);
        const minHR = 100;
        const maxHR = hrs.length ? Math.max(Math.max(...hrs) * 1.05, minHR + 20) : 220;
        const yOf = (hr: number) => top + chartH * (1 - (Math.max(hr, minHR) - minHR) / (maxHR - minHR));

        if (this.useZB) {
            this.drawSteppedLine(ctx, xOf, yOf, this.zoneBlocks.map(b => ({
                s: b.startIndex, e: b.endIndex, v: b.avgHR,
            })), color);
        } else if (this.usePB) {
            this.drawSteppedBlockLine(ctx, xOfT, yOf, this.blockSummaries.map(b => ({
                dur: b.durationSeconds, v: b.actualHR,
            })), color);
        } else {
            ctx.beginPath();
            let first = true;
            this.records.forEach((r, i) => {
                if (!r.heartRate) return;
                if (first) { ctx.moveTo(xOf(i), yOf(r.heartRate)); first = false; }
                else ctx.lineTo(xOf(i), yOf(r.heartRate));
            });
            ctx.strokeStyle = color; ctx.lineWidth = 1.5; ctx.stroke();
        }

        // Y-axis labels
        ctx.fillStyle = color;
        ctx.font = '9px monospace';
        ctx.textAlign = 'right';
        const mid = Math.round((minHR + maxHR) / 2);
        [Math.round(maxHR), mid, minHR].forEach(v => ctx.fillText(String(v), mL - 4, yOf(v) + 4));

        this.drawBlockBounds(ctx, xOfT, top, bottom, totalSec);

        if (this.hoverIdx !== null) {
            const hx = xOf(this.hoverIdx);
            this.drawCrosshair(ctx, hx, top, bottom);
            const hr = this.hoverHR(this.hoverIdx, t0);
            if (hr) this.drawDot(ctx, hx, yOf(hr), color);
        }
    }

    // ── Cadence ──────────────────────────────────────────────────────────

    private drawCadence(): void {
        const s = this.initCanvas(this.cadRef);
        if (!s) return;
        const { ctx, W, H, cW, xOf, xOfT, mT, mB, mL, mR } = s;
        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const chartH = H - mT - mB;
        const top = mT, bottom = mT + chartH;
        const color = '#3b82f6';

        const cads = this.records.map(r => this.getCad(r)).filter(v => v > 0);
        const minCad = this.sportType === 'RUNNING' ? 140 : 40;
        const maxCad = cads.length ? Math.max(Math.max(...cads) * 1.05, minCad + 20) : 120;
        const yOf = (c: number) => top + chartH * (1 - (Math.max(c, minCad) - minCad) / (maxCad - minCad));

        if (this.useZB) {
            this.drawSteppedLine(ctx, xOf, yOf, this.zoneBlocks.map(b => ({
                s: b.startIndex, e: b.endIndex, v: this.getCadBlock(b.avgCadence),
            })), color);
        } else if (this.usePB) {
            this.drawSteppedBlockLine(ctx, xOfT, yOf, this.blockSummaries.map(b => ({
                dur: b.durationSeconds, v: this.getCadBlock(b.actualCadence),
            })), color);
        } else {
            ctx.beginPath();
            let first = true;
            this.records.forEach((r, i) => {
                if (!r.cadence) return;
                const c = this.getCad(r);
                if (first) { ctx.moveTo(xOf(i), yOf(c)); first = false; }
                else ctx.lineTo(xOf(i), yOf(c));
            });
            ctx.strokeStyle = color; ctx.lineWidth = 1.5; ctx.stroke();
        }

        // Y-axis labels (right side to avoid overlap with HR)
        ctx.fillStyle = color;
        ctx.font = '9px monospace';
        ctx.textAlign = 'left';
        const mid = Math.round((minCad + maxCad) / 2);
        [Math.round(maxCad), mid, minCad].forEach(v =>
            ctx.fillText(String(v), W - mR + 4, yOf(v) + 4));

        this.drawBlockBounds(ctx, xOfT, top, bottom, totalSec);

        if (this.hoverIdx !== null) {
            const hx = xOf(this.hoverIdx);
            this.drawCrosshair(ctx, hx, top, bottom);
            const c = this.hoverCadence(this.hoverIdx, t0);
            if (c) this.drawDot(ctx, hx, yOf(c), color);
        }
    }

    // ── Elevation ────────────────────────────────────────────────────────

    private drawElevation(): void {
        const s = this.initCanvas(this.elRef);
        if (!s) return;
        const { ctx, W, H, cW, xOf, xOfT, mT, mB, mL } = s;
        const chartH = H - mT - mB;
        const top = mT, bottom = mT + chartH;

        const elevs = this.records.filter(r => r.elevation != null).map(r => r.elevation!);
        if (elevs.length < 2) return;
        const minE = Math.min(...elevs) - 5;
        const maxE = Math.max(...elevs) + 5;
        const range = maxE - minE || 1;
        const yOf = (e: number) => top + chartH * (1 - (e - minE) / range);

        // Filled area
        ctx.beginPath();
        let started = false;
        let lastX = mL;
        this.records.forEach((r, i) => {
            if (r.elevation == null) return;
            const x = xOf(i);
            if (!started) { ctx.moveTo(x, bottom); ctx.lineTo(x, yOf(r.elevation)); started = true; }
            else ctx.lineTo(x, yOf(r.elevation));
            lastX = x;
        });
        if (started) {
            ctx.lineTo(lastX, bottom);
            ctx.closePath();
            ctx.fillStyle = 'rgba(76,175,80,0.18)';
            ctx.fill();
        }

        // Top edge
        ctx.beginPath();
        let first = true;
        this.records.forEach((r, i) => {
            if (r.elevation == null) return;
            const x = xOf(i);
            if (first) { ctx.moveTo(x, yOf(r.elevation)); first = false; }
            else ctx.lineTo(x, yOf(r.elevation));
        });
        ctx.strokeStyle = 'rgba(76,175,80,0.6)';
        ctx.lineWidth = 1.5;
        ctx.stroke();

        // Y-axis labels
        ctx.fillStyle = 'rgba(76,175,80,0.6)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'right';
        const mid = Math.round((minE + maxE) / 2);
        [Math.round(maxE), mid, Math.round(minE)].forEach(v =>
            ctx.fillText(v + 'm', mL - 4, yOf(v) + 4));

        // Hover
        if (this.hoverIdx !== null) {
            const hx = xOf(this.hoverIdx);
            this.drawCrosshair(ctx, hx, top, bottom);
            const e = this.records[this.hoverIdx].elevation;
            if (e != null) this.drawDot(ctx, hx, yOf(e), '#4caf50');
        }
    }

    // ── X Axis ───────────────────────────────────────────────────────────

    private drawXAxis(): void {
        const c = this.xRef?.nativeElement;
        if (!c || !this.records.length) return;
        const dpr = window.devicePixelRatio || 1;
        const W = c.offsetWidth || 600;
        const H = c.offsetHeight || 22;
        c.width = Math.round(W * dpr);
        c.height = Math.round(H * dpr);
        const ctx = c.getContext('2d')!;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);

        const { mL, mR } = this.margins(W);
        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const cW = W - mL - mR;

        const tick = this.pickTickInterval(totalSec);
        ctx.fillStyle = this._textAlpha40;
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        for (let s = 0; s <= totalSec; s += tick) {
            const x = mL + (s / totalSec) * cW;
            ctx.fillText(`${Math.round(s / 60)}m`, x, 14);
        }

        // Hover line continuation
        if (this.hoverIdx !== null) {
            const hx = mL + ((this.records[this.hoverIdx].timestamp - t0) / totalSec) * cW;
            ctx.save();
            ctx.strokeStyle = this._crosshairAlpha;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(hx, 0); ctx.lineTo(hx, 4);
            ctx.stroke();
            ctx.restore();
        }
    }

    // ── Stepped line helpers (zone blocks / planned blocks) ──────────────

    private drawSteppedLine(
        ctx: CanvasRenderingContext2D,
        xOf: (i: number) => number,
        yOf: (v: number) => number,
        blocks: Array<{ s: number; e: number; v: number }>,
        color: string,
    ): void {
        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.beginPath();
        let first = true;
        for (const b of blocks) {
            const x1 = xOf(b.s), x2 = xOf(b.e), y = yOf(b.v);
            if (first) { ctx.moveTo(x1, y); first = false; }
            else ctx.lineTo(x1, y);
            ctx.lineTo(x2, y);
        }
        ctx.stroke();
    }

    private drawSteppedBlockLine(
        ctx: CanvasRenderingContext2D,
        xOfT: (sec: number) => number,
        yOf: (v: number) => number,
        blocks: Array<{ dur: number; v: number }>,
        color: string,
    ): void {
        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.beginPath();
        let first = true;
        let t = 0;
        for (const b of blocks) {
            const x1 = xOfT(t), x2 = xOfT(t + b.dur), y = yOf(b.v);
            if (first) { ctx.moveTo(x1, y); first = false; }
            else ctx.lineTo(x1, y);
            ctx.lineTo(x2, y);
            t += b.dur;
        }
        ctx.stroke();
    }

    // ── Tooltip ──────────────────────────────────────────────────────────

    private buildTooltip(): void {
        if (this.hoverIdx === null) { this.ttRows = []; return; }
        const rec = this.records[this.hoverIdx];
        const t0 = this.records[0].timestamp;
        const accent = this._accentHex;

        // Block context
        let blockLabel: string | null = null;
        let blockDuration: number | null = null;
        let bp: number | null = null, bpMax: number | null = null;
        let bhr: number | null = null, bcad: number | null = null;
        if (this.useZB) {
            const zb = this.zoneBlocks.find(b => this.hoverIdx! >= b.startIndex && this.hoverIdx! <= b.endIndex);
            if (zb) {
                bp = this.isCycling ? zb.avgPower : zb.avgSpeed;
                bpMax = this.isCycling ? zb.maxPower : zb.maxSpeed;
                bhr = zb.avgHR;
                bcad = this.getCadBlock(zb.avgCadence);
                blockLabel = `${zb.zoneLabel} · ${zb.zoneDescription}`;
                blockDuration = this.records[zb.endIndex].timestamp - this.records[zb.startIndex].timestamp;
            }
        } else if (this.usePB) {
            const elapsed = rec.timestamp - t0;
            let acc = 0;
            for (const b of this.blockSummaries) {
                if (elapsed >= acc && elapsed < acc + b.durationSeconds) {
                    bp = this.isCycling ? b.actualPower
                        : (b.distanceMeters && b.durationSeconds > 0 ? (b.distanceMeters / b.durationSeconds) * 3.6 : 0);
                    bhr = b.actualHR;
                    bcad = this.getCadBlock(b.actualCadence);
                    blockLabel = b.label;
                    blockDuration = b.durationSeconds;
                    break;
                }
                acc += b.durationSeconds;
            }
        }
        const inBlock = (this.useZB || this.usePB) && bp !== null;

        // Header
        const elapsed = rec.timestamp - t0;
        const em = Math.floor(elapsed / 60);
        const es = elapsed % 60;
        const elapsedStr = `${em}:${String(es).padStart(2, '0')}`;
        this.ttHeader = blockLabel ?? elapsedStr;

        // Rows
        const rows: Array<{ label: string; value: string; color: string }> = [];
        if (blockLabel) {
            rows.push({ label: 'Time', value: elapsedStr, color: 'var(--text-color)' });
        }
        if (inBlock && blockDuration != null) {
            const dm = Math.floor(blockDuration / 60);
            const ds = Math.round(blockDuration % 60);
            rows.push({ label: 'Duration', value: `${dm}:${String(ds).padStart(2, '0')}`, color: 'var(--text-color)' });
        }
        if (this.showPrimary) {
            if (inBlock) {
                if (this.isCycling) {
                    rows.push({ label: 'Avg Power', value: `${Math.round(bp!)}W`, color: accent });
                    if (bpMax) rows.push({ label: 'Max Power', value: `${Math.round(bpMax)}W`, color: accent });
                } else {
                    rows.push({ label: 'Avg Speed', value: `${bp!.toFixed(1)} km/h`, color: accent });
                    if (bpMax) rows.push({ label: 'Max Speed', value: `${bpMax.toFixed(1)} km/h`, color: accent });
                }
            } else {
                if (this.isCycling) rows.push({ label: 'Power', value: `${Math.round(rec.power)}W`, color: accent });
                else rows.push({ label: 'Speed', value: `${((rec.speed || 0) * 3.6).toFixed(1)} km/h`, color: accent });
            }
        }
        if (this.showHR) {
            const hr = inBlock ? bhr : rec.heartRate;
            if (hr) rows.push({ label: inBlock ? 'Avg HR' : 'HR', value: `${Math.round(hr)} bpm`, color: '#e74c3c' });
        }
        if (this.showCadence) {
            const cad = inBlock ? bcad : this.getCad(rec);
            if (cad) rows.push({ label: inBlock ? 'Avg Cad' : 'Cadence', value: `${Math.round(cad)} ${this.cadUnit}`, color: '#3b82f6' });
        }
        if (this._hasElevation && rec.elevation != null) {
            rows.push({ label: 'Elevation', value: `${Math.round(rec.elevation)}m`, color: '#4caf50' });
        }
        this.ttRows = rows;
    }

    // ── Hover value helpers (block-aware) ───────────────────────────────

    private findPlannedBlock(idx: number, t0: number): BlockSummary | null {
        const elapsed = this.records[idx].timestamp - t0;
        let acc = 0;
        for (const b of this.blockSummaries) {
            if (elapsed >= acc && elapsed < acc + b.durationSeconds) return b;
            acc += b.durationSeconds;
        }
        return null;
    }

    private hoverPrimaryValue(idx: number, t0: number): number {
        if (this.useZB) {
            const zb = this.zoneBlocks.find(b => idx >= b.startIndex && idx <= b.endIndex);
            if (zb) return this.isCycling ? zb.avgPower : zb.avgSpeed;
        } else if (this.usePB) {
            const pb = this.findPlannedBlock(idx, t0);
            if (pb) return this.isCycling ? pb.actualPower
                : (pb.distanceMeters && pb.durationSeconds > 0 ? (pb.distanceMeters / pb.durationSeconds) * 3.6 : 0);
        }
        const rec = this.records[idx];
        return this.isCycling ? rec.power : (rec.speed || 0) * 3.6;
    }

    private hoverHR(idx: number, t0: number): number {
        if (this.useZB) {
            const zb = this.zoneBlocks.find(b => idx >= b.startIndex && idx <= b.endIndex);
            if (zb) return zb.avgHR;
        } else if (this.usePB) {
            const pb = this.findPlannedBlock(idx, t0);
            if (pb) return pb.actualHR;
        }
        return this.records[idx].heartRate;
    }

    private hoverCadence(idx: number, t0: number): number {
        if (this.useZB) {
            const zb = this.zoneBlocks.find(b => idx >= b.startIndex && idx <= b.endIndex);
            if (zb) return this.getCadBlock(zb.avgCadence);
        } else if (this.usePB) {
            const pb = this.findPlannedBlock(idx, t0);
            if (pb) return this.getCadBlock(pb.actualCadence);
        }
        return this.getCad(this.records[idx]);
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private rollingAvg(data: number[], window: number): number[] {
        return data.map((_, i) => {
            const start = Math.max(0, i - Math.floor(window / 2));
            const end = Math.min(data.length, i + Math.ceil(window / 2));
            const slice = data.slice(start, end);
            return slice.reduce((a, b) => a + b, 0) / slice.length;
        });
    }

    private pickTickInterval(totalSec: number): number {
        const targets = [60, 300, 600, 900, 1800, 3600];
        const desired = totalSec / 8;
        return targets.reduce((a, b) => Math.abs(a - desired) < Math.abs(b - desired) ? a : b);
    }
}

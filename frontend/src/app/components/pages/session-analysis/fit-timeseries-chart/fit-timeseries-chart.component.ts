import {AfterViewInit, Component, ElementRef, Input, OnChanges, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FitRecord} from '../../../../services/metrics.service';
import {BlockSummary} from '../../../../services/workout-execution.service';
import {ZoneBlock} from '../session-analysis.component';

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
                        (mousemove)="onHover($event)"></canvas>
                }
                @if (showHR) {
                    <canvas #hrCanvas class="mc hr-h"
                        (mousemove)="onHover($event)"></canvas>
                }
                @if (showCadence) {
                    <canvas #cadCanvas class="mc cad-h"
                        (mousemove)="onHover($event)"></canvas>
                }
                @if (_hasElevation) {
                    <canvas #elevCanvas class="mc elev-h"
                        (mousemove)="onHover($event)"></canvas>
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
        .chart-toggles { display: flex; gap: 8px; padding: 0 4px; flex-wrap: wrap; }
        .toggle-btn {
            display: flex; align-items: center; gap: 5px;
            background: rgba(255,255,255,0.04);
            border: 1px solid rgba(255,255,255,0.08);
            color: var(--text-muted);
            padding: 4px 10px; border-radius: 6px;
            font-size: 10px; font-weight: 600; letter-spacing: 0.05em;
            cursor: pointer; transition: all 0.15s;
        }
        .toggle-btn.active {
            background: rgba(255,255,255,0.08);
            border-color: rgba(255,255,255,0.18);
            color: #fff;
        }
        .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
        .dot.power { background: var(--accent-color, #ff9d00); }
        .dot.hr { background: #e74c3c; }
        .dot.cad { background: #3b82f6; }
        .dot.blocks { background: #2ecc71; }
        .toggle-sep { width: 1px; height: 16px; background: rgba(255,255,255,0.1); margin: 0 2px; }
        .charts-stack { position: relative; display: flex; flex-direction: column; gap: 4px; }
        .mc { width: 100%; display: block; cursor: crosshair; }
        .primary-h { height: 150px; }
        .hr-h { height: 80px; }
        .cad-h { height: 80px; }
        .elev-h { height: 60px; }
        .xaxis-h { height: 22px; cursor: default; }
        .tt {
            position: absolute; z-index: 10; pointer-events: none;
            background: rgba(32, 34, 52, 0.97);
            border: 1px solid rgba(255,255,255,0.22);
            border-radius: 8px; padding: 8px 10px;
            min-width: 120px; white-space: nowrap;
        }
        .tt-hdr { color: rgba(255,255,255,0.8); font: 9px monospace; }
        .tt-sep { height: 1px; background: rgba(255,255,255,0.15); margin: 5px 0; }
        .tt-row { display: flex; justify-content: space-between; gap: 16px; line-height: 17px; }
        .tt-lbl { color: rgba(255,255,255,0.65); font: 9px monospace; }
        .tt-val { font: bold 10px monospace; text-align: right; }
    `],
})
export class FitTimeseriesChartComponent implements OnChanges, AfterViewInit {
    @Input() records: FitRecord[] = [];
    @Input() ftp: number | null = null;
    @Input() sportType = 'CYCLING';
    @Input() blockSummaries: BlockSummary[] = [];
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
    private readonly ML = 48;
    private readonly MR = 48;

    get isCycling(): boolean { return this.sportType === 'CYCLING'; }
    get primaryLabel(): string { return this.isCycling ? 'Power' : 'Speed'; }
    private get cadUnit(): string { return this.sportType === 'RUNNING' ? 'spm' : 'rpm'; }

    private getCad(r: FitRecord): number {
        return this.sportType === 'RUNNING' ? r.cadence * 2 : r.cadence;
    }

    private getCadBlock(c: number): number {
        return this.sportType === 'RUNNING' ? c * 2 : c;
    }

    ngAfterViewInit(): void {
        this.ready = true;
        this.drawAll();
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
        if (!canvas || this.records.length < 2) return;

        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const mouseX = (event.clientX - rect.left) * scaleX;

        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const cW = canvas.width - this.ML - this.MR;

        const targetT = t0 + ((mouseX - this.ML) / cW) * totalSec;
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
        const rawX = event.clientX - stackRect.left + 14;
        const stackW = stackRect.width;
        this.ttX = rawX + 160 > stackW ? rawX - 174 : rawX;
        this.ttY = event.clientY - stackRect.top - 10;

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
        mT: number; mB: number;
    } | null {
        const c = ref?.nativeElement;
        if (!c) return null;
        const W = (c.width = c.offsetWidth || 600);
        const H = (c.height = c.offsetHeight || 100);
        const ctx = c.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);
        if (!this.records.length) return null;

        const mL = this.ML, mR = this.MR, mT = 6, mB = 6;
        const cW = W - mL - mR;
        const t0 = this.records[0].timestamp;
        const n = this.records.length;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const xOf = (i: number) => mL + ((this.records[i].timestamp - t0) / totalSec) * cW;
        const xOfT = (sec: number) => mL + (sec / totalSec) * cW;
        return { ctx, W, H, cW, xOf, xOfT, mT, mB };
    }

    private drawCrosshair(ctx: CanvasRenderingContext2D, x: number, top: number, bottom: number): void {
        if (this.hoverIdx === null) return;
        ctx.save();
        ctx.strokeStyle = 'rgba(255,255,255,0.25)';
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
        ctx.strokeStyle = 'rgba(255,255,255,0.8)';
        ctx.lineWidth = 1.5;
        ctx.stroke();
    }

    private drawBlockBounds(ctx: CanvasRenderingContext2D, xOfT: (s: number) => number, top: number, bottom: number, totalSec: number): void {
        if (!this.blockSummaries.length || this.showBlocks) return;
        ctx.save();
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = 'rgba(255,255,255,0.12)';
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
        const { ctx, W, H, cW, xOf, xOfT, mT, mB } = s;
        const mL = this.ML, mR = this.MR;
        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
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
                ctx.fillStyle = b.color + '40';
                ctx.fillRect(x1, y, x2 - x1, bottom - y);
                ctx.strokeStyle = b.color;
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(x1, y); ctx.lineTo(x2, y);
                ctx.stroke();
            }
        } else if (this.usePB) {
            let acc = 0;
            for (const b of this.blockSummaries) {
                const x1 = xOfT(acc), x2 = xOfT(acc + b.durationSeconds);
                const val = this.isCycling ? b.actualPower
                    : (b.distanceMeters && b.durationSeconds > 0 ? (b.distanceMeters / b.durationSeconds) * 3.6 : 0);
                const y = yOf(val);
                if (b.targetPower > 0) {
                    const yt = yOf(b.targetPower);
                    ctx.save(); ctx.setLineDash([3, 3]);
                    ctx.strokeStyle = 'rgba(255,255,255,0.2)'; ctx.lineWidth = 1;
                    ctx.beginPath(); ctx.moveTo(x1, yt); ctx.lineTo(x2, yt); ctx.stroke();
                    ctx.restore();
                }
                ctx.fillStyle = accent + '30';
                ctx.fillRect(x1, y, x2 - x1, bottom - y);
                ctx.strokeStyle = accent; ctx.lineWidth = 2;
                ctx.beginPath(); ctx.moveTo(x1, y); ctx.lineTo(x2, y); ctx.stroke();
                acc += b.durationSeconds;
            }
        } else {
            if (this.isCycling) {
                const sm = this.rollingAvg(this.records.map(r => r.power), 5);
                if (this.ftp) {
                    const fy = yOf(this.ftp);
                    ctx.save(); ctx.setLineDash([4, 4]);
                    ctx.strokeStyle = 'rgba(255,255,255,0.15)'; ctx.lineWidth = 1;
                    ctx.beginPath(); ctx.moveTo(mL, fy); ctx.lineTo(W - mR, fy); ctx.stroke();
                    ctx.restore();
                    ctx.fillStyle = 'rgba(255,255,255,0.3)';
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
                    g.addColorStop(0, accent + '80'); g.addColorStop(1, accent + '08');
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
                    g.addColorStop(0, accent + '80'); g.addColorStop(1, accent + '08');
                    ctx.fillStyle = g; ctx.fill();
                    ctx.beginPath();
                    ctx.moveTo(xOf(0), yOf(sm[0]));
                    sm.forEach((v, i) => ctx.lineTo(xOf(i), yOf(v)));
                    ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.stroke();
                }
            }
        }

        // Y-axis labels
        ctx.fillStyle = 'rgba(255,255,255,0.4)';
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
        const { ctx, W, H, cW, xOf, xOfT, mT, mB } = s;
        const mL = this.ML, mR = this.MR;
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
        const { ctx, W, H, cW, xOf, xOfT, mT, mB } = s;
        const mL = this.ML, mR = this.MR;
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
        const { ctx, W, H, cW, xOf, xOfT, mT, mB } = s;
        const mL = this.ML;
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
        const W = (c.width = c.offsetWidth || 600);
        const H = (c.height = c.offsetHeight || 22);
        const ctx = c.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);

        const mL = this.ML, mR = this.MR;
        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const cW = W - mL - mR;

        const tick = this.pickTickInterval(totalSec);
        ctx.fillStyle = 'rgba(255,255,255,0.4)';
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
            ctx.strokeStyle = 'rgba(255,255,255,0.25)';
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
        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';

        // Block context
        let blockLabel: string | null = null;
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
                    break;
                }
                acc += b.durationSeconds;
            }
        }
        const inBlock = (this.useZB || this.usePB) && bp !== null;

        // Header
        if (blockLabel) {
            this.ttHeader = blockLabel;
        } else {
            const elapsed = rec.timestamp - t0;
            const m = Math.floor(elapsed / 60);
            const s = elapsed % 60;
            this.ttHeader = `${m}:${String(s).padStart(2, '0')}`;
        }

        // Rows
        const rows: Array<{ label: string; value: string; color: string }> = [];
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

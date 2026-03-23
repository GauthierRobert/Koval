import {AfterViewInit, Component, ElementRef, Input, OnChanges, ViewChild,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FitRecord} from '../../../../services/metrics.service';
import {BlockSummary} from '../../../../services/workout-execution.service';
import {ZoneBlock} from '../session-analysis.component';

interface Section {
    key: string;
    top: number;
    bottom: number;
    height: number;
    yOf: (v: number) => number;
    minVal: number;
    maxVal: number;
    color: string;
    unit: string;
}

@Component({
    selector: 'app-fit-timeseries-chart',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="chart-wrap">
            <div class="chart-toggles">
                <button class="toggle-btn" [class.active]="showPrimary" (click)="showPrimary = !showPrimary; draw()">
                    <span class="dot power"></span> {{ primaryLabel }}
                </button>
                <button class="toggle-btn" [class.active]="showHR" (click)="showHR = !showHR; draw()">
                    <span class="dot hr"></span> Heart Rate
                </button>
                <button class="toggle-btn" [class.active]="showCadence" (click)="showCadence = !showCadence; draw()">
                    <span class="dot cad"></span> Cadence
                </button>
                @if (zoneBlocks.length > 0 || blockSummaries.length > 0) {
                    <span class="toggle-sep"></span>
                    <button class="toggle-btn" [class.active]="showBlocks" (click)="showBlocks = !showBlocks; draw()">
                        <span class="dot blocks"></span> Blocks
                    </button>
                }
            </div>
            <canvas #canvas class="chart-canvas"
                [style.height.px]="computedHeight"
                (mousemove)="onMouseMove($event)"
                (mouseleave)="onMouseLeave()">
            </canvas>
        </div>
    `,
    styles: [`
        .chart-wrap { display: flex; flex-direction: column; gap: 8px; }
        .chart-toggles { display: flex; gap: 8px; padding: 0 4px; }
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
        .dot {
            width: 8px; height: 8px; border-radius: 50%;
            display: inline-block;
        }
        .dot.power { background: var(--accent-color, #ff9d00); }
        .dot.hr { background: #e74c3c; }
        .dot.cad { background: #3b82f6; }
        .dot.blocks { background: #2ecc71; }
        .toggle-sep {
            width: 1px; height: 16px;
            background: rgba(255,255,255,0.1);
            margin: 0 2px;
        }
        .chart-canvas { width: 100%; display: block; cursor: crosshair; }
    `],
})
export class FitTimeseriesChartComponent implements OnChanges, AfterViewInit {
    @Input() records: FitRecord[] = [];
    @Input() ftp: number | null = null;
    @Input() sportType = 'CYCLING';
    @Input() blockSummaries: BlockSummary[] = [];
    @Input() zoneBlocks: ZoneBlock[] = [];

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

    showPrimary = true;
    showHR = true;
    showCadence = false;
    showBlocks = false;

    private ready = false;
    private hoverIdx: number | null = null;
    private _hasElevation = false;

    private readonly ML = 48;
    private readonly MR = 48;
    private readonly MT = 12;
    private readonly MB = 28;
    private readonly GAP = 6;

    get isCycling(): boolean { return this.sportType === 'CYCLING'; }
    get primaryLabel(): string { return this.isCycling ? 'Power' : 'Speed'; }
    get cadenceUnit(): string { return this.sportType === 'RUNNING' ? 'spm' : 'rpm'; }

    get hasElevation(): boolean { return this._hasElevation; }

    get computedHeight(): number {
        let h = this.MT + this.MB;
        let sections = 0;
        if (this.showPrimary) { h += 150; sections++; }
        if (this.showHR) { h += 80; sections++; }
        if (this.showCadence) { h += 80; sections++; }
        if (this._hasElevation) { h += 60; sections++; }
        if (sections > 1) h += (sections - 1) * this.GAP;
        return Math.max(h, 100);
    }

    private getCadence(record: FitRecord): number {
        return this.sportType === 'RUNNING' ? record.cadence * 2 : record.cadence;
    }

    private getCadenceFromBlock(cadence: number): number {
        return this.sportType === 'RUNNING' ? cadence * 2 : cadence;
    }

    ngAfterViewInit(): void {
        this.ready = true;
        this.draw();
    }

    ngOnChanges(): void {
        this.updateHasElevation();
        if (this.ready) this.draw();
    }

    private updateHasElevation(): void {
        if (!this.records.length) { this._hasElevation = false; return; }
        const vals = this.records.filter(r => r.elevation != null).map(r => r.elevation!);
        this._hasElevation = vals.length >= 2 && vals.some(v => v !== vals[0]);
    }

    onMouseMove(event: MouseEvent): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas || this.records.length < 2) return;

        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const mouseX = (event.clientX - rect.left) * scaleX;

        const cW = canvas.width - this.ML - this.MR;
        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;

        const targetT = t0 + ((mouseX - this.ML) / cW) * totalSec;
        let lo = 0, hi = n - 1;
        while (lo < hi) {
            const mid = (lo + hi) >> 1;
            if (this.records[mid].timestamp < targetT) lo = mid + 1;
            else hi = mid;
        }
        const clamped = (lo > 0 && Math.abs(this.records[lo - 1].timestamp - targetT) < Math.abs(this.records[lo].timestamp - targetT))
            ? lo - 1 : lo;

        if (clamped !== this.hoverIdx) {
            this.hoverIdx = clamped;
            this.draw();
        }
    }

    onMouseLeave(): void {
        this.hoverIdx = null;
        this.draw();
    }

    draw(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas) return;

        const W = (canvas.width = canvas.offsetWidth || 600);
        const H = (canvas.height = canvas.offsetHeight || 200);
        const ctx = canvas.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);

        if (!this.records.length) return;

        const mL = this.ML, mR = this.MR, mT = this.MT, mB = this.MB, gap = this.GAP;
        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
        const n = this.records.length;
        const t0 = this.records[0].timestamp;
        const totalSec = this.records[n - 1].timestamp - t0 || n;
        const cW = W - mL - mR;
        const xOf = (i: number) => mL + ((this.records[i].timestamp - t0) / totalSec) * cW;
        const xOfTime = (sec: number) => mL + (sec / totalSec) * cW;

        // ── Build sections ───────────────────────────────────────────────────
        const sections = new Map<string, Section>();
        let curY = mT;

        // Primary section
        let primarySection: Section | null = null;
        let maxP = 1, maxS = 1;
        if (this.showPrimary) {
            const h = 150;
            const top = curY;
            const bottom = curY + h;
            if (this.isCycling) {
                const smoothed = this.rollingAvg(this.records.map(r => r.power), 5);
                maxP = Math.max(this.ftp ? this.ftp * 1.5 : 0, ...smoothed) || 1;
                primarySection = {
                    key: 'primary', top, bottom, height: h,
                    yOf: (v: number) => top + h * (1 - v / maxP),
                    minVal: 0, maxVal: maxP, color: accent, unit: 'W',
                };
            } else {
                const speeds = this.records.map(r => (r.speed || 0) * 3.6);
                const smoothedS = this.rollingAvg(speeds, 5);
                maxS = Math.max(...smoothedS.filter(v => v > 0), 1);
                primarySection = {
                    key: 'primary', top, bottom, height: h,
                    yOf: (v: number) => top + h * (1 - v / maxS),
                    minVal: 0, maxVal: maxS, color: accent, unit: 'km/h',
                };
            }
            sections.set('primary', primarySection);
            curY = bottom + gap;
        }

        // HR section
        let hrSection: Section | null = null;
        if (this.showHR) {
            const h = 80;
            const top = curY;
            const bottom = curY + h;
            const hrs = this.records.map(r => r.heartRate).filter(v => v > 0);
            const minHR = 100;
            const maxHR = hrs.length ? Math.max(Math.max(...hrs) * 1.05, minHR + 10) : 220;
            hrSection = {
                key: 'hr', top, bottom, height: h,
                yOf: (hr: number) => top + h * (1 - (Math.max(hr, minHR) - minHR) / (maxHR - minHR)),
                minVal: minHR, maxVal: maxHR, color: '#e74c3c', unit: 'bpm',
            };
            sections.set('hr', hrSection);
            curY = bottom + gap;
        }

        // Cadence section
        let cadSection: Section | null = null;
        if (this.showCadence) {
            const h = 80;
            const top = curY;
            const bottom = curY + h;
            const cads = this.records.map(r => this.getCadence(r)).filter(v => v > 0);
            const minCad = this.sportType === 'RUNNING' ? 140 : 40;
            const maxCad = cads.length ? Math.max(Math.max(...cads) * 1.05, minCad + 10) : 120;
            cadSection = {
                key: 'cadence', top, bottom, height: h,
                yOf: (c: number) => top + h * (1 - (Math.max(c, minCad) - minCad) / (maxCad - minCad)),
                minVal: minCad, maxVal: maxCad, color: '#3b82f6', unit: this.cadenceUnit,
            };
            sections.set('cadence', cadSection);
            curY = bottom + gap;
        }

        // Elevation section
        let elevSection: Section | null = null;
        if (this._hasElevation) {
            const h = 60;
            const top = curY;
            const bottom = curY + h;
            const elevs = this.records.filter(r => r.elevation != null).map(r => r.elevation!);
            const minElev = Math.min(...elevs) - 5;
            const maxElev = Math.max(...elevs) + 5;
            const range = maxElev - minElev || 1;
            elevSection = {
                key: 'elevation', top, bottom, height: h,
                yOf: (e: number) => top + h * (1 - (e - minElev) / range),
                minVal: minElev, maxVal: maxElev, color: 'rgba(76,175,80,0.6)', unit: 'm',
            };
            sections.set('elevation', elevSection);
            curY = bottom + gap;
        }

        const useZoneBlocks = this.showBlocks && this.zoneBlocks.length > 0;
        const usePlannedBlocks = this.showBlocks && !useZoneBlocks && this.blockSummaries.length > 0;
        const useBlocks = useZoneBlocks || usePlannedBlocks;

        // ── Section separators ───────────────────────────────────────────────
        const sectionList = [...sections.values()];
        for (let i = 0; i < sectionList.length - 1; i++) {
            const sepY = sectionList[i].bottom + Math.floor(gap / 2);
            ctx.fillStyle = 'rgba(255,255,255,0.06)';
            ctx.fillRect(mL, sepY, cW, 1);
        }

        // ══════════════════════════════════════════════════════════════════════
        // PRIMARY SECTION
        // ══════════════════════════════════════════════════════════════════════
        if (primarySection) {
            const s = primarySection;
            if (useZoneBlocks) {
                if (this.showPrimary) {
                    for (const b of this.zoneBlocks) {
                        const x1 = xOf(b.startIndex);
                        const x2 = xOf(b.endIndex);
                        const val = this.isCycling ? b.avgPower : b.avgSpeed;
                        const y = s.yOf(val);
                        ctx.fillStyle = b.color + '40';
                        ctx.fillRect(x1, y, x2 - x1, s.bottom - y);
                        ctx.strokeStyle = b.color;
                        ctx.lineWidth = 2;
                        ctx.beginPath();
                        ctx.moveTo(x1, y); ctx.lineTo(x2, y);
                        ctx.stroke();
                    }
                }
            } else if (usePlannedBlocks) {
                if (this.showPrimary) {
                    let accTime = 0;
                    for (const b of this.blockSummaries) {
                        const x1 = xOfTime(accTime);
                        const x2 = xOfTime(accTime + b.durationSeconds);
                        const val = this.isCycling ? b.actualPower
                            : (b.distanceMeters && b.durationSeconds > 0 ? (b.distanceMeters / b.durationSeconds) * 3.6 : 0);
                        const y = s.yOf(val);
                        if (b.targetPower > 0) {
                            const yTarget = s.yOf(b.targetPower);
                            ctx.save();
                            ctx.setLineDash([3, 3]);
                            ctx.strokeStyle = 'rgba(255,255,255,0.2)';
                            ctx.lineWidth = 1;
                            ctx.beginPath();
                            ctx.moveTo(x1, yTarget); ctx.lineTo(x2, yTarget);
                            ctx.stroke();
                            ctx.restore();
                        }
                        ctx.fillStyle = accent + '30';
                        ctx.fillRect(x1, y, x2 - x1, s.bottom - y);
                        ctx.strokeStyle = accent;
                        ctx.lineWidth = 2;
                        ctx.beginPath();
                        ctx.moveTo(x1, y); ctx.lineTo(x2, y);
                        ctx.stroke();
                        accTime += b.durationSeconds;
                    }
                }
            } else {
                // Real data
                if (this.isCycling) {
                    const smoothed = this.rollingAvg(this.records.map(r => r.power), 5);

                    if (this.ftp) {
                        const ftpY = s.yOf(this.ftp);
                        ctx.save();
                        ctx.setLineDash([4, 4]);
                        ctx.strokeStyle = 'rgba(255,255,255,0.15)';
                        ctx.lineWidth = 1;
                        ctx.beginPath();
                        ctx.moveTo(mL, ftpY); ctx.lineTo(W - mR, ftpY);
                        ctx.stroke();
                        ctx.restore();
                        ctx.fillStyle = 'rgba(255,255,255,0.3)';
                        ctx.font = '9px monospace';
                        ctx.textAlign = 'left';
                        ctx.fillText('FTP', mL + 2, ftpY - 3);
                    }

                    if (this.showPrimary && smoothed.length > 1) {
                        ctx.beginPath();
                        ctx.moveTo(xOf(0), s.bottom);
                        smoothed.forEach((p, i) => ctx.lineTo(xOf(i), s.yOf(p)));
                        ctx.lineTo(xOf(n - 1), s.bottom);
                        ctx.closePath();
                        const grad = ctx.createLinearGradient(0, s.top, 0, s.bottom);
                        grad.addColorStop(0, accent + '80');
                        grad.addColorStop(1, accent + '08');
                        ctx.fillStyle = grad;
                        ctx.fill();

                        ctx.beginPath();
                        ctx.moveTo(xOf(0), s.yOf(smoothed[0]));
                        smoothed.forEach((p, i) => ctx.lineTo(xOf(i), s.yOf(p)));
                        ctx.strokeStyle = accent;
                        ctx.lineWidth = 2;
                        ctx.stroke();
                    }
                } else {
                    const speeds = this.records.map(r => (r.speed || 0) * 3.6);
                    const smoothed = this.rollingAvg(speeds, 5);

                    if (this.showPrimary && smoothed.length > 1) {
                        ctx.beginPath();
                        ctx.moveTo(xOf(0), s.bottom);
                        smoothed.forEach((v, i) => ctx.lineTo(xOf(i), s.yOf(v)));
                        ctx.lineTo(xOf(n - 1), s.bottom);
                        ctx.closePath();
                        const grad = ctx.createLinearGradient(0, s.top, 0, s.bottom);
                        grad.addColorStop(0, accent + '80');
                        grad.addColorStop(1, accent + '08');
                        ctx.fillStyle = grad;
                        ctx.fill();

                        ctx.beginPath();
                        ctx.moveTo(xOf(0), s.yOf(smoothed[0]));
                        smoothed.forEach((v, i) => ctx.lineTo(xOf(i), s.yOf(v)));
                        ctx.strokeStyle = accent;
                        ctx.lineWidth = 2;
                        ctx.stroke();
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // HR SECTION
        // ══════════════════════════════════════════════════════════════════════
        if (hrSection) {
            const s = hrSection;
            if (useZoneBlocks) {
                ctx.strokeStyle = s.color;
                ctx.lineWidth = 2;
                ctx.beginPath();
                let first = true;
                for (const b of this.zoneBlocks) {
                    const x1 = xOf(b.startIndex);
                    const x2 = xOf(b.endIndex);
                    const y = s.yOf(b.avgHR);
                    if (first) { ctx.moveTo(x1, y); first = false; }
                    else ctx.lineTo(x1, y);
                    ctx.lineTo(x2, y);
                }
                ctx.stroke();
            } else if (usePlannedBlocks) {
                ctx.strokeStyle = s.color;
                ctx.lineWidth = 2;
                ctx.beginPath();
                let first = true;
                let t = 0;
                for (const b of this.blockSummaries) {
                    const x1 = xOfTime(t);
                    const x2 = xOfTime(t + b.durationSeconds);
                    const y = s.yOf(b.actualHR);
                    if (first) { ctx.moveTo(x1, y); first = false; }
                    else ctx.lineTo(x1, y);
                    ctx.lineTo(x2, y);
                    t += b.durationSeconds;
                }
                ctx.stroke();
            } else {
                ctx.beginPath();
                let first = true;
                this.records.forEach((r, i) => {
                    if (!r.heartRate) return;
                    const y = s.yOf(r.heartRate);
                    if (first) { ctx.moveTo(xOf(i), y); first = false; }
                    else ctx.lineTo(xOf(i), y);
                });
                ctx.strokeStyle = s.color;
                ctx.lineWidth = 1.5;
                ctx.stroke();
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // CADENCE SECTION
        // ══════════════════════════════════════════════════════════════════════
        if (cadSection) {
            const s = cadSection;
            if (useZoneBlocks) {
                ctx.strokeStyle = s.color;
                ctx.lineWidth = 2;
                ctx.beginPath();
                let first = true;
                for (const b of this.zoneBlocks) {
                    const x1 = xOf(b.startIndex);
                    const x2 = xOf(b.endIndex);
                    const y = s.yOf(this.getCadenceFromBlock(b.avgCadence));
                    if (first) { ctx.moveTo(x1, y); first = false; }
                    else ctx.lineTo(x1, y);
                    ctx.lineTo(x2, y);
                }
                ctx.stroke();
            } else if (usePlannedBlocks) {
                ctx.strokeStyle = s.color;
                ctx.lineWidth = 2;
                ctx.beginPath();
                let first = true;
                let t = 0;
                for (const b of this.blockSummaries) {
                    const x1 = xOfTime(t);
                    const x2 = xOfTime(t + b.durationSeconds);
                    const y = s.yOf(this.getCadenceFromBlock(b.actualCadence));
                    if (first) { ctx.moveTo(x1, y); first = false; }
                    else ctx.lineTo(x1, y);
                    ctx.lineTo(x2, y);
                    t += b.durationSeconds;
                }
                ctx.stroke();
            } else {
                ctx.beginPath();
                let first = true;
                this.records.forEach((r, i) => {
                    if (!r.cadence) return;
                    const y = s.yOf(this.getCadence(r));
                    if (first) { ctx.moveTo(xOf(i), y); first = false; }
                    else ctx.lineTo(xOf(i), y);
                });
                ctx.strokeStyle = s.color;
                ctx.lineWidth = 1.5;
                ctx.stroke();
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // ELEVATION SECTION
        // ══════════════════════════════════════════════════════════════════════
        if (elevSection) {
            const s = elevSection;
            // Filled area (mountain silhouette)
            ctx.beginPath();
            let started = false;
            let lastX = mL;
            this.records.forEach((r, i) => {
                if (r.elevation == null) return;
                const x = xOf(i);
                const y = s.yOf(r.elevation);
                if (!started) {
                    ctx.moveTo(x, s.bottom);
                    ctx.lineTo(x, y);
                    started = true;
                } else {
                    ctx.lineTo(x, y);
                }
                lastX = x;
            });
            if (started) {
                ctx.lineTo(lastX, s.bottom);
                ctx.closePath();
                ctx.fillStyle = 'rgba(76,175,80,0.18)';
                ctx.fill();
            }

            // Top edge stroke
            ctx.beginPath();
            let firstStroke = true;
            this.records.forEach((r, i) => {
                if (r.elevation == null) return;
                const x = xOf(i);
                const y = s.yOf(r.elevation);
                if (firstStroke) { ctx.moveTo(x, y); firstStroke = false; }
                else ctx.lineTo(x, y);
            });
            ctx.strokeStyle = 'rgba(76,175,80,0.6)';
            ctx.lineWidth = 1.5;
            ctx.stroke();
        }

        // ── Y-axis labels ────────────────────────────────────────────────────
        if (primarySection) {
            const s = primarySection;
            ctx.fillStyle = 'rgba(255,255,255,0.4)';
            ctx.font = '9px monospace';
            ctx.textAlign = 'right';
            if (this.isCycling) {
                [0, 0.5, 1].forEach(frac => {
                    const p = Math.round(maxP * frac);
                    ctx.fillText(String(p), mL - 4, s.yOf(p) + 4);
                });
            } else {
                [0, 0.5, 1].forEach(frac => {
                    const v = Math.round(maxS * frac * 10) / 10;
                    ctx.fillText(v + ' km/h', mL - 4, s.yOf(v) + 4);
                });
            }
        }

        if (hrSection) {
            const s = hrSection;
            ctx.fillStyle = s.color;
            ctx.font = '9px monospace';
            ctx.textAlign = 'right';
            const mid = Math.round((s.minVal + s.maxVal) / 2);
            [s.maxVal, mid, s.minVal].forEach(v => {
                ctx.fillText(String(Math.round(v)), mL - 4, s.yOf(v) + 4);
            });
        }

        if (cadSection) {
            const s = cadSection;
            ctx.fillStyle = s.color;
            ctx.font = '9px monospace';
            ctx.textAlign = 'left';
            const mid = Math.round((s.minVal + s.maxVal) / 2);
            [s.maxVal, mid, s.minVal].forEach(v => {
                ctx.fillText(String(Math.round(v)), W - mR + 4, s.yOf(v) + 4);
            });
        }

        if (elevSection) {
            const s = elevSection;
            ctx.fillStyle = 'rgba(76,175,80,0.6)';
            ctx.font = '9px monospace';
            ctx.textAlign = 'right';
            const mid = Math.round((s.minVal + s.maxVal) / 2);
            [s.maxVal, mid, s.minVal].forEach(v => {
                ctx.fillText(Math.round(v) + 'm', mL - 4, s.yOf(v) + 4);
            });
        }

        // ── Block boundary dashed lines ──────────────────────────────────────
        if (this.blockSummaries.length > 0 && !useBlocks) {
            ctx.save();
            ctx.setLineDash([4, 4]);
            ctx.strokeStyle = 'rgba(255,255,255,0.12)';
            ctx.lineWidth = 1;

            // Span from top of first section to bottom of last non-elevation section
            const nonElevSections = sectionList.filter(s => s.key !== 'elevation');
            const lineTop = sectionList.length ? sectionList[0].top : mT;
            const lineBottom = nonElevSections.length ? nonElevSections[nonElevSections.length - 1].bottom : mT;

            let accTime = 0;
            for (let i = 0; i < this.blockSummaries.length - 1; i++) {
                accTime += this.blockSummaries[i].durationSeconds;
                const x = xOfTime(accTime);
                ctx.beginPath();
                ctx.moveTo(x, lineTop);
                ctx.lineTo(x, lineBottom);
                ctx.stroke();
            }
            ctx.restore();
        }

        // ── X-axis labels (elapsed minutes) ──────────────────────────────────
        const tickEvery = this.pickTickInterval(totalSec);
        ctx.fillStyle = 'rgba(255,255,255,0.4)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        for (let s = 0; s <= totalSec; s += tickEvery) {
            const x = mL + (s / totalSec) * cW;
            ctx.fillText(`${Math.round(s / 60)}m`, x, H - 6);
        }

        // ── Hover crosshair + tooltip ────────────────────────────────────────
        if (this.hoverIdx === null || !sectionList.length) return;
        const rec = this.records[this.hoverIdx];
        const hx = xOf(this.hoverIdx);
        const crosshairTop = sectionList[0].top;
        const crosshairBottom = sectionList[sectionList.length - 1].bottom;

        // Vertical crosshair
        ctx.save();
        ctx.strokeStyle = 'rgba(255,255,255,0.25)';
        ctx.lineWidth = 1;
        ctx.setLineDash([]);
        ctx.beginPath();
        ctx.moveTo(hx, crosshairTop); ctx.lineTo(hx, crosshairBottom);
        ctx.stroke();
        ctx.restore();

        // Resolve block context
        let blockPrimary: number | null = null;
        let blockMaxPrimary: number | null = null;
        let blockHR: number | null = null;
        let blockCad: number | null = null;
        let blockLabel: string | null = null;

        if (useZoneBlocks) {
            const zb = this.zoneBlocks.find(b => this.hoverIdx! >= b.startIndex && this.hoverIdx! <= b.endIndex);
            if (zb) {
                blockPrimary = this.isCycling ? zb.avgPower : zb.avgSpeed;
                blockMaxPrimary = this.isCycling ? zb.maxPower : zb.maxSpeed;
                blockHR = zb.avgHR;
                blockCad = this.getCadenceFromBlock(zb.avgCadence);
                blockLabel = `${zb.zoneLabel} · ${zb.zoneDescription}`;
            }
        } else if (usePlannedBlocks) {
            const elapsed = rec.timestamp - t0;
            let accTime = 0;
            for (const b of this.blockSummaries) {
                if (elapsed >= accTime && elapsed < accTime + b.durationSeconds) {
                    blockPrimary = this.isCycling ? b.actualPower
                        : (b.distanceMeters && b.durationSeconds > 0 ? (b.distanceMeters / b.durationSeconds) * 3.6 : 0);
                    blockHR = b.actualHR;
                    blockCad = this.getCadenceFromBlock(b.actualCadence);
                    blockLabel = b.label;
                    break;
                }
                accTime += b.durationSeconds;
            }
        }

        const inBlock = useBlocks && blockPrimary !== null;

        // Dots
        const dots: Array<{ y: number; color: string }> = [];
        if (primarySection && this.showPrimary) {
            const val = inBlock ? blockPrimary! : (this.isCycling ? rec.power : (rec.speed || 0) * 3.6);
            dots.push({ y: primarySection.yOf(val), color: accent });
        }
        if (hrSection) {
            const val = inBlock ? blockHR! : rec.heartRate;
            if (val) dots.push({ y: hrSection.yOf(val), color: '#e74c3c' });
        }
        if (cadSection) {
            const val = inBlock ? blockCad! : this.getCadence(rec);
            if (val) dots.push({ y: cadSection.yOf(val), color: '#3b82f6' });
        }
        if (elevSection && rec.elevation != null) {
            dots.push({ y: elevSection.yOf(rec.elevation), color: '#4caf50' });
        }

        dots.forEach(({ y, color }) => {
            ctx.beginPath();
            ctx.arc(hx, y, 4, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            ctx.strokeStyle = 'rgba(255,255,255,0.8)';
            ctx.lineWidth = 1.5;
            ctx.stroke();
        });

        // Tooltip rows
        const rows: Array<{ label: string; value: string; color: string }> = [];
        if (inBlock) {
            if (this.showPrimary) {
                if (this.isCycling) {
                    rows.push({ label: 'Avg Power', value: `${Math.round(blockPrimary!)}W`, color: accent });
                    if (blockMaxPrimary) rows.push({ label: 'Max Power', value: `${Math.round(blockMaxPrimary)}W`, color: accent });
                } else {
                    rows.push({ label: 'Avg Speed', value: `${blockPrimary!.toFixed(1)} km/h`, color: accent });
                    if (blockMaxPrimary) rows.push({ label: 'Max Speed', value: `${blockMaxPrimary.toFixed(1)} km/h`, color: accent });
                }
            }
            if (this.showHR && blockHR) rows.push({ label: 'Avg HR', value: `${Math.round(blockHR)} bpm`, color: '#e74c3c' });
            if (this.showCadence && blockCad) rows.push({ label: 'Avg Cad', value: `${Math.round(blockCad)} ${this.cadenceUnit}`, color: '#3b82f6' });
        } else {
            if (this.showPrimary) {
                if (this.isCycling) rows.push({ label: 'Power', value: `${Math.round(rec.power)}W`, color: accent });
                else rows.push({ label: 'Speed', value: `${((rec.speed || 0) * 3.6).toFixed(1)} km/h`, color: accent });
            }
            if (this.showHR && rec.heartRate) rows.push({ label: 'HR', value: `${Math.round(rec.heartRate)} bpm`, color: '#e74c3c' });
            if (this.showCadence && rec.cadence) rows.push({ label: 'Cadence', value: `${Math.round(this.getCadence(rec))} ${this.cadenceUnit}`, color: '#3b82f6' });
        }
        if (this._hasElevation && rec.elevation != null) {
            rows.push({ label: 'Elevation', value: `${Math.round(rec.elevation)}m`, color: '#4caf50' });
        }

        if (rows.length === 0) return;

        const elapsed = this.records[this.hoverIdx].timestamp - t0;
        const em = Math.floor(elapsed / 60);
        const es = elapsed % 60;
        const timeStr = blockLabel ? blockLabel : `${em}:${String(es).padStart(2, '0')}`;

        const labelFont = inBlock ? '11px monospace' : '9px monospace';
        const valueFont = inBlock ? 'bold 12px monospace' : 'bold 10px monospace';
        const headerFont = inBlock ? '11px monospace' : '9px monospace';
        const pad = 10;
        const rowH = inBlock ? 22 : 18;
        const boxW = inBlock ? 180 : 140;
        const headerH = inBlock ? 22 : 18;
        const boxH = pad + headerH + rows.length * rowH + pad;

        let tx = hx + 14;
        if (tx + boxW > W - mR) tx = hx - boxW - 14;
        let ty = mT + 16;
        if (ty + boxH > crosshairBottom) ty = crosshairBottom - boxH;

        // Background
        ctx.save();
        ctx.fillStyle = 'rgba(32, 34, 52, 0.97)';
        ctx.strokeStyle = 'rgba(255,255,255,0.22)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        const r = 8;
        ctx.moveTo(tx + r, ty);
        ctx.lineTo(tx + boxW - r, ty);
        ctx.arcTo(tx + boxW, ty, tx + boxW, ty + r, r);
        ctx.lineTo(tx + boxW, ty + boxH - r);
        ctx.arcTo(tx + boxW, ty + boxH, tx + boxW - r, ty + boxH, r);
        ctx.lineTo(tx + r, ty + boxH);
        ctx.arcTo(tx, ty + boxH, tx, ty + boxH - r, r);
        ctx.lineTo(tx, ty + r);
        ctx.arcTo(tx, ty, tx + r, ty, r);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
        ctx.restore();

        // Time header
        ctx.fillStyle = 'rgba(255,255,255,0.8)';
        ctx.font = headerFont;
        ctx.textAlign = 'left';
        ctx.fillText(timeStr, tx + pad, ty + pad + (inBlock ? 10 : 8));

        // Separator
        ctx.fillStyle = 'rgba(255,255,255,0.15)';
        ctx.fillRect(tx + pad, ty + pad + (inBlock ? 15 : 13), boxW - pad * 2, 1);

        // Value rows
        rows.forEach((row, ri) => {
            const ry = ty + pad + headerH + ri * rowH + (inBlock ? 14 : 11);
            ctx.fillStyle = 'rgba(255,255,255,0.65)';
            ctx.font = labelFont;
            ctx.textAlign = 'left';
            ctx.fillText(row.label, tx + pad, ry);
            ctx.fillStyle = row.color;
            ctx.font = valueFont;
            ctx.textAlign = 'right';
            ctx.fillText(row.value, tx + boxW - pad, ry);
        });
    }

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

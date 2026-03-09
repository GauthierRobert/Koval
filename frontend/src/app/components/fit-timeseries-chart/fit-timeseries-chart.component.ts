import {
    Component,
    Input,
    OnChanges,
    AfterViewInit,
    ViewChild,
    ElementRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FitRecord } from '../../services/metrics.service';
import { BlockSummary } from '../../services/workout-execution.service';

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
            </div>
            <canvas #canvas class="chart-canvas"
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
        .chart-canvas { width: 100%; height: 200px; display: block; cursor: crosshair; }
    `],
})
export class FitTimeseriesChartComponent implements OnChanges, AfterViewInit {
    @Input() records: FitRecord[] = [];
    @Input() ftp: number | null = null;
    @Input() sportType = 'CYCLING';
    @Input() blockSummaries: BlockSummary[] = [];

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

    showPrimary = true;
    showHR = true;
    showCadence = false;

    private ready = false;
    private hoverIdx: number | null = null;

    private readonly ML = 48;
    private readonly MR = 48;

    get isCycling(): boolean { return this.sportType === 'CYCLING'; }
    get primaryLabel(): string { return this.isCycling ? 'Power' : 'Speed'; }

    ngAfterViewInit(): void {
        this.ready = true;
        this.draw();
    }

    ngOnChanges(): void {
        if (this.ready) this.draw();
    }

    onMouseMove(event: MouseEvent): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas || this.records.length < 2) return;

        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const mouseX = (event.clientX - rect.left) * scaleX;

        const cW = canvas.width - this.ML - this.MR;
        const n = this.records.length;
        const idx = Math.round((mouseX - this.ML) / cW * (n - 1));
        const clamped = Math.max(0, Math.min(n - 1, idx));

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

        const mL = this.ML, mR = this.MR, mT = 12, mB = 28;
        const cW = W - mL - mR;
        const cH = H - mT - mB;
        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
        const n = this.records.length;
        const xOf = (i: number) => mL + (i / (n - 1)) * cW;

        // Hoisted scale functions for reuse in tooltip
        let yOfPrimary: ((v: number) => number) | null = null;
        let maxHR = 220;
        let yOfHR: ((hr: number) => number) | null = null;
        let maxCad = 120;
        let yOfCad: ((c: number) => number) | null = null;

        if (this.isCycling) {
            // ── Primary metric: Power (W) ─────────────────────────────────────
            const smoothed = this.rollingAvg(this.records.map((r) => r.power), 5);
            const maxP = Math.max(this.ftp ? this.ftp * 1.5 : 0, ...smoothed) || 1;
            const yOfPow = (p: number) => mT + cH * (1 - p / maxP);
            yOfPrimary = yOfPow;

            // FTP reference line (only if FTP is set)
            if (this.ftp) {
                const ftpY = yOfPow(this.ftp);
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
                ctx.moveTo(xOf(0), H - mB);
                smoothed.forEach((p, i) => ctx.lineTo(xOf(i), yOfPow(p)));
                ctx.lineTo(xOf(n - 1), H - mB);
                ctx.closePath();
                const grad = ctx.createLinearGradient(0, mT, 0, H - mB);
                grad.addColorStop(0, accent + '80');
                grad.addColorStop(1, accent + '08');
                ctx.fillStyle = grad;
                ctx.fill();

                ctx.beginPath();
                ctx.moveTo(xOf(0), yOfPow(smoothed[0]));
                smoothed.forEach((p, i) => ctx.lineTo(xOf(i), yOfPow(p)));
                ctx.strokeStyle = accent;
                ctx.lineWidth = 2;
                ctx.stroke();
            }

            // Left Y-axis labels (watts)
            ctx.fillStyle = 'rgba(255,255,255,0.4)';
            ctx.font = '9px monospace';
            ctx.textAlign = 'right';
            [0, 0.5, 1].forEach((frac) => {
                const p = Math.round(maxP * frac);
                ctx.fillText(String(p), mL - 4, yOfPow(p) + 4);
            });

        } else {
            // ── Primary metric: Speed (km/h) for running/swimming ─────────────
            const speeds = this.records.map((r) => (r.speed || 0) * 3.6);
            const smoothed = this.rollingAvg(speeds, 5);
            const maxS = Math.max(...smoothed.filter((v) => v > 0), 1);
            const yOfSpd = (s: number) => mT + cH * (1 - s / maxS);
            yOfPrimary = yOfSpd;

            if (this.showPrimary && smoothed.length > 1) {
                ctx.beginPath();
                ctx.moveTo(xOf(0), H - mB);
                smoothed.forEach((s, i) => ctx.lineTo(xOf(i), yOfSpd(s)));
                ctx.lineTo(xOf(n - 1), H - mB);
                ctx.closePath();
                const grad = ctx.createLinearGradient(0, mT, 0, H - mB);
                grad.addColorStop(0, accent + '80');
                grad.addColorStop(1, accent + '08');
                ctx.fillStyle = grad;
                ctx.fill();

                ctx.beginPath();
                ctx.moveTo(xOf(0), yOfSpd(smoothed[0]));
                smoothed.forEach((s, i) => ctx.lineTo(xOf(i), yOfSpd(s)));
                ctx.strokeStyle = accent;
                ctx.lineWidth = 2;
                ctx.stroke();
            }

            // Left Y-axis labels (km/h)
            ctx.fillStyle = 'rgba(255,255,255,0.4)';
            ctx.font = '9px monospace';
            ctx.textAlign = 'right';
            [0, 0.5, 1].forEach((frac) => {
                const s = Math.round(maxS * frac * 10) / 10;
                ctx.fillText(s + ' km/h', mL - 4, yOfSpd(s) + 4);
            });
        }

        // ── HR line (right scale, all sports) ────────────────────────────────
        if (this.showHR) {
            const hrs = this.records.map((r) => r.heartRate).filter((v) => v > 0);
            maxHR = hrs.length ? Math.max(...hrs) * 1.05 : 220;
            yOfHR = (hr: number) => mT + cH * (1 - hr / maxHR);

            ctx.save();
            ctx.setLineDash([3, 3]);
            ctx.beginPath();
            let first = true;
            this.records.forEach((r, i) => {
                if (!r.heartRate) return;
                if (first) { ctx.moveTo(xOf(i), yOfHR!(r.heartRate)); first = false; }
                else ctx.lineTo(xOf(i), yOfHR!(r.heartRate));
            });
            ctx.strokeStyle = '#e74c3c';
            ctx.lineWidth = 1.5;
            ctx.stroke();
            ctx.restore();

            ctx.fillStyle = '#e74c3c';
            ctx.font = '9px monospace';
            ctx.textAlign = 'right';
            ctx.fillText('HR', W - 2, mT + 10);
        }

        // ── Cadence line (right scale, all sports) ───────────────────────────
        if (this.showCadence) {
            const cads = this.records.map((r) => r.cadence).filter((v) => v > 0);
            maxCad = cads.length ? Math.max(...cads) * 1.1 : 120;
            yOfCad = (c: number) => mT + cH * (1 - c / maxCad);

            ctx.save();
            ctx.setLineDash([2, 4]);
            ctx.beginPath();
            let first = true;
            this.records.forEach((r, i) => {
                if (!r.cadence) return;
                if (first) { ctx.moveTo(xOf(i), yOfCad!(r.cadence)); first = false; }
                else ctx.lineTo(xOf(i), yOfCad!(r.cadence));
            });
            ctx.strokeStyle = '#3b82f6';
            ctx.lineWidth = 1.5;
            ctx.stroke();
            ctx.restore();
        }

        // ── Block boundary dashed lines ──────────────────────────────────────
        if (this.blockSummaries.length > 1) {
            ctx.save();
            ctx.setLineDash([4, 4]);
            ctx.strokeStyle = 'rgba(255,255,255,0.12)';
            ctx.lineWidth = 1;

            let accTime = 0;
            const totalSec = n; // 1 record ≈ 1 second
            for (let i = 0; i < this.blockSummaries.length - 1; i++) {
                accTime += this.blockSummaries[i].durationSeconds;
                const x = mL + (accTime / totalSec) * cW;
                ctx.beginPath();
                ctx.moveTo(x, mT);
                ctx.lineTo(x, H - mB);
                ctx.stroke();
            }
            ctx.restore();
        }

        // ── X-axis labels (elapsed minutes) ──────────────────────────────────
        const totalSec = n;
        const tickEvery = this.pickTickInterval(totalSec);
        ctx.fillStyle = 'rgba(255,255,255,0.4)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        for (let s = 0; s <= totalSec; s += tickEvery) {
            const x = mL + (s / totalSec) * cW;
            ctx.fillText(`${Math.round(s / 60)}m`, x, H - 6);
        }

        // ── Hover crosshair + tooltip ─────────────────────────────────────────
        if (this.hoverIdx === null) return;
        const rec = this.records[this.hoverIdx];
        const hx = xOf(this.hoverIdx);

        // Vertical crosshair
        ctx.save();
        ctx.strokeStyle = 'rgba(255,255,255,0.25)';
        ctx.lineWidth = 1;
        ctx.setLineDash([]);
        ctx.beginPath();
        ctx.moveTo(hx, mT); ctx.lineTo(hx, H - mB);
        ctx.stroke();
        ctx.restore();

        // Dots on visible lines
        const dots: Array<{ y: number; color: string }> = [];
        if (this.showPrimary && yOfPrimary) {
            const val = this.isCycling ? rec.power : (rec.speed || 0) * 3.6;
            dots.push({ y: yOfPrimary(val), color: accent });
        }
        if (this.showHR && yOfHR && rec.heartRate) {
            dots.push({ y: yOfHR(rec.heartRate), color: '#e74c3c' });
        }
        if (this.showCadence && yOfCad && rec.cadence) {
            dots.push({ y: yOfCad(rec.cadence), color: '#3b82f6' });
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

        // Tooltip box
        const rows: Array<{ label: string; value: string; color: string }> = [];
        if (this.showPrimary) {
            if (this.isCycling) {
                rows.push({ label: 'Power', value: `${Math.round(rec.power)}W`, color: accent });
            } else {
                rows.push({ label: 'Speed', value: `${((rec.speed || 0) * 3.6).toFixed(1)} km/h`, color: accent });
            }
        }
        if (this.showHR && rec.heartRate) {
            rows.push({ label: 'HR', value: `${Math.round(rec.heartRate)} bpm`, color: '#e74c3c' });
        }
        if (this.showCadence && rec.cadence) {
            rows.push({ label: 'Cadence', value: `${Math.round(rec.cadence)} rpm`, color: '#3b82f6' });
        }

        if (rows.length === 0) return;

        const elapsed = this.hoverIdx;
        const em = Math.floor(elapsed / 60);
        const es = elapsed % 60;
        const timeStr = `${em}:${String(es).padStart(2, '0')}`;

        const pad = 10;
        const rowH = 18;
        const boxW = 130;
        const boxH = pad + 18 + rows.length * rowH + pad;

        let tx = hx + 14;
        if (tx + boxW > W - mR) tx = hx - boxW - 14;
        let ty = mT + 16;
        if (ty + boxH > H - mB) ty = H - mB - boxH;

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
        ctx.font = '9px monospace';
        ctx.textAlign = 'left';
        ctx.fillText(timeStr, tx + pad, ty + pad + 8);

        // Separator
        ctx.fillStyle = 'rgba(255,255,255,0.15)';
        ctx.fillRect(tx + pad, ty + pad + 13, boxW - pad * 2, 1);

        // Value rows
        rows.forEach((row, ri) => {
            const ry = ty + pad + 18 + ri * rowH + 11;
            ctx.fillStyle = 'rgba(255,255,255,0.65)';
            ctx.font = '9px monospace';
            ctx.textAlign = 'left';
            ctx.fillText(row.label, tx + pad, ry);
            ctx.fillStyle = row.color;
            ctx.font = 'bold 10px monospace';
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

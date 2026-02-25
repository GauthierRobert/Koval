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
            <canvas #canvas class="chart-canvas"></canvas>
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
        .chart-canvas { width: 100%; height: 200px; display: block; }
    `],
})
export class FitTimeseriesChartComponent implements OnChanges, AfterViewInit {
    @Input() records: FitRecord[] = [];
    @Input() ftp = 250;
    @Input() sportType = 'CYCLING';

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

    showPrimary = true;
    showHR = true;
    showCadence = false;

    private ready = false;

    get isCycling(): boolean { return this.sportType === 'CYCLING'; }
    get primaryLabel(): string { return this.isCycling ? 'Power' : 'Speed'; }

    ngAfterViewInit(): void {
        this.ready = true;
        this.draw();
    }

    ngOnChanges(): void {
        if (this.ready) this.draw();
    }

    draw(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas) return;

        const W = (canvas.width = canvas.offsetWidth || 600);
        const H = (canvas.height = canvas.offsetHeight || 200);
        const ctx = canvas.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);

        if (!this.records.length) return;

        const mL = 48, mR = 48, mT = 12, mB = 28;
        const cW = W - mL - mR;
        const cH = H - mT - mB;
        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
        const n = this.records.length;
        const xOf = (i: number) => mL + (i / (n - 1)) * cW;

        if (this.isCycling) {
            // ── Primary metric: Power (W) ─────────────────────────────────────
            const smoothed = this.rollingAvg(this.records.map((r) => r.power), 5);
            const maxP = Math.max(this.ftp * 1.5, ...smoothed) || 1;
            const yOfPow = (p: number) => mT + cH * (1 - p / maxP);

            // FTP reference line
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
            // speed in FitRecord is m/s → convert to km/h for display
            const speeds = this.records.map((r) => (r.speed || 0) * 3.6);
            const smoothed = this.rollingAvg(speeds, 5);
            const maxS = Math.max(...smoothed.filter((v) => v > 0), 1);
            const yOfSpd = (s: number) => mT + cH * (1 - s / maxS);

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
            const maxHR = hrs.length ? Math.max(...hrs) * 1.05 : 220;
            const yOfHR = (hr: number) => mT + cH * (1 - hr / maxHR);

            ctx.save();
            ctx.setLineDash([3, 3]);
            ctx.beginPath();
            let first = true;
            this.records.forEach((r, i) => {
                if (!r.heartRate) return;
                if (first) { ctx.moveTo(xOf(i), yOfHR(r.heartRate)); first = false; }
                else ctx.lineTo(xOf(i), yOfHR(r.heartRate));
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
            const maxCad = cads.length ? Math.max(...cads) * 1.1 : 120;
            const yOfCad = (c: number) => mT + cH * (1 - c / maxCad);

            ctx.save();
            ctx.setLineDash([2, 4]);
            ctx.beginPath();
            let first = true;
            this.records.forEach((r, i) => {
                if (!r.cadence) return;
                if (first) { ctx.moveTo(xOf(i), yOfCad(r.cadence)); first = false; }
                else ctx.lineTo(xOf(i), yOfCad(r.cadence));
            });
            ctx.strokeStyle = '#3b82f6';
            ctx.lineWidth = 1.5;
            ctx.stroke();
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

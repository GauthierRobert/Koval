import {
    Component,
    Input,
    OnChanges,
    AfterViewInit,
    ViewChild,
    ElementRef,
} from '@angular/core';
import { PmcDataPoint } from '../../services/metrics.service';

@Component({
    selector: 'app-pmc-chart',
    standalone: true,
    imports: [],
    template: `
        <div class="pmc-chart-wrap">
            <canvas #canvas class="pmc-canvas"></canvas>
        </div>
    `,
    styles: [`
        .pmc-chart-wrap { width: 100%; }
        .pmc-canvas { width: 100%; height: 260px; display: block; }
    `],
})
export class PmcChartComponent implements OnChanges, AfterViewInit {
    @Input() data: PmcDataPoint[] | null = [];

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

    private ready = false;

    ngAfterViewInit(): void {
        this.ready = true;
        this.draw();
    }

    ngOnChanges(): void {
        if (this.ready) this.draw();
    }

    private draw(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas) return;

        const W = (canvas.width = canvas.offsetWidth || 700);
        const H = (canvas.height = canvas.offsetHeight || 260);
        const ctx = canvas.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);

        const points = this.data ?? [];
        if (points.length < 2) {
            ctx.globalAlpha = 0.3;
            ctx.fillStyle = '#fff';
            ctx.font = '12px monospace';
            ctx.textAlign = 'center';
            ctx.fillText('No PMC data for this period', W / 2, H / 2);
            ctx.globalAlpha = 1;
            return;
        }

        const mL = 44, mR = 44, mT = 16, mB = 32;
        const cW = W - mL - mR;
        const cH = H - mT - mB;

        const ctlVals = points.map((p) => p.ctl);
        const atlVals = points.map((p) => p.atl);
        const tsbVals = points.map((p) => p.tsb);
        const tssVals = points.map((p) => p.dailyTss);

        const maxLoad = Math.max(...ctlVals, ...atlVals, 1) * 1.1;
        const tsbMax = Math.max(Math.abs(Math.min(...tsbVals)), Math.abs(Math.max(...tsbVals)), 1) * 1.2;
        const maxTss = Math.max(...tssVals, 1);

        const n = points.length;
        const xOf = (i: number) => mL + (i / (n - 1)) * cW;
        const yOfLoad = (v: number) => mT + cH * (1 - v / maxLoad);
        const yOfTsb = (v: number) => mT + cH * (1 - (v + tsbMax) / (2 * tsbMax));
        const yOfTss = (v: number) => mT + cH * (1 - v / maxTss);

        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
        const today = new Date().toISOString().split('T')[0];

        // Daily TSS bars (background)
        ctx.fillStyle = 'rgba(255,255,255,0.06)';
        const barW = Math.max(1, cW / n - 1);
        points.forEach((p, i) => {
            const barH = (p.dailyTss / maxTss) * cH;
            ctx.fillRect(xOf(i) - barW / 2, H - mB - barH, barW, barH);
        });

        // TSB below-zero fill
        const zeroY = yOfTsb(0);
        ctx.save();
        ctx.beginPath();
        points.forEach((p, i) => {
            if (p.tsb >= 0) return;
            const x = xOf(i);
            const y = yOfTsb(p.tsb);
            if (i === 0 || points[i - 1].tsb >= 0) ctx.moveTo(x, zeroY);
            ctx.lineTo(x, y);
        });
        ctx.lineTo(xOf(n - 1), zeroY);
        ctx.closePath();
        ctx.fillStyle = 'rgba(239,68,68,0.08)';
        ctx.fill();
        ctx.restore();

        // Zero TSB line
        ctx.save();
        ctx.setLineDash([3, 3]);
        ctx.strokeStyle = 'rgba(255,255,255,0.12)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(mL, zeroY);
        ctx.lineTo(W - mR, zeroY);
        ctx.stroke();
        ctx.restore();

        // Find today index and peak TSB in non-predicted data
        const todayIdx = points.findIndex((p) => p.date === today);
        const realPoints = points.filter((p) => !p.predicted);
        let peakPoint: PmcDataPoint | null = null;
        if (realPoints.length) {
            peakPoint = realPoints.reduce((a, b) => a.tsb > b.tsb ? a : b);
        }

        // Helper: draw a polyline separating real vs predicted
        const drawLine = (
            getY: (p: PmcDataPoint) => number,
            color: string,
            lineW: number,
            dash: number[] = [],
        ) => {
            // Real segment
            ctx.save();
            ctx.setLineDash(dash);
            ctx.strokeStyle = color;
            ctx.lineWidth = lineW;
            ctx.beginPath();
            let started = false;
            points.forEach((p, i) => {
                if (p.predicted) return;
                const x = xOf(i), y = getY(p);
                if (!started) { ctx.moveTo(x, y); started = true; }
                else ctx.lineTo(x, y);
            });
            ctx.stroke();
            ctx.restore();

            // Predicted segment
            const predStart = points.findIndex((p) => p.predicted);
            if (predStart < 0) return;
            ctx.save();
            ctx.globalAlpha = 0.4;
            ctx.setLineDash([4, 4]);
            ctx.strokeStyle = color;
            ctx.lineWidth = lineW;
            ctx.beginPath();
            let pStarted = false;
            // Start from last real point for continuity
            if (predStart > 0) {
                const prev = points[predStart - 1];
                ctx.moveTo(xOf(predStart - 1), getY(prev));
                pStarted = true;
            }
            points.forEach((p, i) => {
                if (!p.predicted) return;
                const x = xOf(i), y = getY(p);
                if (!pStarted) { ctx.moveTo(x, y); pStarted = true; }
                else ctx.lineTo(x, y);
            });
            ctx.stroke();
            ctx.restore();
        };

        // CTL — thick orange
        drawLine((p) => yOfLoad(p.ctl), accent, 2.5);
        // ATL — thick red
        drawLine((p) => yOfLoad(p.atl), '#e74c3c', 2.5);
        // TSB — thick blue dashed
        drawLine((p) => yOfTsb(p.tsb), '#3b82f6', 2, [4, 2]);

        // Today marker
        if (todayIdx >= 0) {
            const tx = xOf(todayIdx);
            ctx.save();
            ctx.strokeStyle = accent + 'aa';
            ctx.lineWidth = 1.5;
            ctx.setLineDash([3, 3]);
            ctx.beginPath();
            ctx.moveTo(tx, mT);
            ctx.lineTo(tx, H - mB);
            ctx.stroke();
            ctx.restore();
            ctx.fillStyle = accent;
            ctx.font = '9px monospace';
            ctx.textAlign = 'center';
            ctx.fillText('TODAY', tx, mT - 2);
        }

        // Peak TSB marker
        if (peakPoint && peakPoint.tsb > 0) {
            const peakIdx = points.indexOf(peakPoint);
            const px = xOf(peakIdx);
            const py = yOfTsb(peakPoint.tsb);
            ctx.beginPath();
            ctx.arc(px, py, 5, 0, Math.PI * 2);
            ctx.fillStyle = '#3b82f6';
            ctx.fill();
            ctx.fillStyle = '#fff';
            ctx.font = 'bold 9px monospace';
            ctx.textAlign = 'center';
            ctx.fillText(`+${Math.round(peakPoint.tsb)}`, px, py - 9);
        }

        // Month grid + X-axis labels
        ctx.fillStyle = 'rgba(255,255,255,0.35)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        let lastMonth = '';
        points.forEach((p, i) => {
            const month = p.date.substring(0, 7); // YYYY-MM
            if (month !== lastMonth) {
                lastMonth = month;
                const x = xOf(i);
                ctx.save();
                ctx.strokeStyle = 'rgba(255,255,255,0.06)';
                ctx.lineWidth = 1;
                ctx.setLineDash([]);
                ctx.beginPath();
                ctx.moveTo(x, mT);
                ctx.lineTo(x, H - mB);
                ctx.stroke();
                ctx.restore();
                const label = new Date(p.date + 'T12:00:00').toLocaleDateString('en', { month: 'short' });
                ctx.fillText(label, x, H - 8);
            }
        });

        // Y-axis labels (CTL/ATL scale — left; TSB scale — right)
        ctx.textAlign = 'right';
        ctx.fillStyle = 'rgba(255,255,255,0.35)';
        [0, 0.5, 1].forEach((frac) => {
            const v = Math.round(maxLoad * frac);
            ctx.fillText(String(v), mL - 4, yOfLoad(v) + 4);
        });
        ctx.textAlign = 'left';
        [-tsbMax, 0, tsbMax].forEach((v) => {
            ctx.fillStyle = v < 0 ? 'rgba(239,68,68,0.6)' : v > 0 ? 'rgba(59,130,246,0.6)' : 'rgba(255,255,255,0.25)';
            ctx.fillText(String(Math.round(v)), W - mR + 4, yOfTsb(v) + 4);
        });

        // Legend
        const legends: [string, string][] = [
            [accent, 'CTL (Fitness)'],
            ['#e74c3c', 'ATL (Fatigue)'],
            ['#3b82f6', 'TSB (Form)'],
        ];
        let lx = mL;
        legends.forEach(([color, label]) => {
            ctx.fillStyle = color;
            ctx.fillRect(lx, mT - 1, 12, 3);
            ctx.fillStyle = 'rgba(255,255,255,0.5)';
            ctx.font = '9px monospace';
            ctx.textAlign = 'left';
            ctx.fillText(label, lx + 16, mT + 3);
            lx += 90;
        });
    }
}

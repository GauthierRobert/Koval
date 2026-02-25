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
        :host { display: flex; flex-direction: column; flex: 1; min-height: 0; }
        .pmc-chart-wrap { flex: 1; display: flex; flex-direction: column; min-height: 0; }
        .pmc-canvas { width: 100%; flex: 1; min-height: 320px; display: block; }
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
        const H = (canvas.height = canvas.offsetHeight || 420);
        const ctx = canvas.getContext('2d')!;
        ctx.clearRect(0, 0, W, H);

        const FONT = '11px Inter, system-ui, sans-serif';
        const FONT_SM = '10px Inter, system-ui, sans-serif';

        const points = this.data ?? [];
        if (points.length < 2) {
            ctx.globalAlpha = 0.3;
            ctx.fillStyle = '#fff';
            ctx.font = FONT;
            ctx.textAlign = 'center';
            ctx.fillText('No PMC data for this period', W / 2, H / 2);
            ctx.globalAlpha = 1;
            return;
        }

        // ── Margins ───────────────────────────────────────────────────────────
        const mL = 54, mR = 54, mT = 60, mB = 36;
        const cW = W - mL - mR;
        const cH = H - mT - mB;

        // ── Scales ────────────────────────────────────────────────────────────
        const ctlVals = points.map((p) => p.ctl);
        const atlVals = points.map((p) => p.atl);
        const tsbVals = points.map((p) => p.tsb);
        const tssVals = points.map((p) => p.dailyTss);

        const maxLoad = Math.max(...ctlVals, ...atlVals, 1) * 1.15;
        const tsbMax  = Math.max(Math.abs(Math.min(...tsbVals)), Math.abs(Math.max(...tsbVals)), 1) * 1.2;
        const maxTss  = Math.max(...tssVals, 1);

        const n      = points.length;
        const xOf    = (i: number) => mL + (i / (n - 1)) * cW;
        const yLoad  = (v: number) => mT + cH * (1 - v / maxLoad);
        const yTsb   = (v: number) => mT + cH * (1 - (v + tsbMax) / (2 * tsbMax));
        const yTss   = (v: number) => mT + cH * (1 - v / maxTss);

        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
        const today  = new Date().toISOString().split('T')[0];
        const zeroY  = yTsb(0);

        // ── Daily TSS bars ────────────────────────────────────────────────────
        const barW = Math.max(1.5, cW / n - 0.5);
        ctx.fillStyle = 'rgba(255,255,255,0.12)';
        points.forEach((p, i) => {
            const bH = (p.dailyTss / maxTss) * cH;
            ctx.fillRect(xOf(i) - barW / 2, H - mB - bH, barW, bH);
        });

        // ── Fatigue zone (TSB < 0) fill ───────────────────────────────────────
        ctx.save();
        ctx.beginPath();
        let inFatigue = false;
        points.forEach((p, i) => {
            const x = xOf(i);
            const y = yTsb(p.tsb);
            if (p.tsb < 0) {
                if (!inFatigue) { ctx.moveTo(x, Math.min(y, zeroY)); inFatigue = true; }
                ctx.lineTo(x, y);
            } else if (inFatigue) {
                ctx.lineTo(x, zeroY);
                inFatigue = false;
            }
        });
        if (inFatigue) ctx.lineTo(xOf(n - 1), zeroY);
        ctx.closePath();
        ctx.fillStyle = 'rgba(239,68,68,0.12)';
        ctx.fill();
        ctx.restore();

        // ── Horizontal grid lines ─────────────────────────────────────────────
        ctx.strokeStyle = 'rgba(255,255,255,0.07)';
        ctx.lineWidth = 1;
        [0.25, 0.5, 0.75, 1].forEach((frac) => {
            const y = mT + cH * (1 - frac);
            ctx.beginPath();
            ctx.moveTo(mL, y);
            ctx.lineTo(W - mR, y);
            ctx.stroke();
        });

        // ── Zero TSB line ─────────────────────────────────────────────────────
        ctx.save();
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = 'rgba(255,255,255,0.2)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(mL, zeroY);
        ctx.lineTo(W - mR, zeroY);
        ctx.stroke();
        ctx.restore();

        // ── "FATIGUED" label inside red zone ──────────────────────────────────
        const hasSignificantFatigue = tsbVals.some((v) => v < -5);
        if (hasSignificantFatigue) {
            // Find the deepest TSB point for label placement
            const minTsb = Math.min(...tsbVals);
            const minIdx = tsbVals.indexOf(minTsb);
            const lx = Math.min(Math.max(xOf(minIdx), mL + 40), W - mR - 40);
            const ly = (zeroY + yTsb(minTsb)) / 2;
            ctx.fillStyle = 'rgba(239,68,68,0.5)';
            ctx.font = '9px Inter, system-ui, sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('FATIGUED', lx, ly);
        }

        // ── Month grid + X-axis labels ────────────────────────────────────────
        ctx.fillStyle = 'rgba(255,255,255,0.4)';
        ctx.font = FONT_SM;
        ctx.textAlign = 'center';
        let lastMonth = '';
        points.forEach((p, i) => {
            const month = p.date.substring(0, 7);
            if (month !== lastMonth) {
                lastMonth = month;
                const x = xOf(i);
                ctx.save();
                ctx.strokeStyle = 'rgba(255,255,255,0.07)';
                ctx.lineWidth = 1;
                ctx.setLineDash([]);
                ctx.beginPath();
                ctx.moveTo(x, mT);
                ctx.lineTo(x, H - mB);
                ctx.stroke();
                ctx.restore();
                const label = new Date(p.date + 'T12:00:00').toLocaleDateString('en', { month: 'short', year: '2-digit' });
                ctx.fillText(label, x, H - 10);
            }
        });

        // ── Helper: draw a polyline with real/predicted segments ──────────────
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

            // Predicted segment (dashed + dimmed)
            const predStart = points.findIndex((p) => p.predicted);
            if (predStart < 0) return;
            ctx.save();
            ctx.globalAlpha = 0.45;
            ctx.setLineDash([5, 4]);
            ctx.strokeStyle = color;
            ctx.lineWidth = lineW;
            ctx.beginPath();
            let pStarted = false;
            if (predStart > 0) {
                ctx.moveTo(xOf(predStart - 1), getY(points[predStart - 1]));
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

        // CTL (fitness) — orange, thick
        drawLine((p) => yLoad(p.ctl), accent, 2.5);
        // ATL (fatigue) — red, thick
        drawLine((p) => yLoad(p.atl), '#e74c3c', 2.5);
        // TSB (form) — blue, medium dashed
        drawLine((p) => yTsb(p.tsb), '#3b82f6', 2, [4, 2]);

        // ── Today marker ──────────────────────────────────────────────────────
        const todayIdx = points.findIndex((p) => p.date === today);
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
            ctx.font = '9px Inter, system-ui, sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('TODAY', tx, mT - 4);
        }

        // ── Peak TSB marker in real data ──────────────────────────────────────
        const realPoints = points.filter((p) => !p.predicted);
        if (realPoints.length) {
            const peak = realPoints.reduce((a, b) => a.tsb > b.tsb ? a : b);
            if (peak.tsb > 5) {
                const pi = points.indexOf(peak);
                const px = xOf(pi), py = yTsb(peak.tsb);
                ctx.beginPath();
                ctx.arc(px, py, 4, 0, Math.PI * 2);
                ctx.fillStyle = '#3b82f6';
                ctx.fill();
                ctx.fillStyle = '#fff';
                ctx.font = 'bold 9px Inter, system-ui, sans-serif';
                ctx.textAlign = 'center';
                ctx.fillText(`+${Math.round(peak.tsb)}`, px, py - 8);
            }
        }

        // ── Y-axis labels ─────────────────────────────────────────────────────
        ctx.font = FONT_SM;
        ctx.fillStyle = 'rgba(255,255,255,0.4)';

        // Left axis — Load (CTL/ATL)
        ctx.textAlign = 'right';
        [0, 0.5, 1].forEach((frac) => {
            const v = Math.round(maxLoad * frac);
            ctx.fillText(String(v), mL - 6, yLoad(v) + 4);
        });
        ctx.fillStyle = accent + 'aa';
        ctx.font = '9px Inter, system-ui, sans-serif';
        ctx.fillText('LOAD', mL - 6, mT - 6);

        // Right axis — Form (TSB)
        ctx.textAlign = 'left';
        ctx.font = FONT_SM;
        ([-tsbMax, 0, tsbMax] as number[]).forEach((v) => {
            ctx.fillStyle = v < 0 ? 'rgba(239,68,68,0.65)' : v > 0 ? 'rgba(59,130,246,0.65)' : 'rgba(255,255,255,0.3)';
            ctx.fillText(String(Math.round(v)), W - mR + 6, yTsb(v) + 4);
        });
        ctx.fillStyle = 'rgba(59,130,246,0.7)';
        ctx.font = '9px Inter, system-ui, sans-serif';
        ctx.fillText('FORM', W - mR + 6, mT - 6);

        // ── Legend ────────────────────────────────────────────────────────────
        interface LegendItem { label: string; color: string; dash?: boolean; fill?: boolean; bar?: boolean; }
        const legendItems: LegendItem[] = [
            { label: 'CTL — Fitness',    color: accent },
            { label: 'ATL — Fatigue',    color: '#e74c3c' },
            { label: 'TSB — Form',       color: '#3b82f6', dash: true },
            { label: 'Daily TSS',        color: 'rgba(255,255,255,0.35)', bar: true },
            { label: 'Fatigue zone',     color: 'rgba(239,68,68,0.5)', fill: true },
        ];

        const itemW = cW / legendItems.length;
        const ly0 = 22;

        legendItems.forEach((item, idx) => {
            const lx = mL + idx * itemW + 4;

            if (item.bar) {
                ctx.fillStyle = 'rgba(255,255,255,0.3)';
                ctx.fillRect(lx, ly0 - 7, 10, 12);
            } else if (item.fill) {
                ctx.fillStyle = 'rgba(239,68,68,0.35)';
                ctx.fillRect(lx, ly0 - 7, 14, 12);
                ctx.strokeStyle = 'rgba(239,68,68,0.6)';
                ctx.lineWidth = 1;
                ctx.strokeRect(lx, ly0 - 7, 14, 12);
            } else if (item.dash) {
                ctx.save();
                ctx.strokeStyle = item.color;
                ctx.lineWidth = 2;
                ctx.setLineDash([5, 3]);
                ctx.beginPath();
                ctx.moveTo(lx, ly0);
                ctx.lineTo(lx + 14, ly0);
                ctx.stroke();
                ctx.restore();
            } else {
                ctx.fillStyle = item.color;
                ctx.fillRect(lx, ly0 - 3, 14, 5);
            }

            ctx.fillStyle = 'rgba(255,255,255,0.75)';
            ctx.font = FONT;
            ctx.textAlign = 'left';
            ctx.fillText(item.label, lx + 18, ly0 + 4);
        });
    }
}

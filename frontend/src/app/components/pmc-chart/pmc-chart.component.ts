import {AfterViewInit, Component, ElementRef, Input, OnChanges, ViewChild,} from '@angular/core';
import {PmcDataPoint} from '../../services/metrics.service';

const SPORT_COLORS: Record<string, string> = {
    CYCLING: '#FF9D00',
    RUNNING: '#34D399',
    SWIMMING: '#60A5FA',
    BRICK: '#A78BFA',
    GYM: '#F472B6',
};

function getSportColor(sport?: string): string {
    return SPORT_COLORS[sport || ''] || '#FF9D00';
}

@Component({
    selector: 'app-pmc-chart',
    standalone: true,
    imports: [],
    template: `
        <div class="pmc-chart-wrap">
            <canvas #canvas class="pmc-canvas"
                (mousemove)="onMouseMove($event)"
                (mouseleave)="onMouseLeave()">
            </canvas>
        </div>
    `,
    styles: [`
        :host { display: flex; flex-direction: column; flex: 1; min-height: 0; }
        .pmc-chart-wrap { flex: 1; display: flex; flex-direction: column; min-height: 0; }
        .pmc-canvas { width: 100%; flex: 1; min-height: 320px; display: block; cursor: crosshair; }
    `],
})
export class PmcChartComponent implements OnChanges, AfterViewInit {
    @Input() data: PmcDataPoint[] | null = [];

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;
    private ready = false;
    private hoverIdx: number | null = null;

    // Fixed margins — kept in sync with draw()
    private readonly ML = 54;
    private readonly MR = 54;

    ngAfterViewInit(): void { this.ready = true; this.draw(); }
    ngOnChanges(): void { if (this.ready) this.draw(); }

    onMouseMove(event: MouseEvent): void {
        const canvas = this.canvasRef?.nativeElement;
        const points = this.data ?? [];
        if (!canvas || points.length < 2) return;

        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const mouseX = (event.clientX - rect.left) * scaleX;

        const cW = canvas.width - this.ML - this.MR;
        const idx = Math.round((mouseX - this.ML) / cW * (points.length - 1));
        const clamped = Math.max(0, Math.min(points.length - 1, idx));

        if (clamped !== this.hoverIdx) {
            this.hoverIdx = clamped;
            this.draw();
        }
    }

    onMouseLeave(): void {
        this.hoverIdx = null;
        this.draw();
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
        const FONT_XS = '9px Inter, system-ui, sans-serif';

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
        const mL = this.ML, mR = this.MR, mT = 60, mB = 36;
        const cW = W - mL - mR;
        const cH = H - mT - mB;

        // ── Scales ────────────────────────────────────────────────────────────
        const maxLoad = Math.max(...points.map(p => p.ctl), ...points.map(p => p.atl), 1) * 1.15;
        const tsbMax = Math.max(...points.map(p => Math.abs(p.tsb)), 1) * 1.2;
        const maxTss = Math.max(...points.map(p => p.dailyTss), 1);

        const n = points.length;
        const xOf = (i: number) => mL + (i / (n - 1)) * cW;
        const yLoad = (v: number) => mT + cH * (1 - v / maxLoad);
        const yTsb = (v: number) => mT + cH * (1 - (v + tsbMax) / (2 * tsbMax));

        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
        const today = new Date().toISOString().split('T')[0];
        const zeroY = yTsb(0);

        const todayIdx = points.findIndex(p => p.date === today);
        const predStart = points.findIndex(p => p.predicted);

        // ── Future background ─────────────────────────────────────────────────
        const splitX = predStart > 0 ? xOf(predStart - 1)
            : todayIdx > 0 ? xOf(todayIdx)
                : W - mR;

        if (splitX < W - mR) {
            ctx.fillStyle = 'rgba(0, 0, 0, 0.22)';
            ctx.fillRect(splitX, mT, W - mR - splitX, cH);
            ctx.fillStyle = 'rgba(255, 255, 255, 0.25)';
            ctx.font = FONT_XS;
            ctx.textAlign = 'left';
            ctx.fillText('PROJECTED', splitX + 6, mT + 12);
        }

        // ── Daily TSS bars ────────────────────────────────────────────────────
        const barW = Math.max(1.5, cW / n - 0.5);
        points.forEach((p, i) => {
            const x = xOf(i) - barW / 2;
            let currentY = H - mB;

            if (p.sportTss && Object.keys(p.sportTss).length > 0) {
                // Stacked sport bars
                Object.entries(p.sportTss).forEach(([sport, tss]) => {
                    if (tss <= 0) return;
                    const bH = (tss / maxTss) * cH;
                    ctx.fillStyle = getSportColor(sport);
                    ctx.globalAlpha = 0.4;
                    ctx.fillRect(x, currentY - bH, barW, bH);
                    currentY -= bH;
                });
            } else {
                // Fallback to single gray bar
                const bH = (p.dailyTss / maxTss) * cH;
                ctx.fillStyle = 'rgba(255,255,255,0.08)';
                ctx.fillRect(x, currentY - bH, barW, bH);
            }
        });

        // ── Fatigue zone fill ─────────────────────────────────────────────────
        ctx.save();
        ctx.beginPath();
        let inFatigue = false;
        points.forEach((p, i) => {
            const x = xOf(i), y = yTsb(p.tsb);
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
        ctx.fillStyle = 'rgba(239,68,68,0.14)';
        ctx.fill();
        ctx.restore();

        // ── Horizontal grid lines ─────────────────────────────────────────────
        [0.25, 0.5, 0.75, 1].forEach(frac => {
            const y = mT + cH * (1 - frac);
            ctx.beginPath();
            ctx.strokeStyle = 'rgba(255,255,255,0.07)';
            ctx.lineWidth = 1;
            ctx.moveTo(mL, y); ctx.lineTo(W - mR, y);
            ctx.stroke();
        });

        // ── Zero TSB line ─────────────────────────────────────────────────────
        ctx.save();
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = 'rgba(255,255,255,0.2)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(mL, zeroY); ctx.lineTo(W - mR, zeroY);
        ctx.stroke();
        ctx.restore();

        // ── Month grid + X-axis labels ────────────────────────────────────────
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
                ctx.moveTo(x, mT); ctx.lineTo(x, H - mB);
                ctx.stroke();
                ctx.restore();
                const label = new Date(p.date + 'T12:00:00')
                    .toLocaleDateString('en', { month: 'short', year: '2-digit' });
                ctx.fillStyle = 'rgba(255,255,255,0.4)';
                ctx.font = FONT_SM;
                ctx.textAlign = 'center';
                ctx.fillText(label, x, H - 10);
            }
        });

        // ── Lines: solid past, dotted future ──────────────────────────────────
        const drawLine = (getY: (p: PmcDataPoint) => number, color: string, lineW: number) => {
            ctx.save();
            ctx.strokeStyle = color;
            ctx.lineWidth = lineW;
            ctx.setLineDash([]);
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

            if (predStart < 0) return;
            ctx.save();
            ctx.strokeStyle = color;
            ctx.lineWidth = lineW;
            ctx.setLineDash([1, 3]);
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

        drawLine(p => yLoad(p.ctl), accent, 2.5);
        drawLine(p => yLoad(p.atl), '#e74c3c', 2.5);
        drawLine(p => yTsb(p.tsb), '#3b82f6', 2);

        // ── Today marker ──────────────────────────────────────────────────────
        if (todayIdx >= 0) {
            const tx = xOf(todayIdx);
            ctx.save();
            ctx.strokeStyle = accent + 'cc';
            ctx.lineWidth = 1.5;
            ctx.setLineDash([3, 3]);
            ctx.beginPath();
            ctx.moveTo(tx, mT); ctx.lineTo(tx, H - mB);
            ctx.stroke();
            ctx.restore();
            ctx.fillStyle = accent;
            ctx.font = FONT_XS;
            ctx.textAlign = 'center';
            ctx.fillText('TODAY', tx, mT - 4);
        }

        // ── Peak TSB dot ──────────────────────────────────────────────────────
        const realPoints = points.filter(p => !p.predicted);
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
                ctx.font = `bold ${FONT_XS}`;
                ctx.textAlign = 'center';
                ctx.fillText(`+${Math.round(peak.tsb)}`, px, py - 8);
            }
        }

        // ── Y-axis labels ─────────────────────────────────────────────────────
        ctx.textAlign = 'right';
        ctx.fillStyle = 'rgba(255,255,255,0.4)';
        ctx.font = FONT_SM;
        [0, 0.5, 1].forEach(frac => {
            const v = Math.round(maxLoad * frac);
            ctx.fillText(String(v), mL - 6, yLoad(v) + 4);
        });
        ctx.fillStyle = accent + 'bb';
        ctx.font = FONT_XS;
        ctx.fillText('LOAD', mL - 6, mT - 6);

        ctx.textAlign = 'left';
        ctx.font = FONT_SM;
        ([-tsbMax, 0, tsbMax] as number[]).forEach(v => {
            ctx.fillStyle = v < 0 ? 'rgba(239,68,68,0.65)' : v > 0 ? 'rgba(59,130,246,0.65)' : 'rgba(255,255,255,0.3)';
            ctx.fillText(String(Math.round(v)), W - mR + 6, yTsb(v) + 4);
        });
        ctx.fillStyle = 'rgba(59,130,246,0.7)';
        ctx.font = FONT_XS;
        ctx.fillText('FORM', W - mR + 6, mT - 6);

        // ── Legend ────────────────────────────────────────────────────────────
        const legendItems = [
            { label: 'CTL — Fitness', color: accent, bar: false, fill: false },
            { label: 'ATL — Fatigue', color: '#e74c3c', bar: false, fill: false },
            { label: 'TSB — Form', color: '#3b82f6', bar: false, fill: false },
            { label: 'Daily TSS', color: 'rgba(255,255,255,0.3)', bar: true, fill: false },
            { label: 'Fatigue zone', color: 'rgba(239,68,68,0.4)', bar: false, fill: true },
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
            } else {
                ctx.fillStyle = item.color;
                ctx.fillRect(lx, ly0 - 3, 14, 5);
            }
            ctx.fillStyle = 'rgba(255,255,255,0.75)';
            ctx.font = FONT;
            ctx.textAlign = 'left';
            ctx.fillText(item.label, lx + 18, ly0 + 4);
        });

        // ── Hover crosshair + tooltip ─────────────────────────────────────────
        if (this.hoverIdx === null) return;
        const p = points[this.hoverIdx];
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

        // Dots on each line
        const lineDots = [
            { y: yLoad(p.ctl), color: accent },
            { y: yLoad(p.atl), color: '#e74c3c' },
            { y: yTsb(p.tsb), color: '#3b82f6' },
        ];
        lineDots.forEach(({ y, color }) => {
            ctx.beginPath();
            ctx.arc(hx, y, 4, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            ctx.strokeStyle = 'rgba(255,255,255,0.8)';
            ctx.lineWidth = 1.5;
            ctx.stroke();
        });

        // Tooltip box
        const tsbSign = p.tsb >= 0 ? '+' : '';
        const rows: Array<{ label: string; value: string; color: string }> = [
            { label: 'CTL', value: p.ctl.toFixed(1), color: accent },
            { label: 'ATL', value: p.atl.toFixed(1), color: '#e74c3c' },
            { label: 'TSB', value: `${tsbSign}${p.tsb.toFixed(1)}`, color: p.tsb >= 0 ? '#3b82f6' : '#f87171' },
            { label: 'TSS', value: String(Math.round(p.dailyTss)), color: 'rgba(255,255,255,0.55)' },
        ];

        const pad = 10;
        const rowH = 18;
        const boxW = 130;
        const boxH = pad + 18 + rows.length * rowH + pad; // date + rows + padding
        const dateStr = new Date(p.date + 'T12:00:00').toLocaleDateString('en', { month: 'short', day: 'numeric', year: 'numeric' });

        let tx = hx + 14;
        if (tx + boxW > W - mR) tx = hx - boxW - 14;
        let ty = mT + 16;
        if (ty + boxH > H - mB) ty = H - mB - boxH;

        // Background
        ctx.save();
        ctx.fillStyle = 'rgba(8, 8, 18, 0.92)';
        ctx.strokeStyle = 'rgba(255,255,255,0.12)';
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

        // Date header
        ctx.fillStyle = 'rgba(255,255,255,0.5)';
        ctx.font = FONT_XS;
        ctx.textAlign = 'left';
        ctx.fillText(dateStr, tx + pad, ty + pad + 8);

        // Separator
        ctx.fillStyle = 'rgba(255,255,255,0.07)';
        ctx.fillRect(tx + pad, ty + pad + 13, boxW - pad * 2, 1);

        // Value rows
        rows.forEach((row, ri) => {
            const ry = ty + pad + 18 + ri * rowH + 11;
            ctx.fillStyle = 'rgba(255,255,255,0.35)';
            ctx.font = FONT_XS;
            ctx.textAlign = 'left';
            ctx.fillText(row.label, tx + pad, ry);
            ctx.fillStyle = row.color;
            ctx.font = `bold ${FONT_SM}`;
            ctx.textAlign = 'right';
            ctx.fillText(row.value, tx + boxW - pad, ry);
        });
    }
}

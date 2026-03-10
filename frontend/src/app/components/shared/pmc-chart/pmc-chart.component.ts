import {
    AfterViewInit, Component, ElementRef, EventEmitter, Input,
    OnChanges, OnDestroy, Output, SimpleChanges, ViewChild,
} from '@angular/core';
import {PmcDataPoint} from '../../../services/metrics.service';
import {RaceGoal, PRIORITY_COLORS} from '../../../services/race-goal.service';

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
                (mouseleave)="onMouseLeave()"
                (mousedown)="onMouseDown($event)">
            </canvas>
        </div>
    `,
    styles: [`
        :host { display: flex; flex-direction: column; flex: 1; min-height: 0; }
        .pmc-chart-wrap { flex: 1; display: flex; flex-direction: column; min-height: 0; }
        .pmc-canvas { width: 100%; flex: 1; min-height: 320px; display: block; cursor: crosshair; }
    `],
})
export class PmcChartComponent implements OnChanges, AfterViewInit, OnDestroy {
    @Input() data: PmcDataPoint[] | null = [];
    @Input() goals: RaceGoal[] | null = [];

    @Output() viewRangeChange = new EventEmitter<{start: string, end: string}>();

    @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;
    private ready = false;
    private hoverIdx: number | null = null;

    // Fixed margins — kept in sync with draw()
    private readonly ML = 54;
    private readonly MR = 54;

    // Zoom/pan state — date-based
    private viewStartDate = '';
    private viewEndDate = '';
    private viewInitialized = false;
    private isDragging = false;
    private dragStartX = 0;
    private dragStartViewStartDate = '';
    private dragStartViewEndDate = '';
    private dragMoved = false;
    private readonly MIN_VISIBLE_DAYS = 7;
    private readonly MAX_VISIBLE_DAYS = 365;

    // Bound listeners for cleanup
    private wheelHandler = (e: WheelEvent) => this.onWheel(e);
    private mouseUpHandler = (e: MouseEvent) => this.onMouseUp(e);

    // ── Date helpers ──────────────────────────────────────────────────────
    private dateToDays(date: string): number {
        return Math.round(new Date(date + 'T12:00:00').getTime() / 86400000);
    }
    private daysToDate(days: number): string {
        return new Date(days * 86400000 + 43200000).toISOString().split('T')[0];
    }
    private addDaysToDate(date: string, n: number): string {
        return this.daysToDate(this.dateToDays(date) + n);
    }

    private get visibleIndices(): { start: number; end: number } | null {
        const pts = this.data ?? [];
        if (pts.length < 2 || !this.viewStartDate) return null;
        let s = 0;
        while (s < pts.length && pts[s].date < this.viewStartDate) s++;
        if (s >= pts.length) return null;
        let e = pts.length - 1;
        while (e > s && pts[e].date > this.viewEndDate) e--;
        return s <= e ? { start: s, end: e } : null;
    }

    ngAfterViewInit(): void {
        this.ready = true;
        const canvas = this.canvasRef?.nativeElement;
        if (canvas) {
            canvas.addEventListener('wheel', this.wheelHandler, { passive: false });
        }
        document.addEventListener('mouseup', this.mouseUpHandler);
        this.draw();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['data'] && !this.viewInitialized) {
            const pts = this.data ?? [];
            if (pts.length >= 2) {
                const today = new Date().toISOString().split('T')[0];
                const first = pts[0].date;
                const last = pts[pts.length - 1].date;
                const desiredStart = this.addDaysToDate(today, -30);
                const desiredEnd = this.addDaysToDate(today, 30);
                this.viewStartDate = desiredStart < first ? first : desiredStart;
                this.viewEndDate = desiredEnd > last ? last : desiredEnd;
                this.viewInitialized = true;
                this.emitViewRange();
            }
        }
        if (this.ready) this.draw();
    }

    ngOnDestroy(): void {
        const canvas = this.canvasRef?.nativeElement;
        if (canvas) {
            canvas.removeEventListener('wheel', this.wheelHandler);
        }
        document.removeEventListener('mouseup', this.mouseUpHandler);
    }

    onMouseDown(event: MouseEvent): void {
        this.isDragging = false;
        this.dragMoved = false;
        this.dragStartX = event.clientX;
        this.dragStartViewStartDate = this.viewStartDate;
        this.dragStartViewEndDate = this.viewEndDate;
    }

    onMouseMove(event: MouseEvent): void {
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas || !this.viewStartDate) return;

        // Pan with left button held
        if (event.buttons & 1) {
            const dx = event.clientX - this.dragStartX;
            if (!this.dragMoved && Math.abs(dx) > 3) {
                this.dragMoved = true;
                this.isDragging = true;
                this.hoverIdx = null;
                canvas.style.cursor = 'grabbing';
            }
            if (this.isDragging) {
                const rect = canvas.getBoundingClientRect();
                const span = this.dateToDays(this.dragStartViewEndDate) - this.dateToDays(this.dragStartViewStartDate);
                const daysShift = Math.round(-(dx / rect.width) * span);
                this.viewStartDate = this.addDaysToDate(this.dragStartViewStartDate, daysShift);
                this.viewEndDate = this.addDaysToDate(this.dragStartViewEndDate, daysShift);
                this.emitViewRange();
                this.draw();
                return;
            }
        }

        // Normal hover tooltip
        if (this.isDragging) return;

        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const mouseX = (event.clientX - rect.left) * scaleX;

        const vis = this.visibleIndices;
        if (!vis) return;

        const points = this.data!;
        const mL = this.ML;
        const cW = canvas.width - this.ML - this.MR;
        const viewStartDays = this.dateToDays(this.viewStartDate);
        const viewSpan = this.dateToDays(this.viewEndDate) - viewStartDays;
        if (viewSpan <= 0) return;

        const cursorDays = viewStartDays + ((mouseX - mL) / cW) * viewSpan;
        const cursorDate = this.daysToDate(Math.round(cursorDays));

        // Find nearest point in visible slice by date
        let bestIdx = vis.start;
        let bestDist = Math.abs(this.dateToDays(points[vis.start].date) - cursorDays);
        for (let i = vis.start + 1; i <= vis.end; i++) {
            const dist = Math.abs(this.dateToDays(points[i].date) - cursorDays);
            if (dist < bestDist) { bestDist = dist; bestIdx = i; }
        }

        if (bestIdx !== this.hoverIdx) {
            this.hoverIdx = bestIdx;
            this.draw();
        }
    }

    onMouseLeave(): void {
        this.hoverIdx = null;
        if (this.isDragging) {
            this.isDragging = false;
            this.dragMoved = false;
            const canvas = this.canvasRef?.nativeElement;
            if (canvas) canvas.style.cursor = 'crosshair';
        }
        this.draw();
    }

    private onMouseUp(_event: MouseEvent): void {
        if (this.isDragging) {
            this.isDragging = false;
            this.dragMoved = false;
            const canvas = this.canvasRef?.nativeElement;
            if (canvas) canvas.style.cursor = 'crosshair';
        }
    }

    private onWheel(event: WheelEvent): void {
        event.preventDefault();
        if (!this.viewStartDate) return;
        const canvas = this.canvasRef?.nativeElement;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const cursorFrac = (event.clientX - rect.left) / rect.width;

        const startD = this.dateToDays(this.viewStartDate);
        const endD = this.dateToDays(this.viewEndDate);
        const span = endD - startD;
        const anchorD = startD + cursorFrac * span;

        const factor = event.deltaY > 0 ? 1.1 : 1 / 1.1;
        const newSpan = Math.round(
            Math.min(this.MAX_VISIBLE_DAYS, Math.max(this.MIN_VISIBLE_DAYS, span * factor))
        );

        const newStart = Math.round(anchorD - cursorFrac * newSpan);
        this.viewStartDate = this.daysToDate(newStart);
        this.viewEndDate = this.daysToDate(newStart + newSpan);
        this.emitViewRange();
        this.draw();
    }

    private emitViewRange(): void {
        this.viewRangeChange.emit({ start: this.viewStartDate, end: this.viewEndDate });
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

        // ── Margins ───────────────────────────────────────────────────────────
        const mL = this.ML, mR = this.MR, mT = 60, mB = 36;
        const cW = W - mL - mR;
        const cH = H - mT - mB;

        if (!this.viewStartDate || !this.viewEndDate) {
            ctx.globalAlpha = 0.3;
            ctx.fillStyle = '#fff';
            ctx.font = FONT;
            ctx.textAlign = 'center';
            ctx.fillText('No PMC data for this period', W / 2, H / 2);
            ctx.globalAlpha = 1;
            return;
        }

        // ── Date-based xOf ────────────────────────────────────────────────────
        const viewStartDays = this.dateToDays(this.viewStartDate);
        const viewSpan = this.dateToDays(this.viewEndDate) - viewStartDays;
        if (viewSpan <= 0) return;
        const xOf = (date: string) => mL + ((this.dateToDays(date) - viewStartDays) / viewSpan) * cW;

        const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
        const today = new Date().toISOString().split('T')[0];

        // ── Visible indices ──────────────────────────────────────────────────
        const vis = this.visibleIndices;

        // ── Scales (auto-scale to visible window) ────────────────────────────
        let maxLoad = 100, tsbMax = 30, maxTss = 100;
        let visSlice: PmcDataPoint[] = [];
        if (vis) {
            visSlice = points.slice(vis.start, vis.end + 1);
            maxLoad = Math.max(...visSlice.map(p => p.ctl), ...visSlice.map(p => p.atl), 1) * 1.15;
            tsbMax = Math.max(...visSlice.map(p => Math.abs(p.tsb)), 1) * 1.2;
            maxTss = Math.max(...visSlice.map(p => p.dailyTss), 1);
        }

        const yLoad = (v: number) => mT + cH * (1 - v / maxLoad);
        const yTsb = (v: number) => mT + cH * (1 - (v + tsbMax) / (2 * tsbMax));
        const zeroY = yTsb(0);

        const todayIdx = points.findIndex(p => p.date === today);
        const predStart = points.findIndex(p => p.predicted);

        // ── Future background ─────────────────────────────────────────────────
        const splitIdx = predStart > 0 ? predStart - 1
            : todayIdx > 0 ? todayIdx
                : -1;
        if (splitIdx >= 0) {
            const splitDate = points[splitIdx].date;
            if (splitDate >= this.viewStartDate && splitDate <= this.viewEndDate) {
                const splitX = xOf(splitDate);
                if (splitX < W - mR) {
                    ctx.fillStyle = 'rgba(0, 0, 0, 0.22)';
                    ctx.fillRect(splitX, mT, W - mR - splitX, cH);
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.25)';
                    ctx.font = FONT_XS;
                    ctx.textAlign = 'left';
                    ctx.fillText('PROJECTED', splitX + 6, mT + 12);
                }
            } else if (splitDate < this.viewStartDate) {
                // Entire visible range is in the future
                ctx.fillStyle = 'rgba(0, 0, 0, 0.22)';
                ctx.fillRect(mL, mT, cW, cH);
                ctx.fillStyle = 'rgba(255, 255, 255, 0.25)';
                ctx.font = FONT_XS;
                ctx.textAlign = 'left';
                ctx.fillText('PROJECTED', mL + 6, mT + 12);
            }
        }

        // ── Data-dependent rendering (only if we have visible data) ──────────
        if (vis) {
            const { start, end } = vis;

            // ── Daily TSS bars ────────────────────────────────────────────────
            const visCount = end - start;
            const barW = Math.max(1.5, cW / Math.max(visCount, 1) - 0.5);
            for (let i = start; i <= end; i++) {
                const p = points[i];
                const x = xOf(p.date) - barW / 2;
                let currentY = H - mB;

                if (p.sportTss && Object.keys(p.sportTss).length > 0) {
                    Object.entries(p.sportTss).forEach(([sport, tss]) => {
                        if (tss <= 0) return;
                        const bH = (tss / maxTss) * cH;
                        ctx.fillStyle = getSportColor(sport);
                        ctx.globalAlpha = 0.4;
                        ctx.fillRect(x, currentY - bH, barW, bH);
                        currentY -= bH;
                    });
                } else {
                    const bH = (p.dailyTss / maxTss) * cH;
                    ctx.fillStyle = 'rgba(255,255,255,0.08)';
                    ctx.fillRect(x, currentY - bH, barW, bH);
                }
            }

            // ── Fatigue zone fill ─────────────────────────────────────────────
            ctx.save();
            ctx.beginPath();
            ctx.globalAlpha = 1;
            let inFatigue = false;
            for (let i = start; i <= end; i++) {
                const p = points[i];
                const x = xOf(p.date), y = yTsb(p.tsb);
                if (p.tsb < 0) {
                    if (!inFatigue) { ctx.moveTo(x, Math.min(y, zeroY)); inFatigue = true; }
                    ctx.lineTo(x, y);
                } else if (inFatigue) {
                    ctx.lineTo(x, zeroY);
                    inFatigue = false;
                }
            }
            if (inFatigue) ctx.lineTo(xOf(points[end].date), zeroY);
            ctx.closePath();
            ctx.fillStyle = 'rgba(239,68,68,0.06)';
            ctx.fill();
            ctx.restore();
        }

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
        ctx.strokeStyle = 'rgba(255,255,255,0.1)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(mL, zeroY); ctx.lineTo(W - mR, zeroY);
        ctx.stroke();
        ctx.restore();

        // ── Adaptive X-axis ticks (date arithmetic) ───────────────────────────
        const visDays = viewSpan;
        if (visDays <= 14) {
            for (let d = 0; d <= visDays; d++) {
                const dateStr = this.addDaysToDate(this.viewStartDate, d);
                const x = xOf(dateStr);
                const label = new Date(dateStr + 'T12:00:00')
                    .toLocaleDateString('en', { month: 'short', day: 'numeric' });
                ctx.fillStyle = 'rgba(255,255,255,0.4)';
                ctx.font = FONT_SM;
                ctx.textAlign = 'center';
                ctx.fillText(label, x, H - 10);
                ctx.save();
                ctx.strokeStyle = 'rgba(255,255,255,0.04)';
                ctx.lineWidth = 1;
                ctx.setLineDash([]);
                ctx.beginPath();
                ctx.moveTo(x, mT); ctx.lineTo(x, H - mB);
                ctx.stroke();
                ctx.restore();
            }
        } else if (visDays <= 60) {
            for (let d = 0; d <= visDays; d++) {
                const dateStr = this.addDaysToDate(this.viewStartDate, d);
                const dt = new Date(dateStr + 'T12:00:00');
                if (dt.getDay() === 1) { // Monday
                    const x = xOf(dateStr);
                    ctx.save();
                    ctx.strokeStyle = 'rgba(255,255,255,0.07)';
                    ctx.lineWidth = 1;
                    ctx.setLineDash([]);
                    ctx.beginPath();
                    ctx.moveTo(x, mT); ctx.lineTo(x, H - mB);
                    ctx.stroke();
                    ctx.restore();
                    const label = dt.toLocaleDateString('en', { month: 'short', day: 'numeric' });
                    ctx.fillStyle = 'rgba(255,255,255,0.4)';
                    ctx.font = FONT_SM;
                    ctx.textAlign = 'center';
                    ctx.fillText(label, x, H - 10);
                }
            }
        } else {
            // Month boundaries
            let lastMonth = '';
            for (let d = 0; d <= visDays; d++) {
                const dateStr = this.addDaysToDate(this.viewStartDate, d);
                const month = dateStr.substring(0, 7);
                if (month !== lastMonth) {
                    lastMonth = month;
                    const x = xOf(dateStr);
                    ctx.save();
                    ctx.strokeStyle = 'rgba(255,255,255,0.07)';
                    ctx.lineWidth = 1;
                    ctx.setLineDash([]);
                    ctx.beginPath();
                    ctx.moveTo(x, mT); ctx.lineTo(x, H - mB);
                    ctx.stroke();
                    ctx.restore();
                    const label = new Date(dateStr + 'T12:00:00')
                        .toLocaleDateString('en', { month: 'short', year: '2-digit' });
                    ctx.fillStyle = 'rgba(255,255,255,0.4)';
                    ctx.font = FONT_SM;
                    ctx.textAlign = 'center';
                    ctx.fillText(label, x, H - 10);
                }
            }
        }

        // ── Lines: solid past, dotted future ──────────────────────────────────
        if (vis) {
            const { start, end } = vis;

            const drawLine = (getY: (p: PmcDataPoint) => number, color: string, lineW: number) => {
                ctx.save();
                ctx.strokeStyle = color;
                ctx.lineWidth = lineW;
                ctx.setLineDash([]);
                ctx.beginPath();
                let started = false;
                for (let i = start; i <= end; i++) {
                    const p = points[i];
                    if (p.predicted) continue;
                    const x = xOf(p.date), y = getY(p);
                    if (!started) { ctx.moveTo(x, y); started = true; }
                    else ctx.lineTo(x, y);
                }
                ctx.stroke();
                ctx.restore();

                if (predStart < 0) return;
                ctx.save();
                ctx.strokeStyle = color;
                ctx.lineWidth = lineW;
                ctx.setLineDash([1, 3]);
                ctx.beginPath();
                let pStarted = false;
                if (predStart > 0 && predStart - 1 >= start && predStart - 1 <= end) {
                    ctx.moveTo(xOf(points[predStart - 1].date), getY(points[predStart - 1]));
                    pStarted = true;
                }
                for (let i = Math.max(start, predStart); i <= end; i++) {
                    const p = points[i];
                    if (!p.predicted) continue;
                    const x = xOf(p.date), y = getY(p);
                    if (!pStarted) { ctx.moveTo(x, y); pStarted = true; }
                    else ctx.lineTo(x, y);
                }
                ctx.stroke();
                ctx.restore();
            };

            drawLine(p => yLoad(p.ctl), accent, 2.5);
            drawLine(p => yLoad(p.atl), '#e74c3c', 2);
            drawLine(p => yTsb(p.tsb), '#3b82f6', 2);
        }

        // ── Today marker ──────────────────────────────────────────────────────
        if (today >= this.viewStartDate && today <= this.viewEndDate) {
            const tx = xOf(today);
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

        // ── Race Goal markers ─────────────────────────────────────────────────
        const goalsToShow = (this.goals ?? []).filter(g =>
            g.raceDate >= this.viewStartDate && g.raceDate <= this.viewEndDate
        );
        const sortedGoals = [...goalsToShow].sort((a, b) => {
            const order: Record<string, number> = { C: 0, B: 1, A: 2 };
            return (order[a.priority] ?? 0) - (order[b.priority] ?? 0);
        });
        const usedLabelYs: number[] = [];

        for (const goal of sortedGoals) {
            const gx = xOf(goal.raceDate);
            const color = PRIORITY_COLORS[goal.priority] ?? '#9CA3AF';
            const isA = goal.priority === 'A';

            if (isA) {
                ctx.save();
                ctx.fillStyle = color;
                ctx.globalAlpha = 0.06;
                ctx.fillRect(gx - 8, mT, 16, cH);
                ctx.restore();
            }

            ctx.save();
            ctx.strokeStyle = color;
            ctx.lineWidth = isA ? 2 : 1.5;
            ctx.globalAlpha = isA ? 0.85 : 0.5;
            ctx.setLineDash([]);
            ctx.beginPath();
            ctx.moveTo(gx, mT); ctx.lineTo(gx, H - mB);
            ctx.stroke();

            ctx.fillStyle = color;
            ctx.globalAlpha = 0.9;
            ctx.beginPath();
            if (isA) {
                ctx.moveTo(gx, mT + 2);
                ctx.lineTo(gx + 14, mT + 9);
                ctx.lineTo(gx, mT + 16);
            } else {
                ctx.moveTo(gx, mT + 2);
                ctx.lineTo(gx + 10, mT + 7);
                ctx.lineTo(gx, mT + 12);
            }
            ctx.closePath();
            ctx.fill();

            ctx.globalAlpha = 1;
            ctx.fillStyle = color;
            ctx.font = isA ? `bold ${FONT}` : `bold ${FONT_XS}`;
            ctx.textAlign = 'center';
            ctx.fillText(goal.priority, gx - 5, mT - 2);

            let labelY = mT + (isA ? 26 : 22);
            for (const usedY of usedLabelYs) {
                if (Math.abs(gx - usedY) < 60) {
                    labelY += 22;
                }
            }
            usedLabelYs.push(gx);

            const title = goal.title.length > 14 ? goal.title.substring(0, 13) + '\u2026' : goal.title;
            ctx.fillStyle = isA ? 'rgba(255,255,255,0.9)' : 'rgba(255,255,255,0.7)';
            ctx.font = isA ? `bold ${FONT_SM}` : FONT_XS;
            ctx.textAlign = 'left';
            ctx.fillText(title, gx + 3, labelY);

            const dateLabel = new Date(goal.raceDate + 'T12:00:00')
                .toLocaleDateString('en', { month: 'short', day: 'numeric' });
            ctx.fillStyle = 'rgba(255,255,255,0.4)';
            ctx.font = FONT_XS;
            ctx.fillText(dateLabel, gx + 3, labelY + 12);
            ctx.restore();
        }

        // ── Peak TSB dot ──────────────────────────────────────────────────────
        if (vis) {
            const realVisible = visSlice.filter(p => !p.predicted);
            if (realVisible.length) {
                const peak = realVisible.reduce((a, b) => a.tsb > b.tsb ? a : b);
                if (peak.tsb > 5) {
                    const px = xOf(peak.date), py = yTsb(peak.tsb);
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
            { label: 'CTL \u2014 Fitness', color: accent, bar: false, fill: false },
            { label: 'ATL \u2014 Fatigue', color: '#e74c3c', bar: false, fill: false },
            { label: 'TSB \u2014 Form', color: '#3b82f6', bar: false, fill: false },
            { label: 'Daily TSS', color: 'rgba(255,255,255,0.3)', bar: true, fill: false },
            { label: 'Fatigue zone', color: 'rgba(239,68,68,0.2)', bar: false, fill: true },
        ];
        const itemW = cW / legendItems.length;
        const ly0 = 22;
        legendItems.forEach((item, idx) => {
            const lx = mL + idx * itemW + 4;
            if (item.bar) {
                ctx.fillStyle = 'rgba(255,255,255,0.3)';
                ctx.fillRect(lx, ly0 - 7, 10, 12);
            } else if (item.fill) {
                ctx.fillStyle = 'rgba(239,68,68,0.15)';
                ctx.fillRect(lx, ly0 - 7, 14, 12);
                ctx.strokeStyle = 'rgba(239,68,68,0.4)';
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
        if (this.hoverIdx === null || !vis || this.hoverIdx < vis.start || this.hoverIdx > vis.end) return;
        const p = points[this.hoverIdx];
        const hx = xOf(p.date);

        ctx.save();
        ctx.strokeStyle = 'rgba(255,255,255,0.25)';
        ctx.lineWidth = 1;
        ctx.setLineDash([]);
        ctx.beginPath();
        ctx.moveTo(hx, mT); ctx.lineTo(hx, H - mB);
        ctx.stroke();
        ctx.restore();

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
        const boxH = pad + 18 + rows.length * rowH + pad;
        const dateStr = new Date(p.date + 'T12:00:00').toLocaleDateString('en', { month: 'short', day: 'numeric', year: 'numeric' });

        let tx = hx + 14;
        if (tx + boxW > W - mR) tx = hx - boxW - 14;
        let ty = mT + 16;
        if (ty + boxH > H - mB) ty = H - mB - boxH;

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

        ctx.fillStyle = 'rgba(255,255,255,0.8)';
        ctx.font = FONT_XS;
        ctx.textAlign = 'left';
        ctx.fillText(dateStr, tx + pad, ty + pad + 8);

        ctx.fillStyle = 'rgba(255,255,255,0.15)';
        ctx.fillRect(tx + pad, ty + pad + 13, boxW - pad * 2, 1);

        rows.forEach((row, ri) => {
            const ry = ty + pad + 18 + ri * rowH + 11;
            ctx.fillStyle = 'rgba(255,255,255,0.65)';
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

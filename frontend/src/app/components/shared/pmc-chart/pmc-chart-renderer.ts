import {PmcDataPoint} from '../../../services/metrics.service';
import {PRIORITY_COLORS, RaceGoal} from '../../../services/race-goal.service';
import {addDaysToDate, dateToDays, getSportColor, roundRect} from './pmc-chart.utils';

export interface PmcRenderInput {
    data: PmcDataPoint[];
    goals: RaceGoal[];
    viewStartDate: string;
    viewEndDate: string;
    hoverIdx: number | null;
    marginLeft: number;
    marginRight: number;
}

const FONT = '11px Inter, system-ui, sans-serif';
const FONT_SM = '10px Inter, system-ui, sans-serif';
const FONT_XS = '9px Inter, system-ui, sans-serif';

interface VisibleRange {
    start: number;
    end: number;
}

function visibleIndices(points: PmcDataPoint[], viewStartDate: string, viewEndDate: string): VisibleRange | null {
    if (points.length < 2 || !viewStartDate) return null;
    let s = 0;
    while (s < points.length && points[s].date < viewStartDate) s++;
    if (s >= points.length) return null;
    let e = points.length - 1;
    while (e > s && points[e].date > viewEndDate) e--;
    return s <= e ? { start: s, end: e } : null;
}

export function drawPmcChart(canvas: HTMLCanvasElement, input: PmcRenderInput): void {
    const W = (canvas.width = canvas.offsetWidth || 700);
    const H = (canvas.height = canvas.offsetHeight || 420);
    const ctx = canvas.getContext('2d')!;
    ctx.clearRect(0, 0, W, H);

    const points = input.data ?? [];
    const mL = input.marginLeft, mR = input.marginRight, mT = 60, mB = 36;
    const cW = W - mL - mR;
    const cH = H - mT - mB;

    if (!input.viewStartDate || !input.viewEndDate) {
        ctx.globalAlpha = 0.3;
        ctx.fillStyle = '#fff';
        ctx.font = FONT;
        ctx.textAlign = 'center';
        ctx.fillText('No PMC data for this period', W / 2, H / 2);
        ctx.globalAlpha = 1;
        return;
    }

    const viewStartDays = dateToDays(input.viewStartDate);
    const viewSpan = dateToDays(input.viewEndDate) - viewStartDays;
    if (viewSpan <= 0) return;
    const xOf = (date: string) => mL + ((dateToDays(date) - viewStartDays) / viewSpan) * cW;

    const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#ff9d00';
    const today = new Date().toISOString().split('T')[0];

    const vis = visibleIndices(points, input.viewStartDate, input.viewEndDate);

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

    drawFutureBackground(ctx, points, predStart, todayIdx, input.viewStartDate, input.viewEndDate, xOf, mL, mT, mR, cW, cH, W);

    if (vis) {
        drawTssBars(ctx, points, vis, xOf, accent, maxTss, cH, H, mB);
        drawFatigueZone(ctx, points, vis, xOf, yTsb, zeroY);
    }

    drawGridAndZeroLine(ctx, mL, mT, mR, W, cH, zeroY);
    drawXAxisTicks(ctx, input.viewStartDate, viewSpan, xOf, mT, H, mB);

    if (vis) {
        drawLines(ctx, points, vis, predStart, xOf, yLoad, yTsb, accent);
    }

    drawTodayMarker(ctx, today, input.viewStartDate, input.viewEndDate, xOf, accent, mT, H, mB);
    drawGoals(ctx, input.goals ?? [], input.viewStartDate, input.viewEndDate, xOf, mT, mB, cH, H);

    if (vis) {
        drawPeakTsb(ctx, visSlice, xOf, yTsb);
    }

    drawYAxisLabels(ctx, mL, mT, mR, W, accent, maxLoad, tsbMax, yLoad, yTsb);
    drawLegend(ctx, mL, cW, accent);

    if (input.hoverIdx !== null && vis && input.hoverIdx >= vis.start && input.hoverIdx <= vis.end) {
        drawHoverTooltip(ctx, points[input.hoverIdx], xOf, yLoad, yTsb, accent, mT, mR, mB, W, H);
    }
}

function drawFutureBackground(
    ctx: CanvasRenderingContext2D,
    points: PmcDataPoint[],
    predStart: number,
    todayIdx: number,
    viewStartDate: string,
    viewEndDate: string,
    xOf: (date: string) => number,
    mL: number, mT: number, mR: number, cW: number, cH: number, W: number,
): void {
    const splitIdx = predStart > 0 ? predStart - 1 : todayIdx > 0 ? todayIdx : -1;
    if (splitIdx < 0) return;

    const splitDate = points[splitIdx].date;
    if (splitDate >= viewStartDate && splitDate <= viewEndDate) {
        const splitX = xOf(splitDate);
        if (splitX < W - mR) {
            ctx.fillStyle = 'rgba(0, 0, 0, 0.22)';
            ctx.fillRect(splitX, mT, W - mR - splitX, cH);
            ctx.fillStyle = 'rgba(255, 255, 255, 0.25)';
            ctx.font = FONT_XS;
            ctx.textAlign = 'left';
            ctx.fillText('PROJECTED', splitX + 6, mT + 12);
        }
    } else if (splitDate < viewStartDate) {
        ctx.fillStyle = 'rgba(0, 0, 0, 0.22)';
        ctx.fillRect(mL, mT, cW, cH);
        ctx.fillStyle = 'rgba(255, 255, 255, 0.25)';
        ctx.font = FONT_XS;
        ctx.textAlign = 'left';
        ctx.fillText('PROJECTED', mL + 6, mT + 12);
    }
}

function drawTssBars(
    ctx: CanvasRenderingContext2D,
    points: PmcDataPoint[],
    vis: VisibleRange,
    xOf: (date: string) => number,
    accent: string,
    maxTss: number,
    cH: number, H: number, mB: number,
): void {
    const { start, end } = vis;
    const visCount = end - start;
    const cW = ctx.canvas.width;
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
        } else if (p.dailyTss > 0) {
            const bH = (p.dailyTss / maxTss) * cH;
            if (p.predicted) {
                ctx.fillStyle = 'rgba(255,255,255,0.28)';
                ctx.fillRect(x, currentY - bH, barW, bH);
                ctx.fillStyle = accent;
                ctx.globalAlpha = 0.9;
                ctx.fillRect(x, currentY - bH, barW, Math.min(2, bH));
                ctx.globalAlpha = 1;
            } else {
                ctx.fillStyle = 'rgba(255,255,255,0.08)';
                ctx.fillRect(x, currentY - bH, barW, bH);
            }
        }
    }
    ctx.globalAlpha = 1;
}

function drawFatigueZone(
    ctx: CanvasRenderingContext2D,
    points: PmcDataPoint[],
    vis: VisibleRange,
    xOf: (date: string) => number,
    yTsb: (v: number) => number,
    zeroY: number,
): void {
    const { start, end } = vis;
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

function drawGridAndZeroLine(
    ctx: CanvasRenderingContext2D,
    mL: number, mT: number, mR: number, W: number, cH: number, zeroY: number,
): void {
    [0.25, 0.5, 0.75, 1].forEach(frac => {
        const y = mT + cH * (1 - frac);
        ctx.beginPath();
        ctx.strokeStyle = 'rgba(255,255,255,0.07)';
        ctx.lineWidth = 1;
        ctx.moveTo(mL, y); ctx.lineTo(W - mR, y);
        ctx.stroke();
    });

    ctx.save();
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = 'rgba(255,255,255,0.1)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(mL, zeroY); ctx.lineTo(W - mR, zeroY);
    ctx.stroke();
    ctx.restore();
}

function drawXAxisTicks(
    ctx: CanvasRenderingContext2D,
    viewStartDate: string,
    visDays: number,
    xOf: (date: string) => number,
    mT: number, H: number, mB: number,
): void {
    if (visDays <= 14) {
        for (let d = 0; d <= visDays; d++) {
            const dateStr = addDaysToDate(viewStartDate, d);
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
            const dateStr = addDaysToDate(viewStartDate, d);
            const dt = new Date(dateStr + 'T12:00:00');
            if (dt.getDay() === 1) {
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
        let lastMonth = '';
        for (let d = 0; d <= visDays; d++) {
            const dateStr = addDaysToDate(viewStartDate, d);
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
}

function drawLines(
    ctx: CanvasRenderingContext2D,
    points: PmcDataPoint[],
    vis: VisibleRange,
    predStart: number,
    xOf: (date: string) => number,
    yLoad: (v: number) => number,
    yTsb: (v: number) => number,
    accent: string,
): void {
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
        ctx.setLineDash([3, 4]);
        ctx.globalAlpha = 0.7;
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

function drawTodayMarker(
    ctx: CanvasRenderingContext2D,
    today: string, viewStartDate: string, viewEndDate: string,
    xOf: (date: string) => number,
    accent: string,
    mT: number, H: number, mB: number,
): void {
    if (today < viewStartDate || today > viewEndDate) return;
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

function drawGoals(
    ctx: CanvasRenderingContext2D,
    goals: RaceGoal[],
    viewStartDate: string, viewEndDate: string,
    xOf: (date: string) => number,
    mT: number, mB: number, cH: number, H: number,
): void {
    const goalsToShow = goals.filter(g => {
        const date = g.race?.scheduledDate;
        return !!date && date >= viewStartDate && date <= viewEndDate;
    });
    const sortedGoals = [...goalsToShow].sort((a, b) => {
        const order: Record<string, number> = { C: 0, B: 1, A: 2 };
        return (order[a.priority] ?? 0) - (order[b.priority] ?? 0);
    });
    const usedLabelBoxes: Array<{ x: number; y: number; w: number; h: number }> = [];

    for (const goal of sortedGoals) {
        const goalDate = goal.race?.scheduledDate;
        if (!goalDate) continue;
        const gx = xOf(goalDate);
        const color = PRIORITY_COLORS[goal.priority] ?? '#9CA3AF';
        const isA = goal.priority === 'A';

        if (isA) {
            ctx.save();
            for (const [w, a] of [[6, 0.03], [4, 0.05], [2, 0.08]] as [number, number][]) {
                ctx.strokeStyle = color;
                ctx.globalAlpha = a;
                ctx.lineWidth = w;
                ctx.setLineDash([]);
                ctx.beginPath();
                ctx.moveTo(gx, mT); ctx.lineTo(gx, H - mB);
                ctx.stroke();
            }
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

        const badgeW = isA ? 18 : 16;
        const badgeH = isA ? 16 : 14;
        const badgeX = gx - badgeW / 2;
        const badgeY = mT - badgeH - 4;
        ctx.globalAlpha = 1;
        ctx.fillStyle = color;
        roundRect(ctx, badgeX, badgeY, badgeW, badgeH, 4);
        ctx.fill();
        ctx.fillStyle = '#000';
        ctx.font = `bold ${isA ? FONT_SM : FONT_XS}`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(goal.priority, gx, badgeY + badgeH / 2);
        ctx.textBaseline = 'alphabetic';

        const title = goal.title.length > 21 ? goal.title.substring(0, 20) + '…' : goal.title;
        ctx.font = isA ? `bold ${FONT_SM}` : FONT_XS;
        const titleWidth = ctx.measureText(title).width;
        const dateLabel = new Date(goalDate + 'T12:00:00')
            .toLocaleDateString('en', { month: 'short', day: 'numeric' });
        ctx.font = FONT_XS;
        const dateWidth = ctx.measureText(dateLabel).width;
        const labelW = Math.max(titleWidth, dateWidth) + 8;
        const goalDistance = (goal as { distance?: string }).distance;
        const labelH = goalDistance ? 40 : 28;

        let labelY = mT + (isA ? 26 : 22);
        for (const box of usedLabelBoxes) {
            if (gx + 3 < box.x + box.w && gx + 3 + labelW > box.x &&
                labelY - 12 < box.y + box.h && labelY - 12 + labelH > box.y) {
                labelY = box.y + box.h + 4;
            }
        }
        usedLabelBoxes.push({ x: gx + 3, y: labelY - 12, w: labelW, h: labelH });

        ctx.globalAlpha = 1;
        ctx.fillStyle = 'rgba(15, 15, 17, 0.88)';
        roundRect(ctx, gx - 1, labelY - 12, labelW + 4, labelH, 4);
        ctx.fill();

        ctx.fillStyle = isA ? 'rgba(255,255,255,0.9)' : 'rgba(255,255,255,0.7)';
        ctx.font = isA ? `bold ${FONT_SM}` : FONT_XS;
        ctx.textAlign = 'left';
        ctx.fillText(title, gx + 3, labelY);

        ctx.fillStyle = 'rgba(255,255,255,0.4)';
        ctx.font = FONT_XS;
        ctx.fillText(dateLabel, gx + 3, labelY + 12);

        if (goalDistance) {
            ctx.fillStyle = 'rgba(255,255,255,0.3)';
            ctx.font = FONT_XS;
            ctx.fillText(goalDistance, gx + 3, labelY + 24);
        }
        ctx.restore();
    }
}

function drawPeakTsb(
    ctx: CanvasRenderingContext2D,
    visSlice: PmcDataPoint[],
    xOf: (date: string) => number,
    yTsb: (v: number) => number,
): void {
    const realVisible = visSlice.filter(p => !p.predicted);
    if (!realVisible.length) return;
    const peak = realVisible.reduce((a, b) => a.tsb > b.tsb ? a : b);
    if (peak.tsb <= 5) return;

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

function drawYAxisLabels(
    ctx: CanvasRenderingContext2D,
    mL: number, mT: number, mR: number, W: number,
    accent: string,
    maxLoad: number, tsbMax: number,
    yLoad: (v: number) => number, yTsb: (v: number) => number,
): void {
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
}

function drawLegend(
    ctx: CanvasRenderingContext2D,
    mL: number, cW: number, accent: string,
): void {
    const legendItems: Array<{ label: string; color: string; kind: 'line' | 'bar' | 'planned' | 'fill' }> = [
        { label: 'CTL — Fitness', color: accent, kind: 'line' },
        { label: 'ATL — Fatigue', color: '#e74c3c', kind: 'line' },
        { label: 'TSB — Form', color: '#3b82f6', kind: 'line' },
        { label: 'Daily TSS', color: 'rgba(255,255,255,0.3)', kind: 'bar' },
        { label: 'Planned TSS', color: accent, kind: 'planned' },
        { label: 'Fatigue zone', color: 'rgba(239,68,68,0.2)', kind: 'fill' },
    ];
    const itemW = cW / legendItems.length;
    const ly0 = 22;
    legendItems.forEach((item, idx) => {
        const lx = mL + idx * itemW + 4;
        if (item.kind === 'bar') {
            ctx.fillStyle = 'rgba(255,255,255,0.3)';
            ctx.fillRect(lx, ly0 - 7, 10, 12);
        } else if (item.kind === 'planned') {
            ctx.fillStyle = 'rgba(255,255,255,0.28)';
            ctx.fillRect(lx, ly0 - 7, 10, 12);
            ctx.fillStyle = item.color;
            ctx.globalAlpha = 0.9;
            ctx.fillRect(lx, ly0 - 7, 10, 2);
            ctx.globalAlpha = 1;
        } else if (item.kind === 'fill') {
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
}

function drawHoverTooltip(
    ctx: CanvasRenderingContext2D,
    p: PmcDataPoint,
    xOf: (date: string) => number,
    yLoad: (v: number) => number, yTsb: (v: number) => number,
    accent: string,
    mT: number, mR: number, mB: number, W: number, H: number,
): void {
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
    roundRect(ctx, tx, ty, boxW, boxH, 8);
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

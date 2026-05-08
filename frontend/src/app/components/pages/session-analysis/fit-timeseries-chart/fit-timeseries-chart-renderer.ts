import {FitRecord} from '../../../../services/metrics.service';
import {BlockSummary} from '../../../../services/workout-execution.service';
import {ZoneBlock} from '../../../../services/zone';
import {
    accentAlphaFromRgb,
    cssToRgb,
    getCadBlockFromValue,
    getCadFromRecord,
    marginsForWidth,
    pickTickInterval,
    speedToPlotValue,
    ThemeColors,
} from './fit-timeseries-chart.utils';
import {
    HoverContext,
    hoverCadence,
    hoverEfficiency,
    hoverHR,
    hoverPrimaryValue,
} from './fit-timeseries-chart-tooltip';

export interface RenderCanvases {
    primary?: HTMLCanvasElement | null;
    hr?: HTMLCanvasElement | null;
    cad?: HTMLCanvasElement | null;
    eff?: HTMLCanvasElement | null;
    elev?: HTMLCanvasElement | null;
    xAxis?: HTMLCanvasElement | null;
}

export interface RenderInput {
    records: FitRecord[];
    downsampled: FitRecord[];
    sportType: string;
    ftp: number | null;
    zoneBlocks: ZoneBlock[];
    blockSummaries: BlockSummary[];
    blockColors: string[];
    showBlocks: boolean;
    showPrimary: boolean;
    showHR: boolean;
    showCadence: boolean;
    showEfficiency: boolean;
    hasElevation: boolean;
    hoverIdx: number | null;
    theme: ThemeColors;
}

export interface RenderResult {
    primaryMin: number;
    primaryMax: number;
    effSmoothed: number[];
}

interface CanvasFrame {
    ctx: CanvasRenderingContext2D;
    W: number;
    H: number;
    cW: number;
    xOf: (i: number) => number;
    xOfT: (sec: number) => number;
    mT: number;
    mB: number;
    mL: number;
    mR: number;
}

function initCanvas(canvas: HTMLCanvasElement | null | undefined, records: FitRecord[]): CanvasFrame | null {
    if (!canvas) return null;
    const dpr = window.devicePixelRatio || 1;
    const W = canvas.offsetWidth || 600;
    const H = canvas.offsetHeight || 100;
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    const ctx = canvas.getContext('2d')!;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);
    if (!records.length) return null;

    const { mL, mR } = marginsForWidth(W);
    const mT = 6, mB = 6;
    const cW = W - mL - mR;
    const t0 = records[0].timestamp;
    const n = records.length;
    const totalSec = records[n - 1].timestamp - t0 || n;
    const xOf = (i: number) => mL + ((records[i].timestamp - t0) / totalSec) * cW;
    const xOfT = (sec: number) => mL + (sec / totalSec) * cW;
    return { ctx, W, H, cW, xOf, xOfT, mT, mB, mL, mR };
}

function drawCrosshair(
    ctx: CanvasRenderingContext2D,
    theme: ThemeColors,
    hoverIdx: number | null,
    x: number,
    top: number,
    bottom: number,
): void {
    if (hoverIdx === null) return;
    ctx.save();
    ctx.strokeStyle = theme.crosshairAlpha;
    ctx.lineWidth = 1;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(x, top);
    ctx.lineTo(x, bottom);
    ctx.stroke();
    ctx.restore();
}

function drawDot(
    ctx: CanvasRenderingContext2D,
    theme: ThemeColors,
    x: number,
    y: number,
    color: string,
): void {
    ctx.beginPath();
    ctx.arc(x, y, 4, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.strokeStyle = theme.dotStroke;
    ctx.lineWidth = 1.5;
    ctx.stroke();
}

function drawBlockBounds(
    ctx: CanvasRenderingContext2D,
    input: RenderInput,
    xOfT: (s: number) => number,
    top: number,
    bottom: number,
): void {
    if (!input.blockSummaries.length || input.showBlocks) return;
    ctx.save();
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = input.theme.gridAlpha12;
    ctx.lineWidth = 1;
    let acc = 0;
    for (let i = 0; i < input.blockSummaries.length - 1; i++) {
        acc += input.blockSummaries[i].durationSeconds;
        const x = xOfT(acc);
        ctx.beginPath();
        ctx.moveTo(x, top);
        ctx.lineTo(x, bottom);
        ctx.stroke();
    }
    ctx.restore();
}

function drawSteppedLine(
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

function drawSteppedBlockLine(
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

const isCycling = (sportType: string) => sportType === 'CYCLING';
const isSwimming = (sportType: string) => sportType === 'SWIMMING';

const useZoneBlocks = (input: RenderInput): boolean =>
    input.showBlocks && input.zoneBlocks.length > 0;
const usePlannedBlocks = (input: RenderInput): boolean =>
    input.showBlocks && !useZoneBlocks(input) && input.blockSummaries.length > 0;

function buildHoverContext(input: RenderInput, primaryMax: number, effSmoothed: number[]): HoverContext | null {
    if (input.hoverIdx === null) return null;
    return {
        records: input.records,
        downsampled: input.downsampled,
        effSmoothed,
        sportType: input.sportType,
        zoneBlocks: input.zoneBlocks,
        blockSummaries: input.blockSummaries,
        showBlocks: input.showBlocks,
        primaryMax,
        showPrimary: input.showPrimary,
        showHR: input.showHR,
        showCadence: input.showCadence,
        showEfficiency: input.showEfficiency,
        hasElevation: input.hasElevation,
        accentHex: input.theme.accentHex,
        hoverIdx: input.hoverIdx,
    };
}

function drawPrimary(canvas: HTMLCanvasElement | null | undefined, input: RenderInput): { min: number; max: number } {
    const s = initCanvas(canvas, input.records);
    if (!s) return { min: 0, max: 0 };
    const { ctx, W, H, xOf, xOfT, mT, mB, mL, mR } = s;
    const accent = input.theme.accentHex;
    const records = input.records;
    const t0 = records[0].timestamp;
    const totalSec = records[records.length - 1].timestamp - t0 || records.length;
    const chartH = H - mT - mB;
    const top = mT, bottom = mT + chartH;
    const cycling = isCycling(input.sportType);
    const swimming = isSwimming(input.sportType);

    let maxP = 1, maxS = 1;
    let minPace = 0, maxPace = 1;
    let primaryMin = 0, primaryMax = 0;
    let yOf: (v: number) => number;
    if (cycling) {
        maxP = Math.max(input.ftp ? input.ftp * 1.5 : 0, ...records.map(r => r.power)) || 1;
        yOf = (v) => top + chartH * (1 - v / maxP);
        primaryMax = maxP;
    } else if (swimming) {
        const paces = records
            .map(r => (r.speed || 0) * 3.6)
            .filter(v => v > 0.5)
            .map(v => 360 / v);
        if (paces.length === 0) {
            minPace = 60; maxPace = 180;
        } else {
            const lo = Math.min(...paces);
            const hi = Math.max(...paces);
            const pad = Math.max((hi - lo) * 0.05, 2);
            minPace = Math.max(0, lo - pad);
            maxPace = hi + pad;
        }
        primaryMin = minPace;
        primaryMax = maxPace;
        const range = maxPace - minPace || 1;
        yOf = (paceVal) => top + chartH * ((paceVal - minPace) / range);
    } else {
        const sp = records.map(r => (r.speed || 0) * 3.6);
        maxS = Math.max(...sp.filter(v => v > 0), 1);
        yOf = (v) => top + chartH * (1 - v / maxS);
        primaryMax = maxS;
    }

    const plotValue = (speedKmh: number) => speedToPlotValue(speedKmh, swimming, primaryMax);

    if (useZoneBlocks(input)) {
        for (const b of input.zoneBlocks) {
            const x1 = xOf(b.startIndex), x2 = xOf(b.endIndex);
            const val = cycling ? b.avgPower : plotValue(b.avgSpeed);
            const y = yOf(val);
            const [br, bg, bb] = cssToRgb(b.color);
            const hb = `rgb(${br},${bg},${bb})`;
            ctx.fillStyle = `rgba(${br},${bg},${bb},0.25)`;
            ctx.fillRect(x1, y, x2 - x1, bottom - y);
            ctx.strokeStyle = hb;
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(x1, y); ctx.lineTo(x2, y);
            ctx.stroke();
        }
    } else if (usePlannedBlocks(input)) {
        let acc = 0;
        for (let bi = 0; bi < input.blockSummaries.length; bi++) {
            const b = input.blockSummaries[bi];
            const x1 = xOfT(acc), x2 = xOfT(acc + b.durationSeconds);
            const speedKmh = b.distanceMeters && b.durationSeconds > 0 ? (b.distanceMeters / b.durationSeconds) * 3.6 : 0;
            const val = cycling ? b.actualPower : plotValue(speedKmh);
            const y = yOf(val);
            if (b.targetPower > 0) {
                const yt = yOf(b.targetPower);
                ctx.save(); ctx.setLineDash([3, 3]);
                ctx.strokeStyle = input.theme.gridAlpha15; ctx.lineWidth = 1;
                ctx.beginPath(); ctx.moveTo(x1, yt); ctx.lineTo(x2, yt); ctx.stroke();
                ctx.restore();
            }
            const [cr, cg, cb] = cssToRgb(input.blockColors[bi] || accent);
            const bColor = `rgb(${cr},${cg},${cb})`;
            ctx.fillStyle = `rgba(${cr},${cg},${cb},0.25)`;
            ctx.fillRect(x1, y, x2 - x1, bottom - y);
            ctx.strokeStyle = bColor; ctx.lineWidth = 2;
            ctx.beginPath(); ctx.moveTo(x1, y); ctx.lineTo(x2, y); ctx.stroke();
            acc += b.durationSeconds;
        }
    } else {
        const ds = input.downsampled;
        const dsX = (i: number) => xOfT(ds[i].timestamp - t0);
        if (cycling) {
            const vals = ds.map(r => r.power);
            if (input.ftp) {
                const fy = yOf(input.ftp);
                ctx.save(); ctx.setLineDash([4, 4]);
                ctx.strokeStyle = input.theme.gridAlpha15; ctx.lineWidth = 1;
                ctx.beginPath(); ctx.moveTo(mL, fy); ctx.lineTo(W - mR, fy); ctx.stroke();
                ctx.restore();
                ctx.fillStyle = input.theme.textAlpha30;
                ctx.font = '9px monospace'; ctx.textAlign = 'left';
                ctx.fillText('FTP', mL + 2, fy - 3);
            }
            if (vals.length > 1) {
                ctx.beginPath();
                ctx.moveTo(dsX(0), bottom);
                vals.forEach((p, i) => ctx.lineTo(dsX(i), yOf(p)));
                ctx.lineTo(dsX(ds.length - 1), bottom);
                ctx.closePath();
                const g = ctx.createLinearGradient(0, top, 0, bottom);
                g.addColorStop(0, accentAlphaFromRgb(input.theme.accentRgb, 0.5));
                g.addColorStop(1, accentAlphaFromRgb(input.theme.accentRgb, 0.03));
                ctx.fillStyle = g; ctx.fill();
                ctx.beginPath();
                ctx.moveTo(dsX(0), yOf(vals[0]));
                vals.forEach((p, i) => ctx.lineTo(dsX(i), yOf(p)));
                ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.stroke();
            }
        } else {
            const vals = ds.map(r => plotValue((r.speed || 0) * 3.6));
            if (vals.length > 1) {
                ctx.beginPath();
                ctx.moveTo(dsX(0), bottom);
                vals.forEach((v, i) => ctx.lineTo(dsX(i), yOf(v)));
                ctx.lineTo(dsX(ds.length - 1), bottom);
                ctx.closePath();
                const g = ctx.createLinearGradient(0, top, 0, bottom);
                g.addColorStop(0, accentAlphaFromRgb(input.theme.accentRgb, 0.5));
                g.addColorStop(1, accentAlphaFromRgb(input.theme.accentRgb, 0.03));
                ctx.fillStyle = g; ctx.fill();
                ctx.beginPath();
                ctx.moveTo(dsX(0), yOf(vals[0]));
                vals.forEach((v, i) => ctx.lineTo(dsX(i), yOf(v)));
                ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.stroke();
            }
        }
    }

    ctx.fillStyle = input.theme.textAlpha40;
    ctx.font = '9px monospace';
    ctx.textAlign = 'right';
    if (cycling) {
        [0, 0.5, 1].forEach(f => {
            const p = Math.round(maxP * f);
            ctx.fillText(String(p), mL - 4, yOf(p) + 4);
        });
    } else if (swimming) {
        [0, 0.5, 1].forEach(f => {
            const pace = minPace + (maxPace - minPace) * f;
            const m = Math.floor(pace / 60);
            const sec = Math.round(pace % 60);
            const valStr = `${m}:${String(sec).padStart(2, '0')}`;
            const y = yOf(pace);
            ctx.fillText(valStr, mL - 4, y);
            ctx.fillText('/100', mL - 4, y + 10);
        });
    } else {
        [0, 0.5, 1].forEach(f => {
            const v = Math.round(maxS * f * 10) / 10;
            ctx.fillText(v + ' km/h', mL - 4, yOf(v) + 4);
        });
    }

    drawBlockBounds(ctx, input, xOfT, top, bottom);

    if (input.hoverIdx !== null) {
        const hx = xOf(input.hoverIdx);
        drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
        const hCtx = buildHoverContext(input, primaryMax, []);
        if (hCtx) {
            const val = hoverPrimaryValue(hCtx, input.hoverIdx, t0);
            drawDot(ctx, input.theme, hx, yOf(val), accent);
        }
    }

    return { min: primaryMin, max: primaryMax };
}

function drawHR(canvas: HTMLCanvasElement | null | undefined, input: RenderInput, hoverCtx: HoverContext | null): void {
    const s = initCanvas(canvas, input.records);
    if (!s) return;
    const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
    const records = input.records;
    const t0 = records[0].timestamp;
    const chartH = H - mT - mB;
    const top = mT, bottom = mT + chartH;
    const color = '#e74c3c';

    const hrs = records.map(r => r.heartRate).filter(v => v > 0);
    const minHR = 100;
    const maxHR = hrs.length ? Math.max(Math.max(...hrs) * 1.05, minHR + 20) : 220;
    const yOf = (hr: number) => top + chartH * (1 - (Math.max(hr, minHR) - minHR) / (maxHR - minHR));

    if (useZoneBlocks(input)) {
        drawSteppedLine(ctx, xOf, yOf, input.zoneBlocks.map(b => ({
            s: b.startIndex, e: b.endIndex, v: b.avgHR,
        })), color);
    } else if (usePlannedBlocks(input)) {
        drawSteppedBlockLine(ctx, xOfT, yOf, input.blockSummaries.map(b => ({
            dur: b.durationSeconds, v: b.actualHR,
        })), color);
    } else {
        const ds = input.downsampled;
        ctx.beginPath();
        let first = true;
        ds.forEach((r) => {
            if (!r.heartRate) return;
            const x = xOfT(r.timestamp - t0);
            if (first) { ctx.moveTo(x, yOf(r.heartRate)); first = false; }
            else ctx.lineTo(x, yOf(r.heartRate));
        });
        ctx.strokeStyle = color; ctx.lineWidth = 1.5; ctx.stroke();
    }

    ctx.fillStyle = color;
    ctx.font = '9px monospace';
    ctx.textAlign = 'right';
    const mid = Math.round((minHR + maxHR) / 2);
    [Math.round(maxHR), mid, minHR].forEach(v => ctx.fillText(String(v), mL - 4, yOf(v) + 4));

    drawBlockBounds(ctx, input, xOfT, top, bottom);

    if (input.hoverIdx !== null && hoverCtx) {
        const hx = xOf(input.hoverIdx);
        drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
        const hr = hoverHR(hoverCtx, input.hoverIdx, t0);
        if (hr) drawDot(ctx, input.theme, hx, yOf(hr), color);
    }
}

function drawEfficiency(canvas: HTMLCanvasElement | null | undefined, input: RenderInput): number[] {
    if (input.sportType === 'SWIMMING' || input.downsampled.length < 2) return [];

    const ds = input.downsampled;
    const records = input.records;
    const t0 = records[0].timestamp;
    const cycling = isCycling(input.sportType);
    const effSmoothed = ds.map(r => {
        if (r.heartRate <= 0) return NaN;
        const metric = cycling ? r.power : (r.speed || 0) * 3.6;
        return metric > 0 ? metric / r.heartRate : NaN;
    });

    const valid = effSmoothed.filter(v => !isNaN(v));
    if (valid.length < 2) return effSmoothed;
    const effMin = Math.min(...valid) * 0.95;
    const effMax = Math.max(...valid) * 1.05;

    const s = initCanvas(canvas, records);
    if (!s) return effSmoothed;
    const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
    const chartH = H - mT - mB;
    const top = mT, bottom = mT + chartH;
    const range = effMax - effMin || 1;
    const yOf = (v: number) => top + chartH * (1 - (v - effMin) / range);
    const color = '#a855f7';

    const effOfBlock = (power: number, speed: number, hr: number): number => {
        if (hr <= 0) return NaN;
        const metric = cycling ? power : speed * 3.6;
        return metric > 0 ? metric / hr : NaN;
    };

    if (useZoneBlocks(input)) {
        drawSteppedLine(ctx, xOf, yOf, input.zoneBlocks.map(b => ({
            s: b.startIndex, e: b.endIndex, v: effOfBlock(b.avgPower, b.avgSpeed, b.avgHR),
        })), color);
    } else if (usePlannedBlocks(input)) {
        drawSteppedBlockLine(ctx, xOfT, yOf, input.blockSummaries.map(b => {
            const speed = b.distanceMeters && b.durationSeconds > 0 ? b.distanceMeters / b.durationSeconds : 0;
            return { dur: b.durationSeconds, v: effOfBlock(b.actualPower, speed, b.actualHR) };
        }), color);
    } else {
        ctx.beginPath();
        let started = false;
        let lastX = mL;
        effSmoothed.forEach((v, i) => {
            if (isNaN(v)) return;
            const x = xOfT(ds[i].timestamp - t0);
            if (!started) { ctx.moveTo(x, bottom); ctx.lineTo(x, yOf(v)); started = true; }
            else ctx.lineTo(x, yOf(v));
            lastX = x;
        });
        if (started) {
            ctx.lineTo(lastX, bottom);
            ctx.closePath();
            const g = ctx.createLinearGradient(0, top, 0, bottom);
            g.addColorStop(0, 'rgba(168,85,247,0.35)');
            g.addColorStop(1, 'rgba(168,85,247,0.03)');
            ctx.fillStyle = g;
            ctx.fill();
        }

        ctx.beginPath();
        let first = true;
        effSmoothed.forEach((v, i) => {
            if (isNaN(v)) return;
            const x = xOfT(ds[i].timestamp - t0);
            if (first) { ctx.moveTo(x, yOf(v)); first = false; }
            else ctx.lineTo(x, yOf(v));
        });
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.5;
        ctx.stroke();
    }

    ctx.fillStyle = color;
    ctx.font = '9px monospace';
    ctx.textAlign = 'right';
    const mid = (effMin + effMax) / 2;
    [effMax, mid, effMin].forEach(v => ctx.fillText(v.toFixed(2), mL - 4, yOf(v) + 4));

    drawBlockBounds(ctx, input, xOfT, top, bottom);

    if (input.hoverIdx !== null) {
        const hx = xOfT(records[input.hoverIdx].timestamp - t0);
        drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
        const hCtx = buildHoverContext(input, 0, effSmoothed);
        if (hCtx) {
            const v = hoverEfficiency(hCtx, input.hoverIdx, t0);
            if (!isNaN(v)) drawDot(ctx, input.theme, hx, yOf(v), color);
        }
    }

    return effSmoothed;
}

function drawCadence(canvas: HTMLCanvasElement | null | undefined, input: RenderInput, hoverCtx: HoverContext | null): void {
    const s = initCanvas(canvas, input.records);
    if (!s) return;
    const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
    const records = input.records;
    const t0 = records[0].timestamp;
    const chartH = H - mT - mB;
    const top = mT, bottom = mT + chartH;
    const color = '#3b82f6';

    const cads = records.map(r => getCadFromRecord(r, input.sportType)).filter(v => v > 0);
    const minCad = input.sportType === 'RUNNING' ? 140 : 40;
    const maxCad = cads.length ? Math.max(Math.max(...cads) * 1.05, minCad + 20) : 120;
    const yOf = (c: number) => top + chartH * (1 - (Math.max(c, minCad) - minCad) / (maxCad - minCad));

    if (useZoneBlocks(input)) {
        drawSteppedLine(ctx, xOf, yOf, input.zoneBlocks.map(b => ({
            s: b.startIndex, e: b.endIndex, v: getCadBlockFromValue(b.avgCadence, input.sportType),
        })), color);
    } else if (usePlannedBlocks(input)) {
        drawSteppedBlockLine(ctx, xOfT, yOf, input.blockSummaries.map(b => ({
            dur: b.durationSeconds, v: getCadBlockFromValue(b.actualCadence, input.sportType),
        })), color);
    } else {
        const ds = input.downsampled;
        ctx.beginPath();
        let first = true;
        ds.forEach((r) => {
            if (!r.cadence) return;
            const c = getCadFromRecord(r, input.sportType);
            const x = xOfT(r.timestamp - t0);
            if (first) { ctx.moveTo(x, yOf(c)); first = false; }
            else ctx.lineTo(x, yOf(c));
        });
        ctx.strokeStyle = color; ctx.lineWidth = 1.5; ctx.stroke();
    }

    ctx.fillStyle = color;
    ctx.font = '9px monospace';
    ctx.textAlign = 'right';
    const mid = Math.round((minCad + maxCad) / 2);
    [Math.round(maxCad), mid, minCad].forEach(v =>
        ctx.fillText(String(v), mL - 4, yOf(v) + 4));

    drawBlockBounds(ctx, input, xOfT, top, bottom);

    if (input.hoverIdx !== null && hoverCtx) {
        const hx = xOf(input.hoverIdx);
        drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
        const c = hoverCadence(hoverCtx, input.hoverIdx, t0);
        if (c) drawDot(ctx, input.theme, hx, yOf(c), color);
    }
}

function drawElevation(canvas: HTMLCanvasElement | null | undefined, input: RenderInput): void {
    const s = initCanvas(canvas, input.records);
    if (!s) return;
    const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
    const chartH = H - mT - mB;
    const top = mT, bottom = mT + chartH;
    const records = input.records;

    const ds = input.downsampled;
    const t0 = records[0].timestamp;
    const elevs = records.filter(r => r.elevation != null).map(r => r.elevation!);
    if (elevs.length < 2) return;
    const minE = Math.min(...elevs) - 5;
    const maxE = Math.max(...elevs) + 5;
    const range = maxE - minE || 1;
    const yOf = (e: number) => top + chartH * (1 - (e - minE) / range);

    ctx.beginPath();
    let started = false;
    let lastX = mL;
    ds.forEach((r) => {
        if (r.elevation == null) return;
        const x = xOfT(r.timestamp - t0);
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

    ctx.beginPath();
    let first = true;
    ds.forEach((r) => {
        if (r.elevation == null) return;
        const x = xOfT(r.timestamp - t0);
        if (first) { ctx.moveTo(x, yOf(r.elevation)); first = false; }
        else ctx.lineTo(x, yOf(r.elevation));
    });
    ctx.strokeStyle = 'rgba(76,175,80,0.6)';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    ctx.fillStyle = 'rgba(76,175,80,0.6)';
    ctx.font = '9px monospace';
    ctx.textAlign = 'right';
    const mid = Math.round((minE + maxE) / 2);
    [Math.round(maxE), mid, Math.round(minE)].forEach(v =>
        ctx.fillText(v + 'm', mL - 4, yOf(v) + 4));

    if (input.hoverIdx !== null) {
        const hx = xOf(input.hoverIdx);
        drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
        const e = records[input.hoverIdx].elevation;
        if (e != null) drawDot(ctx, input.theme, hx, yOf(e), '#4caf50');
    }
}

function drawXAxis(canvas: HTMLCanvasElement | null | undefined, input: RenderInput): void {
    if (!canvas || !input.records.length) return;
    const dpr = window.devicePixelRatio || 1;
    const W = canvas.offsetWidth || 600;
    const H = canvas.offsetHeight || 22;
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    const ctx = canvas.getContext('2d')!;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);

    const { mL, mR } = marginsForWidth(W);
    const records = input.records;
    const n = records.length;
    const t0 = records[0].timestamp;
    const totalSec = records[n - 1].timestamp - t0 || n;
    const cW = W - mL - mR;

    const tick = pickTickInterval(totalSec);
    ctx.fillStyle = input.theme.textAlpha40;
    ctx.font = '9px monospace';
    ctx.textAlign = 'center';
    for (let s = 0; s <= totalSec; s += tick) {
        const x = mL + (s / totalSec) * cW;
        ctx.fillText(`${Math.round(s / 60)}m`, x, 14);
    }

    if (input.hoverIdx !== null) {
        const hx = mL + ((records[input.hoverIdx].timestamp - t0) / totalSec) * cW;
        ctx.save();
        ctx.strokeStyle = input.theme.crosshairAlpha;
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(hx, 0); ctx.lineTo(hx, 4);
        ctx.stroke();
        ctx.restore();
    }
}

export function drawAll(canvases: RenderCanvases, input: RenderInput): RenderResult {
    const primary = drawPrimary(canvases.primary, input);
    const effSmoothed = drawEfficiency(canvases.eff, input);
    const hoverCtx = buildHoverContext(input, primary.max, effSmoothed);
    drawHR(canvases.hr, input, hoverCtx);
    drawCadence(canvases.cad, input, hoverCtx);
    drawElevation(canvases.elev, input);
    drawXAxis(canvases.xAxis, input);
    return {
        primaryMin: primary.min,
        primaryMax: primary.max,
        effSmoothed,
    };
}

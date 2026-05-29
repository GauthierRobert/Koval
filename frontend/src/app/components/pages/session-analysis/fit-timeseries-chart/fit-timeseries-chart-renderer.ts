import { FitRecord } from '../../../../services/metrics.service';
import { BlockSummary } from '../../../../services/workout-execution.service';
import { ZoneBlock } from '../../../../services/zone';
import {
  accentAlphaFromRgb,
  cssToRgb,
  DriftCurves,
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
  hoverHR,
  hoverPrimaryValue,
  hoverSpeed,
} from './fit-timeseries-chart-tooltip';

export interface RenderCanvases {
  primary?: HTMLCanvasElement | null;
  speed?: HTMLCanvasElement | null;
  hr?: HTMLCanvasElement | null;
  cad?: HTMLCanvasElement | null;
  drift?: HTMLCanvasElement | null;
  elev?: HTMLCanvasElement | null;
  xAxis?: HTMLCanvasElement | null;
}

export interface RenderInput {
  records: FitRecord[];
  downsampled: FitRecord[];
  sportType: string;
  ftp: number | null;
  zoneBlocks: ZoneBlock[];
  /** When non-empty, zones not in this set render dimmed. Empty/null = no filter. */
  zoneFilters: Set<string> | null;
  blockSummaries: BlockSummary[];
  blockColors: string[];
  showBlocks: boolean;
  showPrimary: boolean;
  showSpeed: boolean;
  showHR: boolean;
  showCadence: boolean;
  showDrift: boolean;
  hasElevation: boolean;
  driftCurves: DriftCurves | null;
  hoverIdx: number | null;
  theme: ThemeColors;
  /** Visible time window in elapsed seconds from session start. null = full range. */
  viewStartSec: number | null;
  viewEndSec: number | null;
}

export interface RenderResult {
  primaryMin: number;
  primaryMax: number;
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

/** Resolve the visible [startSec, endSec] window, defaulting to the full session. */
function viewWindow(input: RenderInput): { startSec: number; endSec: number } {
  const records = input.records;
  const t0 = records[0].timestamp;
  const full = records[records.length - 1].timestamp - t0 || records.length;
  return {
    startSec: input.viewStartSec ?? 0,
    endSec: input.viewEndSec ?? full,
  };
}

function initCanvas(
  canvas: HTMLCanvasElement | null | undefined,
  records: FitRecord[],
  view: { startSec: number; endSec: number } | null,
): CanvasFrame | null {
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
  const mT = 6,
    mB = 6;
  const cW = W - mL - mR;
  const t0 = records[0].timestamp;
  const n = records.length;
  const fullSec = records[n - 1].timestamp - t0 || n;
  const vStart = view ? view.startSec : 0;
  const vEnd = view ? view.endSec : fullSec;
  const span = vEnd - vStart || fullSec;
  const xOf = (i: number) => mL + ((records[i].timestamp - t0 - vStart) / span) * cW;
  const xOfT = (sec: number) => mL + ((sec - vStart) / span) * cW;
  return { ctx, W, H, cW, xOf, xOfT, mT, mB, mL, mR };
}

/** Clip subsequent drawing to the plot region so zoomed-out content doesn't
 * bleed over the Y-axis labels in the left margin or past the right edge. */
function clipPlot(s: { ctx: CanvasRenderingContext2D; H: number; cW: number; mL: number }): void {
  s.ctx.save();
  s.ctx.beginPath();
  s.ctx.rect(s.mL, 0, s.cW, s.H);
  s.ctx.clip();
}

/** Draws just the left vertical (Y) axis spine on each sub-canvas. We don't
 * draw the bottom horizontal here: time ticks live in their own xAxis canvas
 * below the stack, so a per-chart bottom line would duplicate it. */
function drawAxisSpine(
  ctx: CanvasRenderingContext2D,
  s: { H: number; mT: number; mB: number; mL: number },
): void {
  const top = s.mT;
  const bottom = s.H - s.mB;
  ctx.save();
  ctx.strokeStyle = 'rgba(255,255,255,0.22)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(s.mL, top);
  ctx.lineTo(s.mL, bottom);
  ctx.stroke();
  ctx.restore();
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
  ctx.lineWidth = 1;
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

interface AreaFill {
  rgb: [number, number, number];
  top: number;
  bottom: number;
}

function fillSteppedArea(
  ctx: CanvasRenderingContext2D,
  pts: Array<{ x1: number; x2: number; y: number }>,
  fill: AreaFill,
): void {
  if (pts.length === 0) return;
  ctx.beginPath();
  ctx.moveTo(pts[0].x1, fill.bottom);
  for (const p of pts) {
    ctx.lineTo(p.x1, p.y);
    ctx.lineTo(p.x2, p.y);
  }
  ctx.lineTo(pts[pts.length - 1].x2, fill.bottom);
  ctx.closePath();
  const g = ctx.createLinearGradient(0, fill.top, 0, fill.bottom);
  const [r, gC, b] = fill.rgb;
  g.addColorStop(0, `rgba(${r},${gC},${b},0.35)`);
  g.addColorStop(1, `rgba(${r},${gC},${b},0.03)`);
  ctx.fillStyle = g;
  ctx.fill();
}

const DIM_RGB: [number, number, number] = [140, 148, 158];

function drawSteppedLine(
  ctx: CanvasRenderingContext2D,
  xOf: (i: number) => number,
  yOf: (v: number) => number,
  blocks: Array<{ s: number; e: number; v: number }>,
  color: string,
  fill?: AreaFill,
): void {
  const pts = blocks.map((b, i) => ({
    x1: xOf(b.s),
    x2: i + 1 < blocks.length ? xOf(blocks[i + 1].s) : xOf(b.e),
    y: yOf(b.v),
  }));
  if (fill) fillSteppedArea(ctx, pts, fill);
  ctx.lineWidth = 1;
  ctx.strokeStyle = color;
  ctx.beginPath();
  pts.forEach((p, i) => {
    if (i === 0) ctx.moveTo(p.x1, p.y);
    else ctx.lineTo(p.x1, p.y);
    ctx.lineTo(p.x2, p.y);
  });
  ctx.stroke();
}

function drawSteppedBlockLine(
  ctx: CanvasRenderingContext2D,
  xOfT: (sec: number) => number,
  yOf: (v: number) => number,
  blocks: Array<{ dur: number; v: number }>,
  color: string,
  fill?: AreaFill,
): void {
  const pts: Array<{ x1: number; x2: number; y: number }> = [];
  let t = 0;
  for (const b of blocks) {
    pts.push({ x1: xOfT(t), x2: xOfT(t + b.dur), y: yOf(b.v) });
    t += b.dur;
  }
  if (fill) fillSteppedArea(ctx, pts, fill);
  ctx.strokeStyle = color;
  ctx.lineWidth = 1;
  ctx.beginPath();
  pts.forEach((p, i) => {
    if (i === 0) ctx.moveTo(p.x1, p.y);
    else ctx.lineTo(p.x1, p.y);
    ctx.lineTo(p.x2, p.y);
  });
  ctx.stroke();
}

function fillPolyline(
  ctx: CanvasRenderingContext2D,
  points: Array<{ x: number; y: number }>,
  fill: AreaFill,
): void {
  if (points.length < 2) return;
  ctx.beginPath();
  ctx.moveTo(points[0].x, fill.bottom);
  for (const p of points) ctx.lineTo(p.x, p.y);
  ctx.lineTo(points[points.length - 1].x, fill.bottom);
  ctx.closePath();
  const g = ctx.createLinearGradient(0, fill.top, 0, fill.bottom);
  const [r, gC, b] = fill.rgb;
  g.addColorStop(0, `rgba(${r},${gC},${b},0.35)`);
  g.addColorStop(1, `rgba(${r},${gC},${b},0.03)`);
  ctx.fillStyle = g;
  ctx.fill();
}

const isCycling = (sportType: string) => sportType === 'CYCLING';
const isSwimming = (sportType: string) => sportType === 'SWIMMING';

const useZoneBlocks = (input: RenderInput): boolean =>
  input.showBlocks && input.zoneBlocks.length > 0;
const usePlannedBlocks = (input: RenderInput): boolean =>
  input.showBlocks && !useZoneBlocks(input) && input.blockSummaries.length > 0;

function buildHoverContext(input: RenderInput, primaryMax: number): HoverContext | null {
  if (input.hoverIdx === null) return null;
  return {
    records: input.records,
    downsampled: input.downsampled,
    sportType: input.sportType,
    zoneBlocks: input.zoneBlocks,
    blockSummaries: input.blockSummaries,
    showBlocks: input.showBlocks,
    primaryMax,
    showPrimary: input.showPrimary,
    showHR: input.showHR,
    showCadence: input.showCadence,
    hasElevation: input.hasElevation,
    showDrift: input.showDrift,
    driftCurves: input.driftCurves,
    accentHex: input.theme.accentHex,
    hoverIdx: input.hoverIdx,
  };
}

function drawPrimary(
  canvas: HTMLCanvasElement | null | undefined,
  input: RenderInput,
): { min: number; max: number } {
  const s = initCanvas(canvas, input.records, viewWindow(input));
  if (!s) return { min: 0, max: 0 };
  const { ctx, W, H, xOf, xOfT, mT, mB, mL, mR } = s;
  const accent = input.theme.accentHex;
  const records = input.records;
  const t0 = records[0].timestamp;
  const chartH = H - mT - mB;
  const top = mT,
    bottom = mT + chartH;
  const cycling = isCycling(input.sportType);
  const swimming = isSwimming(input.sportType);

  let maxP = 1,
    maxS = 1;
  let minPace = 0,
    maxPace = 1;
  let primaryMin = 0,
    primaryMax = 0;
  let yOf: (v: number) => number;
  if (cycling) {
    maxP = Math.max(input.ftp ? input.ftp * 1.5 : 0, ...records.map((r) => r.power)) || 1;
    yOf = (v) => top + chartH * (1 - v / maxP);
    primaryMax = maxP;
  } else if (swimming) {
    const paces = records
      .map((r) => (r.speed || 0) * 3.6)
      .filter((v) => v > 0.5)
      .map((v) => 360 / v);
    if (paces.length === 0) {
      minPace = 60;
      maxPace = 180;
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
    const sp = records.map((r) => (r.speed || 0) * 3.6);
    maxS = Math.max(...sp.filter((v) => v > 0), 1);
    yOf = (v) => top + chartH * (1 - v / maxS);
    primaryMax = maxS;
  }

  const plotValue = (speedKmh: number) => speedToPlotValue(speedKmh, swimming, primaryMax);

  clipPlot(s);

  if (cycling && input.ftp) {
    const fy = yOf(input.ftp);
    ctx.save();
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = input.theme.gridAlpha15;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(mL, fy);
    ctx.lineTo(W - mR, fy);
    ctx.stroke();
    ctx.restore();
    ctx.fillStyle = input.theme.textAlpha30;
    ctx.font = '9px monospace';
    ctx.textAlign = 'left';
    ctx.fillText('FTP', mL + 2, fy - 3);
  }

  if (useZoneBlocks(input)) {
    const zBlocks = input.zoneBlocks;
    const filter = input.zoneFilters;
    const hasFilter = !!filter && filter.size > 0;
    for (let bi = 0; bi < zBlocks.length; bi++) {
      const b = zBlocks[bi];
      const x1 = xOf(b.startIndex);
      const x2 = bi + 1 < zBlocks.length ? xOf(zBlocks[bi + 1].startIndex) : xOf(b.endIndex);
      const val = cycling ? b.avgPower : plotValue(b.avgSpeed);
      const y = yOf(val);
      const active = !hasFilter || filter!.has(b.zoneLabel);
      const [br, bg, bb] = active ? cssToRgb(b.color) : DIM_RGB;
      const fillAlpha = active ? 0.25 : 0.08;
      const strokeAlpha = active ? 1 : 0.45;
      ctx.fillStyle = `rgba(${br},${bg},${bb},${fillAlpha})`;
      ctx.fillRect(x1, y, x2 - x1, bottom - y);
      ctx.strokeStyle = `rgba(${br},${bg},${bb},${strokeAlpha})`;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(x1, y);
      ctx.lineTo(x2, y);
      ctx.stroke();
    }
  } else if (usePlannedBlocks(input)) {
    interface PlannedBlockGeom {
      x1: number;
      x2: number;
      y: number;
      targetY: number | null;
      color: string;
      rgb: [number, number, number];
    }
    const blocks: PlannedBlockGeom[] = [];
    let acc = 0;
    for (let bi = 0; bi < input.blockSummaries.length; bi++) {
      const b = input.blockSummaries[bi];
      const x1 = xOfT(acc);
      const x2 = xOfT(acc + b.durationSeconds);
      const speedKmh =
        b.distanceMeters && b.durationSeconds > 0
          ? (b.distanceMeters / b.durationSeconds) * 3.6
          : 0;
      const val = cycling ? b.actualPower : plotValue(speedKmh);
      const y = yOf(val);
      const targetY = b.targetPower > 0 ? yOf(b.targetPower) : null;
      const rgb = cssToRgb(input.blockColors[bi] || accent);
      blocks.push({
        x1,
        x2,
        y,
        targetY,
        color: `rgb(${rgb[0]},${rgb[1]},${rgb[2]})`,
        rgb,
      });
      acc += b.durationSeconds;
    }

    for (const blk of blocks) {
      if (blk.targetY === null) continue;
      ctx.save();
      ctx.setLineDash([3, 3]);
      ctx.strokeStyle = input.theme.gridAlpha15;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(blk.x1, blk.targetY);
      ctx.lineTo(blk.x2, blk.targetY);
      ctx.stroke();
      ctx.restore();
    }

    for (const blk of blocks) {
      const [r, g, b] = blk.rgb;
      ctx.fillStyle = `rgba(${r},${g},${b},0.25)`;
      ctx.fillRect(blk.x1, blk.y, blk.x2 - blk.x1, bottom - blk.y);
    }

    ctx.lineWidth = 1;
    for (let i = 0; i < blocks.length; i++) {
      const blk = blocks[i];
      ctx.strokeStyle = blk.color;
      ctx.beginPath();
      ctx.moveTo(blk.x1, blk.y);
      ctx.lineTo(blk.x2, blk.y);
      if (i < blocks.length - 1) ctx.lineTo(blk.x2, blocks[i + 1].y);
      ctx.stroke();
    }
  } else {
    const ds = input.downsampled;
    const dsX = (i: number) => xOfT(ds[i].timestamp - t0);
    if (cycling) {
      const vals = ds.map((r) => r.power);
      if (vals.length > 1) {
        ctx.beginPath();
        ctx.moveTo(dsX(0), bottom);
        vals.forEach((p, i) => ctx.lineTo(dsX(i), yOf(p)));
        ctx.lineTo(dsX(ds.length - 1), bottom);
        ctx.closePath();
        const g = ctx.createLinearGradient(0, top, 0, bottom);
        g.addColorStop(0, accentAlphaFromRgb(input.theme.accentRgb, 0.5));
        g.addColorStop(1, accentAlphaFromRgb(input.theme.accentRgb, 0.03));
        ctx.fillStyle = g;
        ctx.fill();
        ctx.beginPath();
        ctx.moveTo(dsX(0), yOf(vals[0]));
        vals.forEach((p, i) => ctx.lineTo(dsX(i), yOf(p)));
        ctx.strokeStyle = accent;
        ctx.lineWidth = 1;
        ctx.stroke();
      }
    } else {
      const vals = ds.map((r) => plotValue((r.speed || 0) * 3.6));
      if (vals.length > 1) {
        ctx.beginPath();
        ctx.moveTo(dsX(0), bottom);
        vals.forEach((v, i) => ctx.lineTo(dsX(i), yOf(v)));
        ctx.lineTo(dsX(ds.length - 1), bottom);
        ctx.closePath();
        const g = ctx.createLinearGradient(0, top, 0, bottom);
        g.addColorStop(0, accentAlphaFromRgb(input.theme.accentRgb, 0.5));
        g.addColorStop(1, accentAlphaFromRgb(input.theme.accentRgb, 0.03));
        ctx.fillStyle = g;
        ctx.fill();
        ctx.beginPath();
        ctx.moveTo(dsX(0), yOf(vals[0]));
        vals.forEach((v, i) => ctx.lineTo(dsX(i), yOf(v)));
        ctx.strokeStyle = accent;
        ctx.lineWidth = 1;
        ctx.stroke();
      }
    }
  }

  drawBlockBounds(ctx, input, xOfT, top, bottom);

  if (input.hoverIdx !== null) {
    const hx = xOf(input.hoverIdx);
    drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
    const hCtx = buildHoverContext(input, primaryMax);
    if (hCtx) {
      const val = hoverPrimaryValue(hCtx, input.hoverIdx, t0);
      drawDot(ctx, input.theme, hx, yOf(val), accent);
    }
  }

  ctx.restore();

  ctx.fillStyle = input.theme.textAlpha40;
  ctx.font = '9px monospace';
  ctx.textAlign = 'right';
  if (cycling) {
    [0, 0.5, 1].forEach((f) => {
      const p = Math.round(maxP * f);
      ctx.fillText(String(p), mL - 4, yOf(p) + 4);
    });
  } else if (swimming) {
    [0, 0.5, 1].forEach((f) => {
      const pace = minPace + (maxPace - minPace) * f;
      const m = Math.floor(pace / 60);
      const sec = Math.round(pace % 60);
      const valStr = `${m}:${String(sec).padStart(2, '0')}`;
      const y = yOf(pace);
      ctx.fillText(valStr, mL - 4, y);
      ctx.fillText('/100', mL - 4, y + 10);
    });
  } else {
    [0, 0.5, 1].forEach((f) => {
      const v = Math.round(maxS * f * 10) / 10;
      const pace = v > 0.5 ? 3600 / v : NaN;
      const valStr = isNaN(pace)
        ? '—'
        : `${Math.floor(pace / 60)}:${String(Math.round(pace % 60)).padStart(2, '0')}`;
      const y = yOf(v);
      ctx.fillText(valStr, mL - 4, y);
      ctx.fillText('/km', mL - 4, y + 10);
    });
  }

  drawAxisSpine(ctx, s);

  return { min: primaryMin, max: primaryMax };
}

function drawSpeed(
  canvas: HTMLCanvasElement | null | undefined,
  input: RenderInput,
  hoverCtx: HoverContext | null,
): void {
  const s = initCanvas(canvas, input.records, viewWindow(input));
  if (!s) return;
  const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
  const records = input.records;
  const t0 = records[0].timestamp;
  const chartH = H - mT - mB;
  const top = mT,
    bottom = mT + chartH;
  const color = '#22d3ee';
  const fillRgb: [number, number, number] = [34, 211, 238];
  const fill: AreaFill = { rgb: fillRgb, top, bottom };

  const sp = records.map((r) => (r.speed || 0) * 3.6);
  const maxS = Math.max(...sp.filter((v) => v > 0), 1);
  const yOf = (v: number) => top + chartH * (1 - v / maxS);

  clipPlot(s);

  if (useZoneBlocks(input)) {
    drawSteppedLine(
      ctx,
      xOf,
      yOf,
      input.zoneBlocks.map((b) => ({
        s: b.startIndex,
        e: b.endIndex,
        v: b.avgSpeed,
      })),
      color,
      fill,
    );
  } else if (usePlannedBlocks(input)) {
    drawSteppedBlockLine(
      ctx,
      xOfT,
      yOf,
      input.blockSummaries.map((b) => ({
        dur: b.durationSeconds,
        v:
          b.distanceMeters && b.durationSeconds > 0
            ? (b.distanceMeters / b.durationSeconds) * 3.6
            : 0,
      })),
      color,
      fill,
    );
  } else {
    const ds = input.downsampled;
    const pts: Array<{ x: number; y: number }> = [];
    ds.forEach((r) => {
      const v = (r.speed || 0) * 3.6;
      pts.push({ x: xOfT(r.timestamp - t0), y: yOf(v) });
    });
    if (pts.length >= 2) {
      fillPolyline(ctx, pts, fill);
      ctx.beginPath();
      pts.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
      ctx.strokeStyle = color;
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }

  drawBlockBounds(ctx, input, xOfT, top, bottom);

  if (input.hoverIdx !== null && hoverCtx) {
    const hx = xOf(input.hoverIdx);
    drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
    const v = hoverSpeed(hoverCtx, input.hoverIdx, t0);
    drawDot(ctx, input.theme, hx, yOf(v), color);
  }

  ctx.restore();

  ctx.fillStyle = color;
  ctx.font = '9px monospace';
  ctx.textAlign = 'right';
  [maxS, maxS / 2, 0].forEach((v) =>
    ctx.fillText(`${v.toFixed(v < 10 ? 1 : 0)}`, mL - 4, yOf(v) + 4),
  );

  drawAxisSpine(ctx, s);
}

function drawHR(
  canvas: HTMLCanvasElement | null | undefined,
  input: RenderInput,
  hoverCtx: HoverContext | null,
): void {
  const s = initCanvas(canvas, input.records, viewWindow(input));
  if (!s) return;
  const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
  const records = input.records;
  const t0 = records[0].timestamp;
  const chartH = H - mT - mB;
  const top = mT,
    bottom = mT + chartH;
  const color = '#e74c3c';
  const fillRgb: [number, number, number] = [231, 76, 60];
  const fill: AreaFill = { rgb: fillRgb, top, bottom };

  const hrs = records.map((r) => r.heartRate).filter((v) => v > 0);
  const minHR = 100;
  const maxHR = hrs.length ? Math.max(Math.max(...hrs) * 1.05, minHR + 20) : 220;
  const yOf = (hr: number) => top + chartH * (1 - (Math.max(hr, minHR) - minHR) / (maxHR - minHR));

  clipPlot(s);

  if (useZoneBlocks(input)) {
    drawSteppedLine(
      ctx,
      xOf,
      yOf,
      input.zoneBlocks.map((b) => ({
        s: b.startIndex,
        e: b.endIndex,
        v: b.avgHR,
      })),
      color,
      fill,
    );
  } else if (usePlannedBlocks(input)) {
    drawSteppedBlockLine(
      ctx,
      xOfT,
      yOf,
      input.blockSummaries.map((b) => ({
        dur: b.durationSeconds,
        v: b.actualHR,
      })),
      color,
      fill,
    );
  } else {
    const ds = input.downsampled;
    const pts: Array<{ x: number; y: number }> = [];
    ds.forEach((r) => {
      if (!r.heartRate) return;
      pts.push({ x: xOfT(r.timestamp - t0), y: yOf(r.heartRate) });
    });
    fillPolyline(ctx, pts, fill);
    ctx.beginPath();
    pts.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    ctx.stroke();
  }

  drawBlockBounds(ctx, input, xOfT, top, bottom);

  if (input.hoverIdx !== null && hoverCtx) {
    const hx = xOf(input.hoverIdx);
    drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
    const hr = hoverHR(hoverCtx, input.hoverIdx, t0);
    if (hr) drawDot(ctx, input.theme, hx, yOf(hr), color);
  }

  ctx.restore();

  ctx.fillStyle = color;
  ctx.font = '9px monospace';
  ctx.textAlign = 'right';
  const mid = Math.round((minHR + maxHR) / 2);
  [Math.round(maxHR), mid, minHR].forEach((v) => ctx.fillText(String(v), mL - 4, yOf(v) + 4));

  drawAxisSpine(ctx, s);
}

function drawCadence(
  canvas: HTMLCanvasElement | null | undefined,
  input: RenderInput,
  hoverCtx: HoverContext | null,
): void {
  const s = initCanvas(canvas, input.records, viewWindow(input));
  if (!s) return;
  const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
  const records = input.records;
  const t0 = records[0].timestamp;
  const chartH = H - mT - mB;
  const top = mT,
    bottom = mT + chartH;
  const color = '#3b82f6';
  const fillRgb: [number, number, number] = [59, 130, 246];
  const fill: AreaFill = { rgb: fillRgb, top, bottom };

  const cads = records.map((r) => getCadFromRecord(r, input.sportType)).filter((v) => v > 0);
  const minCad = input.sportType === 'RUNNING' ? 140 : 40;
  const maxCad = cads.length ? Math.max(Math.max(...cads) * 1.05, minCad + 20) : 120;
  const yOf = (c: number) =>
    top + chartH * (1 - (Math.max(c, minCad) - minCad) / (maxCad - minCad));

  clipPlot(s);

  if (useZoneBlocks(input)) {
    drawSteppedLine(
      ctx,
      xOf,
      yOf,
      input.zoneBlocks.map((b) => ({
        s: b.startIndex,
        e: b.endIndex,
        v: getCadBlockFromValue(b.avgCadence, input.sportType),
      })),
      color,
      fill,
    );
  } else if (usePlannedBlocks(input)) {
    drawSteppedBlockLine(
      ctx,
      xOfT,
      yOf,
      input.blockSummaries.map((b) => ({
        dur: b.durationSeconds,
        v: getCadBlockFromValue(b.actualCadence, input.sportType),
      })),
      color,
      fill,
    );
  } else {
    const ds = input.downsampled;
    const pts: Array<{ x: number; y: number }> = [];
    ds.forEach((r) => {
      if (!r.cadence) return;
      const c = getCadFromRecord(r, input.sportType);
      pts.push({ x: xOfT(r.timestamp - t0), y: yOf(c) });
    });
    fillPolyline(ctx, pts, fill);
    ctx.beginPath();
    pts.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    ctx.stroke();
  }

  drawBlockBounds(ctx, input, xOfT, top, bottom);

  if (input.hoverIdx !== null && hoverCtx) {
    const hx = xOf(input.hoverIdx);
    drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
    const c = hoverCadence(hoverCtx, input.hoverIdx, t0);
    if (c) drawDot(ctx, input.theme, hx, yOf(c), color);
  }

  ctx.restore();

  ctx.fillStyle = color;
  ctx.font = '9px monospace';
  ctx.textAlign = 'right';
  const mid = Math.round((minCad + maxCad) / 2);
  [Math.round(maxCad), mid, minCad].forEach((v) => ctx.fillText(String(v), mL - 4, yOf(v) + 4));

  drawAxisSpine(ctx, s);
}

function drawDrift(canvas: HTMLCanvasElement | null | undefined, input: RenderInput): void {
  const s = initCanvas(canvas, input.records, viewWindow(input));
  if (!s || !input.driftCurves) return;
  const { ctx, W, H, xOf, xOfT, mT, mB, mL, mR } = s;
  const records = input.records;
  const t0 = records[0].timestamp;
  const chartH = H - mT - mB;
  const top = mT;
  const bottom = mT + chartH;
  const hrColor = '#e74c3c';
  const outColor = input.theme.accentHex;

  const { hrNormPct, outNormPct, usePower, baselineStartSec, baselineEndSec } = input.driftCurves;

  clipPlot(s);

  // Faint shaded band marking the baseline window so the user can see what
  // "100%" is calibrated against. Drawn first so everything else paints over it.
  const bandX1 = xOfT(baselineStartSec);
  const bandX2 = xOfT(baselineEndSec);
  ctx.fillStyle = 'rgba(255,255,255,0.04)';
  ctx.fillRect(bandX1, top, bandX2 - bandX1, chartH);
  ctx.fillStyle = input.theme.textAlpha30;
  ctx.font = '9px monospace';
  ctx.textAlign = 'center';
  ctx.fillText('baseline', (bandX1 + bandX2) / 2, top + chartH - 3);

  // Auto-fit y range across both curves, padded; always include 100% baseline
  // so the reference line never sits at the chart edge.
  let lo = 100,
    hi = 100;
  for (let i = 0; i < hrNormPct.length; i++) {
    const a = hrNormPct[i],
      b = outNormPct[i];
    if (Number.isFinite(a)) {
      if (a < lo) lo = a;
      if (a > hi) hi = a;
    }
    if (Number.isFinite(b)) {
      if (b < lo) lo = b;
      if (b > hi) hi = b;
    }
  }
  const pad = Math.max((hi - lo) * 0.08, 1.5);
  lo = Math.min(100, lo - pad);
  hi = Math.max(100, hi + pad);
  const range = hi - lo || 1;
  const yOf = (v: number) => top + chartH * (1 - (v - lo) / range);

  // 100% baseline reference.
  const yBase = yOf(100);
  ctx.save();
  ctx.setLineDash([4, 4]);
  ctx.strokeStyle = input.theme.gridAlpha15;
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(mL, yBase);
  ctx.lineTo(W - mR, yBase);
  ctx.stroke();
  ctx.restore();
  ctx.fillStyle = input.theme.textAlpha30;
  ctx.font = '9px monospace';
  ctx.textAlign = 'left';
  ctx.fillText('100%', mL + 2, yBase - 3);

  // Sample the curves through the downsampled timeline so we draw a smooth
  // ~30s-bucket polyline rather than every record (matches HR/cad panels).
  const ds = input.downsampled;
  const hrPts: Array<{ x: number; y: number; v: number } | null> = [];
  const outPts: Array<{ x: number; y: number; v: number } | null> = [];
  let lastIdx = 0;
  for (const r of ds) {
    while (lastIdx < records.length - 1 && records[lastIdx + 1].timestamp <= r.timestamp) {
      lastIdx++;
    }
    const x = xOfT(r.timestamp - t0);
    const a = hrNormPct[lastIdx];
    const b = outNormPct[lastIdx];
    hrPts.push(Number.isFinite(a) ? { x, y: yOf(a), v: a } : null);
    outPts.push(Number.isFinite(b) ? { x, y: yOf(b), v: b } : null);
  }

  // Gap fill between the two curves — red tint when HR > output (drift), green
  // tint when output > HR (rare; warmup or pacing improvement).
  const driftRgb: [number, number, number] = [231, 76, 60];
  const goodRgb: [number, number, number] = [76, 175, 80];
  for (let i = 0; i < hrPts.length - 1; i++) {
    const a1 = hrPts[i],
      a2 = hrPts[i + 1];
    const b1 = outPts[i],
      b2 = outPts[i + 1];
    if (!a1 || !a2 || !b1 || !b2) continue;
    const drifting = (a1.v + a2.v) / 2 > (b1.v + b2.v) / 2;
    const [r, g, b] = drifting ? driftRgb : goodRgb;
    ctx.fillStyle = `rgba(${r},${g},${b},0.18)`;
    ctx.beginPath();
    ctx.moveTo(a1.x, a1.y);
    ctx.lineTo(a2.x, a2.y);
    ctx.lineTo(b2.x, b2.y);
    ctx.lineTo(b1.x, b1.y);
    ctx.closePath();
    ctx.fill();
  }

  // Output curve (under HR for layer order).
  ctx.strokeStyle = outColor;
  ctx.lineWidth = 1;
  ctx.beginPath();
  let started = false;
  for (const p of outPts) {
    if (!p) {
      started = false;
      continue;
    }
    if (!started) {
      ctx.moveTo(p.x, p.y);
      started = true;
    } else ctx.lineTo(p.x, p.y);
  }
  ctx.stroke();

  // HR curve on top.
  ctx.strokeStyle = hrColor;
  ctx.lineWidth = 1;
  ctx.beginPath();
  started = false;
  for (const p of hrPts) {
    if (!p) {
      started = false;
      continue;
    }
    if (!started) {
      ctx.moveTo(p.x, p.y);
      started = true;
    } else ctx.lineTo(p.x, p.y);
  }
  ctx.stroke();

  drawBlockBounds(ctx, input, xOfT, top, bottom);

  if (input.hoverIdx !== null) {
    const hx = xOf(input.hoverIdx);
    drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
    const a = hrNormPct[input.hoverIdx];
    const b = outNormPct[input.hoverIdx];
    if (Number.isFinite(b)) drawDot(ctx, input.theme, hx, yOf(b), outColor);
    if (Number.isFinite(a)) drawDot(ctx, input.theme, hx, yOf(a), hrColor);
  }

  ctx.restore();

  // Y-axis labels: low / 100 / high.
  ctx.fillStyle = input.theme.textAlpha40;
  ctx.font = '9px monospace';
  ctx.textAlign = 'right';
  [hi, 100, lo].forEach((v) => ctx.fillText(`${Math.round(v)}%`, mL - 4, yOf(v) + 4));

  // Compact legend (top-right) so the panel is self-explanatory without a header.
  const legendY = top + 8;
  ctx.textAlign = 'right';
  const outLabel = usePower ? 'Pwr%' : 'Spd%';
  ctx.fillStyle = hrColor;
  ctx.fillText('HR%', W - mR - 4, legendY);
  ctx.fillStyle = outColor;
  ctx.fillText(outLabel, W - mR - 4, legendY + 10);

  drawAxisSpine(ctx, s);
}

function drawElevation(canvas: HTMLCanvasElement | null | undefined, input: RenderInput): void {
  const s = initCanvas(canvas, input.records, viewWindow(input));
  if (!s) return;
  const { ctx, H, xOf, xOfT, mT, mB, mL } = s;
  const chartH = H - mT - mB;
  const top = mT,
    bottom = mT + chartH;
  const records = input.records;

  const ds = input.downsampled;
  const t0 = records[0].timestamp;
  const elevs = records.filter((r) => r.elevation != null).map((r) => r.elevation!);
  if (elevs.length < 2) return;
  const minE = Math.min(...elevs) - 5;
  const maxE = Math.max(...elevs) + 5;
  const range = maxE - minE || 1;
  const yOf = (e: number) => top + chartH * (1 - (e - minE) / range);

  clipPlot(s);

  ctx.beginPath();
  let started = false;
  let lastX = mL;
  ds.forEach((r) => {
    if (r.elevation == null) return;
    const x = xOfT(r.timestamp - t0);
    if (!started) {
      ctx.moveTo(x, bottom);
      ctx.lineTo(x, yOf(r.elevation));
      started = true;
    } else ctx.lineTo(x, yOf(r.elevation));
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
    if (first) {
      ctx.moveTo(x, yOf(r.elevation));
      first = false;
    } else ctx.lineTo(x, yOf(r.elevation));
  });
  ctx.strokeStyle = 'rgba(76,175,80,0.6)';
  ctx.lineWidth = 1;
  ctx.stroke();

  if (input.hoverIdx !== null) {
    const hx = xOf(input.hoverIdx);
    drawCrosshair(ctx, input.theme, input.hoverIdx, hx, top, bottom);
    const e = records[input.hoverIdx].elevation;
    if (e != null) drawDot(ctx, input.theme, hx, yOf(e), '#4caf50');
  }

  ctx.restore();

  ctx.fillStyle = 'rgba(76,175,80,0.6)';
  ctx.font = '9px monospace';
  ctx.textAlign = 'right';
  const mid = Math.round((minE + maxE) / 2);
  [Math.round(maxE), mid, Math.round(minE)].forEach((v) =>
    ctx.fillText(v + 'm', mL - 4, yOf(v) + 4),
  );

  drawAxisSpine(ctx, s);
}

/** Axis tick label: minutes (e.g. "12m") when on a whole minute, "m:ss" or
 * seconds for the finer ticks that appear when zoomed in. */
function fmtAxisLabel(sec: number): string {
  const r = Math.round(sec);
  if (r < 60) return `${r}s`;
  const m = Math.floor(r / 60);
  const s = r % 60;
  return s === 0 ? `${m}m` : `${m}:${String(s).padStart(2, '0')}`;
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
  const fullSec = records[n - 1].timestamp - t0 || n;
  const cW = W - mL - mR;
  const view = viewWindow(input);
  const vStart = view.startSec;
  const vEnd = view.endSec;
  const span = vEnd - vStart || fullSec;
  const xOfT = (sec: number) => mL + ((sec - vStart) / span) * cW;

  const tick = pickTickInterval(span);
  ctx.fillStyle = input.theme.textAlpha40;
  ctx.font = '9px monospace';
  ctx.textAlign = 'center';
  const firstTick = Math.ceil(vStart / tick) * tick;
  for (let sec = firstTick; sec <= vEnd + 0.5; sec += tick) {
    ctx.fillText(fmtAxisLabel(sec), xOfT(sec), 14);
  }

  if (input.hoverIdx !== null) {
    const hx = xOfT(records[input.hoverIdx].timestamp - t0);
    ctx.save();
    ctx.strokeStyle = input.theme.crosshairAlpha;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(hx, 0);
    ctx.lineTo(hx, 4);
    ctx.stroke();
    ctx.restore();
  }
}

export function drawAll(canvases: RenderCanvases, input: RenderInput): RenderResult {
  const primary = input.showPrimary ? drawPrimary(canvases.primary, input) : { min: 0, max: 0 };
  const hoverCtx = buildHoverContext(input, primary.max);
  if (input.showSpeed) drawSpeed(canvases.speed, input, hoverCtx);
  drawHR(canvases.hr, input, hoverCtx);
  drawCadence(canvases.cad, input, hoverCtx);
  if (input.showDrift) drawDrift(canvases.drift, input);
  drawElevation(canvases.elev, input);
  drawXAxis(canvases.xAxis, input);
  return {
    primaryMin: primary.min,
    primaryMax: primary.max,
  };
}

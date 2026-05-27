import { PmcDataPoint } from '../../../services/metrics.service';
import { PRIORITY_COLORS, RaceGoal } from '../../../services/race-goal.service';
import { addDaysToDate, dateToDays, getSportColor, roundRect } from './pmc-chart.utils';

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

const CTL_COLOR = '#f5a623'; // Fitness — warm gold
const ATL_COLOR = '#e74c3c'; // Fatigue — red
const TSB_COLOR = '#3b82f6'; // Form — blue
const TSB_NEG_COLOR = '#f87171';

// Axis/label ink adapts to the active theme so gridlines and scale numbers stay
// legible in light mode (where pure-white text vanished against the pale canvas).
let isLightTheme = false;
function ink(alpha: number): string {
  return isLightTheme ? `rgba(0,0,0,${alpha})` : `rgba(255,255,255,${alpha})`;
}

// Axis scale numbers and tick lines should read more strongly than the rest of the
// chrome — brighter in dark mode, darker in light mode — so the scales are easy to
// follow. axisInk boosts the requested alpha (capped at 1) for that extra contrast.
function axisInk(alpha: number): string {
  return ink(Math.min(1, alpha + 0.35));
}

interface VisibleRange {
  start: number;
  end: number;
}

interface Pt {
  x: number;
  y: number;
}

/** Geometry of the three stacked panels, sharing one horizontal axis. */
interface Layout {
  tsbTop: number;
  tsbBot: number;
  loadTop: number;
  loadBot: number;
  tssTop: number;
  tssBot: number;
}

function visibleIndices(
  points: PmcDataPoint[],
  viewStartDate: string,
  viewEndDate: string,
): VisibleRange | null {
  if (points.length < 2 || !viewStartDate) return null;
  let s = 0;
  while (s < points.length && points[s].date < viewStartDate) s++;
  if (s >= points.length) return null;
  let e = points.length - 1;
  while (e > s && points[e].date > viewEndDate) e--;
  return s <= e ? { start: s, end: e } : null;
}

/** Traces a Catmull-Rom smoothed path through every point (passes through each day). */
function buildSmoothPath(ctx: CanvasRenderingContext2D, pts: Pt[]): void {
  if (pts.length === 0) return;
  ctx.moveTo(pts[0].x, pts[0].y);
  if (pts.length < 3) {
    for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i].x, pts[i].y);
    return;
  }
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] ?? p2;
    const cp1x = p1.x + (p2.x - p0.x) / 6;
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    // Clamp control-point Y within the segment's endpoints. Plain Catmull-Rom
    // overshoots on sharp drops, which rendered ATL/CTL dipping below their true
    // (always non-negative) values — visually impossible. Clamping removes the
    // overshoot while keeping the curve smooth between points.
    const loY = Math.min(p1.y, p2.y);
    const hiY = Math.max(p1.y, p2.y);
    const cp1y = Math.max(loY, Math.min(hiY, p1.y + (p2.y - p0.y) / 6));
    const cp2y = Math.max(loY, Math.min(hiY, p2.y - (p3.y - p1.y) / 6));
    ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
  }
}

/** Splits a visible series into the historical (solid) and projected (dashed) point runs. */
function seriesPoints(
  points: PmcDataPoint[],
  vis: VisibleRange,
  predStart: number,
  xOf: (date: string) => number,
  getY: (p: PmcDataPoint) => number,
): { real: Pt[]; pred: Pt[] } {
  const real: Pt[] = [];
  const pred: Pt[] = [];
  for (let i = vis.start; i <= vis.end; i++) {
    const p = points[i];
    const pt = { x: xOf(p.date), y: getY(p) };
    if (p.predicted) pred.push(pt);
    else real.push(pt);
  }
  // Bridge the gap so the dashed projection starts at the last real point.
  if (pred.length && predStart > 0) {
    const bridge = predStart - 1;
    if (bridge >= vis.start && bridge <= vis.end) {
      pred.unshift({ x: xOf(points[bridge].date), y: getY(points[bridge]) });
    }
  }
  return { real, pred };
}

function strokeSeries(
  ctx: CanvasRenderingContext2D,
  real: Pt[],
  pred: Pt[],
  color: string,
  lineW: number,
): void {
  if (real.length) {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = lineW;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.setLineDash([]);
    ctx.beginPath();
    buildSmoothPath(ctx, real);
    ctx.stroke();
    ctx.restore();
  }
  if (pred.length >= 2) {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = lineW;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.globalAlpha = 0.7;
    ctx.setLineDash([3, 4]);
    ctx.beginPath();
    buildSmoothPath(ctx, pred);
    ctx.stroke();
    ctx.restore();
  }
}

export function drawPmcChart(canvas: HTMLCanvasElement, input: PmcRenderInput): void {
  const W = (canvas.width = canvas.offsetWidth || 700);
  const H = (canvas.height = canvas.offsetHeight || 420);
  const ctx = canvas.getContext('2d')!;
  ctx.clearRect(0, 0, W, H);

  isLightTheme = document.documentElement.getAttribute('data-theme') === 'light';

  const points = input.data ?? [];
  const mL = input.marginLeft,
    mR = input.marginRight,
    mT = 56,
    mB = 34;
  const cW = W - mL - mR;
  const cH = H - mT - mB;

  if (!input.viewStartDate || !input.viewEndDate || cH < 80) {
    ctx.globalAlpha = 0.3;
    ctx.fillStyle = '#fff';
    ctx.font = FONT;
    ctx.textAlign = 'center';
    ctx.fillText('No PMC data for this period', W / 2, H / 2);
    ctx.globalAlpha = 1;
    return;
  }

  // ── Panel geometry: TSB (form) headlines, LOAD + TSS ride below ──
  const gap = 26;
  const tsbH = Math.round(cH * 0.5);
  const loadH = Math.round(cH * 0.28);
  const layout: Layout = {
    tsbTop: mT,
    tsbBot: mT + tsbH,
    loadTop: mT + tsbH + gap,
    loadBot: mT + tsbH + gap + loadH,
    tssTop: mT + tsbH + gap + loadH + gap,
    tssBot: H - mB,
  };

  const viewStartDays = dateToDays(input.viewStartDate);
  const viewSpan = dateToDays(input.viewEndDate) - viewStartDays;
  if (viewSpan <= 0) return;
  const xOf = (date: string) => mL + ((dateToDays(date) - viewStartDays) / viewSpan) * cW;

  const today = new Date().toISOString().split('T')[0];
  const vis = visibleIndices(points, input.viewStartDate, input.viewEndDate);

  let maxLoad = 100,
    tsbMax = 30,
    maxTss = 100;
  let visSlice: PmcDataPoint[] = [];
  if (vis) {
    visSlice = points.slice(vis.start, vis.end + 1);
    maxLoad = Math.max(...visSlice.map((p) => p.ctl), ...visSlice.map((p) => p.atl), 1) * 1.12;
    tsbMax = Math.max(...visSlice.map((p) => Math.abs(p.tsb)), 1) * 1.2;
    maxTss = Math.max(...visSlice.map((p) => p.dailyTss), 1) * 1.05;
  }

  const yTsb = (v: number) =>
    layout.tsbTop + (layout.tsbBot - layout.tsbTop) * (1 - (v + tsbMax) / (2 * tsbMax));
  const yLoad = (v: number) => layout.loadBot - (layout.loadBot - layout.loadTop) * (v / maxLoad);
  const zeroY = yTsb(0);

  const predStart = points.findIndex((p) => p.predicted);

  drawTsbPanel(ctx, points, vis, predStart, visSlice, xOf, yTsb, zeroY, tsbMax, mL, mR, W, layout);
  drawLoadPanel(ctx, points, vis, predStart, xOf, yLoad, mL, mR, W, maxLoad, layout);
  drawTssPanel(ctx, points, vis, xOf, maxTss, mL, mR, W, layout);

  drawAxisLines(ctx, mL, W, mR, mT, H, mB, layout);
  drawXAxisTicks(ctx, input.viewStartDate, viewSpan, xOf, mT, H, mB);
  drawTodayMarker(ctx, today, input.viewStartDate, input.viewEndDate, xOf, mT, H, mB);
  drawGoals(ctx, input.goals ?? [], input.viewStartDate, input.viewEndDate, xOf, mT, mB, cH, H);
  drawLegend(ctx, mL);

  if (input.hoverIdx !== null && vis && input.hoverIdx >= vis.start && input.hoverIdx <= vis.end) {
    drawHover(ctx, points[input.hoverIdx], xOf, yLoad, yTsb, mT, mR, mB, W, H, layout);
  }
}

function drawTsbPanel(
  ctx: CanvasRenderingContext2D,
  points: PmcDataPoint[],
  vis: VisibleRange | null,
  predStart: number,
  visSlice: PmcDataPoint[],
  xOf: (date: string) => number,
  yTsb: (v: number) => number,
  zeroY: number,
  tsbMax: number,
  mL: number,
  mR: number,
  W: number,
  layout: Layout,
): void {
  const left = mL,
    right = W - mR;
  const top = layout.tsbTop,
    bot = layout.tsbBot;

  // Zone bands — fresh (blue) above 0, fatigued (red, deepening) below 0.
  const freshGrad = ctx.createLinearGradient(0, top, 0, zeroY);
  freshGrad.addColorStop(0, 'rgba(59,130,246,0.10)');
  freshGrad.addColorStop(1, 'rgba(59,130,246,0.0)');
  ctx.fillStyle = freshGrad;
  ctx.fillRect(left, top, right - left, zeroY - top);

  const fatGrad = ctx.createLinearGradient(0, zeroY, 0, bot);
  fatGrad.addColorStop(0, 'rgba(239,68,68,0.0)');
  fatGrad.addColorStop(1, 'rgba(239,68,68,0.16)');
  ctx.fillStyle = fatGrad;
  ctx.fillRect(left, zeroY, right - left, bot - zeroY);

  // Zone labels (faint, right-aligned).
  ctx.textAlign = 'right';
  ctx.font = FONT_XS;
  ctx.fillStyle = 'rgba(59,130,246,0.4)';
  ctx.fillText('FRESH', right - 6, top + 14);
  ctx.fillStyle = ink(0.32);
  ctx.fillText('NEUTRAL', right - 6, zeroY - 6);
  ctx.fillStyle = 'rgba(239,68,68,0.4)';
  ctx.fillText('FATIGUED', right - 6, bot - 8);

  if (vis) {
    const { real, pred } = seriesPoints(points, vis, predStart, xOf, (p) => yTsb(p.tsb));
    const allPts = [...real, ...(pred.length ? pred.slice(real.length ? 1 : 0) : [])];

    // Bicolour area between the curve and the zero line.
    if (allPts.length >= 2) {
      ctx.save();
      ctx.beginPath();
      buildSmoothPath(ctx, allPts);
      ctx.lineTo(allPts[allPts.length - 1].x, zeroY);
      ctx.lineTo(allPts[0].x, zeroY);
      ctx.closePath();
      ctx.clip();
      ctx.fillStyle = 'rgba(59,130,246,0.20)';
      ctx.fillRect(left, top, right - left, zeroY - top);
      ctx.fillStyle = 'rgba(239,68,68,0.20)';
      ctx.fillRect(left, zeroY, right - left, bot - zeroY);
      ctx.restore();
    }

    strokeSeries(ctx, real, pred, TSB_COLOR, 2.5);
    drawPeakTsb(ctx, visSlice, xOf, yTsb);
    drawTsbNow(ctx, points, vis, predStart, xOf, yTsb);
  }

  // Zero line.
  ctx.save();
  ctx.setLineDash([4, 4]);
  ctx.strokeStyle = ink(0.22);
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(left, zeroY);
  ctx.lineTo(right, zeroY);
  ctx.stroke();
  ctx.restore();

  // Y-axis scale + panel label. Quartered ticks (±max, ±½max, 0) give the eye
  // more reference lines than the previous three-value scale.
  ctx.textAlign = 'right';
  ctx.font = FONT_SM;
  const extent = Math.round(tsbMax / 1.2);
  const half = Math.round(extent / 2);
  ([extent, half, 0, -half, -extent] as number[]).forEach((v) => {
    if (v !== 0) {
      ctx.save();
      ctx.strokeStyle = ink(0.06);
      ctx.lineWidth = 1;
      ctx.setLineDash([]);
      ctx.beginPath();
      ctx.moveTo(left, yTsb(v));
      ctx.lineTo(right, yTsb(v));
      ctx.stroke();
      ctx.restore();
    }
    ctx.fillStyle = v < 0 ? 'rgba(239,68,68,0.85)' : v > 0 ? 'rgba(59,130,246,0.85)' : axisInk(0.4);
    ctx.fillText((v > 0 ? '+' : '') + v, mL - 6, yTsb(v) + 4);
  });
  ctx.fillStyle = 'rgba(59,130,246,0.7)';
  ctx.font = FONT_XS;
  ctx.fillText('FORM', mL - 6, top - 6);
}

function drawTsbNow(
  ctx: CanvasRenderingContext2D,
  points: PmcDataPoint[],
  vis: VisibleRange,
  predStart: number,
  xOf: (date: string) => number,
  yTsb: (v: number) => number,
): void {
  const lastRealIdx = predStart > 0 ? predStart - 1 : vis.end;
  if (lastRealIdx < vis.start || lastRealIdx > vis.end) return;
  const p = points[lastRealIdx];
  const x = xOf(p.date),
    y = yTsb(p.tsb);
  ctx.beginPath();
  ctx.arc(x, y, 4.5, 0, Math.PI * 2);
  ctx.fillStyle = p.tsb >= 0 ? TSB_COLOR : TSB_NEG_COLOR;
  ctx.fill();
  ctx.strokeStyle = 'rgba(255,255,255,0.85)';
  ctx.lineWidth = 1;
  ctx.stroke();
}

function drawPeakTsb(
  ctx: CanvasRenderingContext2D,
  visSlice: PmcDataPoint[],
  xOf: (date: string) => number,
  yTsb: (v: number) => number,
): void {
  const predicted = visSlice.filter((p) => p.predicted);
  if (!predicted.length) return;
  const peak = predicted.reduce((a, b) => (a.tsb > b.tsb ? a : b));
  if (peak.tsb <= 5) return;

  const px = xOf(peak.date),
    py = yTsb(peak.tsb);
  ctx.beginPath();
  ctx.arc(px, py, 4, 0, Math.PI * 2);
  ctx.fillStyle = TSB_COLOR;
  ctx.fill();
  ctx.fillStyle = '#fff';
  ctx.font = `bold ${FONT_XS}`;
  ctx.textAlign = 'center';
  ctx.fillText(`★ +${Math.round(peak.tsb)}`, px, py - 8);
}

function drawLoadPanel(
  ctx: CanvasRenderingContext2D,
  points: PmcDataPoint[],
  vis: VisibleRange | null,
  predStart: number,
  xOf: (date: string) => number,
  yLoad: (v: number) => number,
  mL: number,
  mR: number,
  W: number,
  maxLoad: number,
  layout: Layout,
): void {
  const left = mL,
    right = W - mR;
  const top = layout.loadTop,
    bot = layout.loadBot;

  // Horizontal gridlines + scale.
  ctx.textAlign = 'right';
  ctx.font = FONT_SM;
  [0, 0.25, 0.5, 0.75, 1].forEach((frac) => {
    const v = Math.round((maxLoad / 1.12) * frac);
    const y = yLoad(v);
    ctx.strokeStyle = ink(0.06);
    ctx.lineWidth = 1;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(left, y);
    ctx.lineTo(right, y);
    ctx.stroke();
    ctx.fillStyle = axisInk(0.4);
    ctx.fillText(String(v), left - 6, y + 4);
  });

  ctx.fillStyle = CTL_COLOR + 'cc';
  ctx.font = FONT_XS;
  ctx.textAlign = 'right';
  ctx.fillText('LOAD', mL - 6, top - 6);

  if (!vis) return;

  const ctl = seriesPoints(points, vis, predStart, xOf, (p) => yLoad(p.ctl));
  const atl = seriesPoints(points, vis, predStart, xOf, (p) => yLoad(p.atl));
  strokeSeries(ctx, atl.real, atl.pred, ATL_COLOR, 1.8);
  strokeSeries(ctx, ctl.real, ctl.pred, CTL_COLOR, 2.2);

  // Value callouts at the present-day boundary, like the reference design.
  const lastRealIdx = predStart > 0 ? predStart - 1 : vis.end;
  if (lastRealIdx >= vis.start && lastRealIdx <= vis.end) {
    const p = points[lastRealIdx];
    const cx = xOf(p.date);
    drawValueChip(ctx, cx, yLoad(p.atl), Math.round(p.atl), ATL_COLOR, right);
    drawValueChip(ctx, cx, yLoad(p.ctl), Math.round(p.ctl), CTL_COLOR, right);
  }

  // Inline legend.
  ctx.font = FONT_SM;
  ctx.textAlign = 'left';
  let lx = left + 4;
  for (const [label, color] of [
    ['Fitness', CTL_COLOR],
    ['Fatigue', ATL_COLOR],
  ] as [string, string][]) {
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(lx, top + 9);
    ctx.lineTo(lx + 14, top + 9);
    ctx.stroke();
    ctx.fillStyle = ink(0.7);
    ctx.fillText(label, lx + 18, top + 12);
    lx += 18 + ctx.measureText(label).width + 16;
  }
}

function drawValueChip(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  value: number,
  color: string,
  right: number,
): void {
  const text = String(value);
  ctx.font = `bold ${FONT_SM}`;
  const w = ctx.measureText(text).width + 12;
  const h = 16;
  let bx = x + 6;
  if (bx + w > right) bx = x - 6 - w;
  const by = y - h / 2;
  ctx.fillStyle = 'rgba(15,15,17,0.9)';
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.2;
  roundRect(ctx, bx, by, w, h, 4);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = color;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, bx + w / 2, y + 0.5);
  ctx.textBaseline = 'alphabetic';
}

function drawTssPanel(
  ctx: CanvasRenderingContext2D,
  points: PmcDataPoint[],
  vis: VisibleRange | null,
  xOf: (date: string) => number,
  maxTss: number,
  mL: number,
  mR: number,
  W: number,
  layout: Layout,
): void {
  const top = layout.tssTop,
    bot = layout.tssBot;
  const panelH = bot - top;

  ctx.fillStyle = axisInk(0.4);
  ctx.font = FONT_XS;
  ctx.textAlign = 'right';
  ctx.fillText('TSS', mL - 6, top - 4);

  // Horizontal scale: 0 / mid / peak, mirroring the LOAD panel's gridlines.
  const tssTop = Math.round(maxTss / 1.05);
  ctx.font = FONT_SM;
  [0, 0.5, 1].forEach((frac) => {
    const v = Math.round(tssTop * frac);
    const y = bot - panelH * frac;
    ctx.strokeStyle = ink(0.06);
    ctx.lineWidth = 1;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(mL, y);
    ctx.lineTo(W - mR, y);
    ctx.stroke();
    ctx.fillStyle = axisInk(0.4);
    ctx.textAlign = 'right';
    ctx.fillText(String(v), mL - 6, y + 4);
  });
  if (!vis) return;

  const { start, end } = vis;
  const visCount = Math.max(end - start, 1);
  const cW = ctx.canvas.width;
  const barW = Math.max(1.5, cW / visCount - 1);

  // Draws one stacked sport segment; `scheduled` uses the dashed, semi-transparent
  // treatment shared by future-projection bars and today's pending workouts.
  const drawSegment = (x: number, top: number, bH: number, color: string, scheduled: boolean) => {
    if (scheduled) {
      ctx.fillStyle = color;
      ctx.globalAlpha = 0.16;
      ctx.fillRect(x, top, barW, bH);
      ctx.globalAlpha = 0.7;
      ctx.strokeStyle = color;
      ctx.lineWidth = 1;
      ctx.setLineDash([2, 2]);
      ctx.strokeRect(x + 0.5, top + 0.5, barW - 1, bH - 1);
      ctx.setLineDash([]);
    } else {
      ctx.fillStyle = color;
      ctx.globalAlpha = 0.72;
      ctx.fillRect(x, top, barW, bH);
    }
  };

  for (let i = start; i <= end; i++) {
    const p = points[i];
    const x = xOf(p.date) - barW / 2;
    let y = bot;

    const entries =
      p.sportTss && Object.keys(p.sportTss).length
        ? Object.entries(p.sportTss).filter(([, t]) => t > 0)
        : !p.predicted && p.dailyTss > 0 && !p.scheduledSportTss
          ? ([['CYCLING', p.dailyTss]] as [string, number][])
          : [];

    // Completed (or fully-projected) load.
    for (const [sport, tss] of entries) {
      const bH = (tss / maxTss) * panelH;
      drawSegment(x, y - bH, bH, getSportColor(sport), p.predicted);
      y -= bH;
    }

    // Today's still-pending scheduled load, stacked on top in the scheduled style.
    if (p.scheduledSportTss) {
      for (const [sport, tss] of Object.entries(p.scheduledSportTss)) {
        if (tss <= 0) continue;
        const bH = (tss / maxTss) * panelH;
        drawSegment(x, y - bH, bH, getSportColor(sport), true);
        y -= bH;
      }
    }
    ctx.globalAlpha = 1;
  }
}

/**
 * Draws the two primary axis lines, distinct from the faint internal gridlines:
 *  - Y axis: a single vertical spine running the full height along all three panels.
 *  - X axis: a single horizontal baseline, only at the foot of the TSS panel.
 */
function drawAxisLines(
  ctx: CanvasRenderingContext2D,
  mL: number,
  W: number,
  mR: number,
  mT: number,
  H: number,
  mB: number,
  layout: Layout,
): void {
  ctx.save();
  ctx.strokeStyle = axisInk(0.25);
  ctx.lineWidth = 1;
  ctx.setLineDash([]);
  // Y axis — full height.
  ctx.beginPath();
  ctx.moveTo(mL, mT);
  ctx.lineTo(mL, layout.tssBot);
  ctx.stroke();
  // X axis — TSS baseline only.
  ctx.beginPath();
  ctx.moveTo(mL, layout.tssBot);
  ctx.lineTo(W - mR, layout.tssBot);
  ctx.stroke();
  ctx.restore();
}

function drawXAxisTicks(
  ctx: CanvasRenderingContext2D,
  viewStartDate: string,
  visDays: number,
  xOf: (date: string) => number,
  mT: number,
  H: number,
  mB: number,
): void {
  const tick = (dateStr: string, label: string) => {
    const x = xOf(dateStr);
    ctx.save();
    ctx.strokeStyle = ink(0.06);
    ctx.lineWidth = 1;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(x, mT);
    ctx.lineTo(x, H - mB);
    ctx.stroke();
    ctx.restore();
    ctx.fillStyle = axisInk(0.45);
    ctx.font = FONT_SM;
    ctx.textAlign = 'center';
    ctx.fillText(label, x, H - 10);
  };

  if (visDays <= 14) {
    for (let d = 0; d <= visDays; d++) {
      const dateStr = addDaysToDate(viewStartDate, d);
      tick(
        dateStr,
        new Date(dateStr + 'T12:00:00').toLocaleDateString('en', {
          month: 'short',
          day: 'numeric',
        }),
      );
    }
  } else if (visDays <= 60) {
    for (let d = 0; d <= visDays; d++) {
      const dateStr = addDaysToDate(viewStartDate, d);
      const dt = new Date(dateStr + 'T12:00:00');
      if (dt.getDay() === 1)
        tick(dateStr, dt.toLocaleDateString('en', { month: 'short', day: 'numeric' }));
    }
  } else {
    let lastMonth = '';
    for (let d = 0; d <= visDays; d++) {
      const dateStr = addDaysToDate(viewStartDate, d);
      const month = dateStr.substring(0, 7);
      if (month !== lastMonth) {
        lastMonth = month;
        tick(
          dateStr,
          new Date(dateStr + 'T12:00:00').toLocaleDateString('en', {
            month: 'short',
            year: '2-digit',
          }),
        );
      }
    }
  }
}

function drawTodayMarker(
  ctx: CanvasRenderingContext2D,
  today: string,
  viewStartDate: string,
  viewEndDate: string,
  xOf: (date: string) => number,
  mT: number,
  H: number,
  mB: number,
): void {
  if (today < viewStartDate || today > viewEndDate) return;
  const tx = xOf(today);
  ctx.save();
  ctx.strokeStyle = ink(0.5);
  ctx.lineWidth = 1;
  ctx.setLineDash([3, 3]);
  ctx.beginPath();
  ctx.moveTo(tx, mT);
  ctx.lineTo(tx, H - mB);
  ctx.stroke();
  ctx.restore();
  ctx.fillStyle = ink(0.85);
  ctx.font = `bold ${FONT_XS}`;
  ctx.textAlign = 'center';
  ctx.fillText('TODAY', tx, mT - 4);
}

function drawGoals(
  ctx: CanvasRenderingContext2D,
  goals: RaceGoal[],
  viewStartDate: string,
  viewEndDate: string,
  xOf: (date: string) => number,
  mT: number,
  mB: number,
  cH: number,
  H: number,
): void {
  const goalsToShow = goals.filter((g) => {
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
      for (const [w, a] of [
        [6, 0.03],
        [4, 0.05],
        [2, 0.08],
      ] as [number, number][]) {
        ctx.strokeStyle = color;
        ctx.globalAlpha = a;
        ctx.lineWidth = w;
        ctx.setLineDash([]);
        ctx.beginPath();
        ctx.moveTo(gx, mT);
        ctx.lineTo(gx, H - mB);
        ctx.stroke();
      }
      ctx.restore();
    }

    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = isA ? 2 : 1.5;
    ctx.globalAlpha = isA ? 0.85 : 0.5;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(gx, mT);
    ctx.lineTo(gx, H - mB);
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
    const dateLabel = new Date(goalDate + 'T12:00:00').toLocaleDateString('en', {
      month: 'short',
      day: 'numeric',
    });
    ctx.font = FONT_XS;
    const dateWidth = ctx.measureText(dateLabel).width;
    const labelW = Math.max(titleWidth, dateWidth) + 8;
    const goalDistance = (goal as { distance?: string }).distance;
    const labelH = goalDistance ? 40 : 28;

    let labelY = mT + (isA ? 26 : 22);
    for (const box of usedLabelBoxes) {
      if (
        gx + 3 < box.x + box.w &&
        gx + 3 + labelW > box.x &&
        labelY - 12 < box.y + box.h &&
        labelY - 12 + labelH > box.y
      ) {
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

function drawLegend(ctx: CanvasRenderingContext2D, mL: number): void {
  const items: Array<{ label: string; color: string }> = [
    { label: 'Form (TSB)', color: TSB_COLOR },
    { label: 'Fitness (CTL)', color: CTL_COLOR },
    { label: 'Fatigue (ATL)', color: ATL_COLOR },
  ];
  let lx = mL + 4;
  const ly = 18;
  items.forEach((item) => {
    ctx.strokeStyle = item.color;
    ctx.lineWidth = 2;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(lx, ly);
    ctx.lineTo(lx + 14, ly);
    ctx.stroke();
    ctx.fillStyle = ink(0.75);
    ctx.font = FONT;
    ctx.textAlign = 'left';
    ctx.fillText(item.label, lx + 18, ly + 4);
    lx += 18 + ctx.measureText(item.label).width + 18;
  });
}

function drawHover(
  ctx: CanvasRenderingContext2D,
  p: PmcDataPoint,
  xOf: (date: string) => number,
  yLoad: (v: number) => number,
  yTsb: (v: number) => number,
  mT: number,
  mR: number,
  mB: number,
  W: number,
  H: number,
  layout: Layout,
): void {
  const hx = xOf(p.date);

  ctx.save();
  ctx.strokeStyle = 'rgba(255,255,255,0.25)';
  ctx.lineWidth = 1;
  ctx.setLineDash([]);
  ctx.beginPath();
  ctx.moveTo(hx, mT);
  ctx.lineTo(hx, H - mB);
  ctx.stroke();
  ctx.restore();

  const dots = [
    { y: yTsb(p.tsb), color: p.tsb >= 0 ? TSB_COLOR : TSB_NEG_COLOR },
    { y: yLoad(p.ctl), color: CTL_COLOR },
    { y: yLoad(p.atl), color: ATL_COLOR },
  ];
  dots.forEach(({ y, color }) => {
    ctx.beginPath();
    ctx.arc(hx, y, 4, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.8)';
    ctx.lineWidth = 1;
    ctx.stroke();
  });

  const tsbSign = p.tsb >= 0 ? '+' : '';
  const rows: Array<{ label: string; value: string; color: string }> = [
    {
      label: 'TSB',
      value: `${tsbSign}${p.tsb.toFixed(1)}`,
      color: p.tsb >= 0 ? TSB_COLOR : TSB_NEG_COLOR,
    },
    { label: 'CTL', value: p.ctl.toFixed(1), color: CTL_COLOR },
    { label: 'ATL', value: p.atl.toFixed(1), color: ATL_COLOR },
    { label: 'TSS', value: String(Math.round(p.dailyTss)), color: 'rgba(255,255,255,0.55)' },
  ];

  const pad = 10,
    rowH = 18,
    boxW = 130;
  const boxH = pad + 18 + rows.length * rowH + pad;
  const dateStr = new Date(p.date + 'T12:00:00').toLocaleDateString('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

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

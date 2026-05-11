import {PacingSegment, SegmentRange} from '../../../../services/pacing.service';
import {getSlopeColor, getSlopeFill, parsePaceSeconds, SLOPE_LEGEND} from './elevation-chart.utils';

export interface ElevationRenderContext {
  ctx: CanvasRenderingContext2D;
  segments: PacingSegment[];
  highlightedRange: SegmentRange | null;
  showSpeed: boolean;
  groupMeanPowers: number[] | null;
  width: number;
  height: number;
  padding: {top: number; right: number; bottom: number; left: number};
}

export function renderElevationChart(rc: ElevationRenderContext): void {
  const {ctx, segments, highlightedRange, showSpeed, groupMeanPowers, width: w, height: h, padding: p} = rc;
  if (!segments.length) return;

  const plotW = w - p.left - p.right;
  const plotH = h - p.top - p.bottom;

  const dataZoneH = plotH * 0.4;
  const gapH = plotH * 0.08;
  const elevZoneH = plotH * 0.52;
  const dataTop = p.top;
  const elevTop = p.top + dataZoneH + gapH;

  ctx.clearRect(0, 0, w, h);

  const maxDist = Math.max(...segments.map((s) => s.endDistance));
  const elevations = segments.map((s) => s.elevation);
  const minElev = Math.min(...elevations) - 20;
  const maxElev = Math.max(...elevations) + 20;

  const xScale = (d: number) => p.left + (d / maxDist) * plotW;
  const yElevScale = (e: number) => elevTop + elevZoneH - ((e - minElev) / (maxElev - minElev)) * elevZoneH;

  drawElevationBars(ctx, segments, xScale, yElevScale, elevTop + elevZoneH);

  const hasPower = segments.some((s) => s.targetPower != null);
  const hasPace = segments.some((s) => s.targetPace != null);
  const hasSpeed = segments.some((s) => s.estimatedSpeedKmh != null && s.estimatedSpeedKmh > 0);

  if (hasPower && !showSpeed) {
    drawPowerOverlay(ctx, segments, groupMeanPowers, xScale, dataTop, dataZoneH, w, p);
  }
  if (showSpeed && hasSpeed) {
    drawSpeedOverlay(ctx, segments, xScale, dataTop, dataZoneH, w, p);
  }
  if (hasPace) {
    drawPaceOverlay(ctx, segments, xScale, dataTop, dataZoneH, w, p);
  }

  ctx.beginPath();
  ctx.moveTo(p.left, dataTop + dataZoneH + gapH / 2);
  ctx.lineTo(w - p.right, dataTop + dataZoneH + gapH / 2);
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
  ctx.lineWidth = 1;
  ctx.stroke();

  drawFatigueOverlay(ctx, segments, xScale, elevTop, elevZoneH);
  drawHighlightedRange(ctx, segments, highlightedRange, xScale, p.top, plotH);
  drawNutritionMarkers(ctx, segments, xScale, dataTop);
  drawXAxisLabels(ctx, segments, xScale, h - p.bottom + 20);

  ctx.fillStyle = 'rgba(255, 255, 255, 0.6)';
  ctx.textAlign = 'right';
  ctx.fillText(`${Math.round(maxElev)}m`, p.left - 8, elevTop + 12);
  ctx.fillText(`${Math.round(minElev)}m`, p.left - 8, elevTop + elevZoneH);

  drawLegend(ctx, p.left, p.top - 10, showSpeed, hasSpeed, hasPower, hasPace, !!groupMeanPowers);
}

function drawElevationBars(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  xScale: (d: number) => number,
  yElevScale: (e: number) => number,
  baseY: number,
): void {
  for (let i = 0; i < segments.length; i++) {
    const seg = segments[i];
    const prevSeg = i > 0 ? segments[i - 1] : null;
    const x1 = xScale(seg.startDistance);
    const x2 = xScale(seg.endDistance);
    const y1 = prevSeg ? yElevScale(prevSeg.elevation) : yElevScale(seg.elevation);
    const y2 = yElevScale(seg.elevation);
    const slopeColor = getSlopeColor(seg.gradient);
    const slopeFill = getSlopeFill(seg.gradient);

    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.lineTo(x2, baseY);
    ctx.lineTo(x1, baseY);
    ctx.closePath();
    const grad = ctx.createLinearGradient(0, Math.min(y1, y2), 0, baseY);
    grad.addColorStop(0, slopeFill);
    grad.addColorStop(1, 'rgba(0, 0, 0, 0.02)');
    ctx.fillStyle = grad;
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.strokeStyle = slopeColor;
    ctx.lineWidth = 2.5;
    ctx.stroke();
  }
}

function drawPowerOverlay(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  groupMeanPowers: number[] | null,
  xScale: (d: number) => number,
  dataTop: number,
  dataZoneH: number,
  w: number,
  p: {right: number},
): void {
  const useGroupMean = groupMeanPowers != null && groupMeanPowers.length === segments.length;
  const powerValues = useGroupMean
    ? groupMeanPowers!.filter((v) => v > 0)
    : segments.filter((s) => s.targetPower != null).map((s) => s.targetPower!);
  const minP = Math.min(...powerValues) * 0.9;
  const maxP = Math.max(...powerValues) * 1.1;
  const yPowerScale = (pw: number) => dataTop + dataZoneH - ((pw - minP) / (maxP - minP)) * dataZoneH;

  ctx.beginPath();
  let started = false;
  if (useGroupMean) {
    for (let i = 0; i < segments.length; i++) {
      const seg = segments[i];
      const gp = groupMeanPowers![i];
      if (gp <= 0) continue;
      const x1 = xScale(seg.startDistance);
      const x2 = xScale(seg.endDistance);
      const y = yPowerScale(gp);
      if (!started) {
        ctx.moveTo(x1, y);
        started = true;
      } else {
        ctx.lineTo(x1, y);
      }
      ctx.lineTo(x2, y);
    }
    ctx.strokeStyle = '#60a5fa';
    ctx.lineWidth = 2.5;
  } else {
    for (const seg of segments) {
      if (seg.targetPower == null) continue;
      const x = xScale((seg.startDistance + seg.endDistance) / 2);
      const y = yPowerScale(seg.targetPower);
      if (!started) {
        ctx.moveTo(x, y);
        started = true;
      } else {
        ctx.lineTo(x, y);
      }
    }
    ctx.strokeStyle = '#60a5fa';
    ctx.lineWidth = 2;
  }
  ctx.stroke();

  ctx.fillStyle = '#60a5fa';
  ctx.font = '11px monospace';
  ctx.textAlign = 'left';
  ctx.fillText(`${Math.round(maxP)}W`, w - p.right + 8, dataTop + 12);
  ctx.fillText(`${Math.round(minP)}W`, w - p.right + 8, dataTop + dataZoneH);
}

function drawSpeedOverlay(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  xScale: (d: number) => number,
  dataTop: number,
  dataZoneH: number,
  w: number,
  p: {right: number},
): void {
  const speeds = segments
    .filter((s) => s.estimatedSpeedKmh != null && s.estimatedSpeedKmh > 0)
    .map((s) => s.estimatedSpeedKmh!);
  const minS = Math.min(...speeds) * 0.9;
  const maxS = Math.max(...speeds) * 1.1;
  const ySpeedScale = (sp: number) => dataTop + dataZoneH - ((sp - minS) / (maxS - minS)) * dataZoneH;

  ctx.beginPath();
  let started = false;
  for (const seg of segments) {
    if (seg.estimatedSpeedKmh == null || seg.estimatedSpeedKmh <= 0) continue;
    const x = xScale((seg.startDistance + seg.endDistance) / 2);
    const y = ySpeedScale(seg.estimatedSpeedKmh);
    if (!started) {
      ctx.moveTo(x, y);
      started = true;
    } else {
      ctx.lineTo(x, y);
    }
  }
  ctx.strokeStyle = '#34d399';
  ctx.lineWidth = 2;
  ctx.stroke();

  ctx.fillStyle = '#34d399';
  ctx.font = '11px monospace';
  ctx.textAlign = 'left';
  ctx.fillText(`${maxS.toFixed(0)} km/h`, w - p.right + 8, dataTop + 12);
  ctx.fillText(`${minS.toFixed(0)} km/h`, w - p.right + 8, dataTop + dataZoneH);
}

function drawPaceOverlay(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  xScale: (d: number) => number,
  dataTop: number,
  dataZoneH: number,
  w: number,
  p: {right: number},
): void {
  const paces = segments.filter((s) => s.targetPace != null).map((s) => parsePaceSeconds(s.targetPace!));
  const minPace = Math.min(...paces) * 0.9;
  const maxPace = Math.max(...paces) * 1.1;
  const yPaceScale = (pc: number) => dataTop + ((pc - minPace) / (maxPace - minPace)) * dataZoneH;

  ctx.beginPath();
  let started = false;
  for (const seg of segments) {
    if (seg.targetPace == null) continue;
    const x = xScale((seg.startDistance + seg.endDistance) / 2);
    const y = yPaceScale(parsePaceSeconds(seg.targetPace));
    if (!started) {
      ctx.moveTo(x, y);
      started = true;
    } else {
      ctx.lineTo(x, y);
    }
  }
  ctx.strokeStyle = '#34d399';
  ctx.lineWidth = 2;
  ctx.stroke();

  ctx.fillStyle = '#34d399';
  ctx.font = '11px monospace';
  ctx.textAlign = 'left';
  const fastPace = Math.round(minPace);
  const slowPace = Math.round(maxPace);
  ctx.fillText(`${Math.floor(fastPace / 60)}:${String(fastPace % 60).padStart(2, '0')}`, w - p.right + 8, dataTop + 12);
  ctx.fillText(`${Math.floor(slowPace / 60)}:${String(slowPace % 60).padStart(2, '0')}`, w - p.right + 8, dataTop + dataZoneH);
}

function drawFatigueOverlay(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  xScale: (d: number) => number,
  elevTop: number,
  elevZoneH: number,
): void {
  if (segments.length <= 1) return;
  const maxFatigue = Math.max(...segments.map((s) => s.cumulativeFatigue));
  if (maxFatigue <= 0) return;
  for (const seg of segments) {
    const x1 = xScale(seg.startDistance);
    const x2 = xScale(seg.endDistance);
    const alpha = Math.min((seg.cumulativeFatigue / maxFatigue) * 0.15, 0.15);
    ctx.fillStyle = `rgba(239, 68, 68, ${alpha})`;
    ctx.fillRect(x1, elevTop, x2 - x1, elevZoneH);
  }
}

function drawHighlightedRange(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  range: SegmentRange | null,
  xScale: (d: number) => number,
  top: number,
  height: number,
): void {
  if (!range) return;
  const startSeg = segments[range.start];
  const endSeg = segments[Math.min(range.end, segments.length - 1)];
  if (!startSeg || !endSeg) return;
  const hx1 = xScale(startSeg.startDistance);
  const hx2 = xScale(endSeg.endDistance);
  ctx.fillStyle = 'rgba(255, 255, 255, 0.12)';
  ctx.fillRect(hx1, top, hx2 - hx1, height);
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.4)';
  ctx.lineWidth = 1;
  ctx.strokeRect(hx1, top, hx2 - hx1, height);
}

function drawNutritionMarkers(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  xScale: (d: number) => number,
  dataTop: number,
): void {
  for (const seg of segments) {
    if (!seg.nutritionSuggestion) continue;
    const x = xScale((seg.startDistance + seg.endDistance) / 2);
    ctx.fillStyle = '#fbbf24';
    ctx.beginPath();
    ctx.arc(x, dataTop + 12, 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#000';
    ctx.font = 'bold 8px monospace';
    ctx.textAlign = 'center';
    ctx.fillText('N', x, dataTop + 15);
  }
}

function drawXAxisLabels(
  ctx: CanvasRenderingContext2D,
  segments: PacingSegment[],
  xScale: (d: number) => number,
  y: number,
): void {
  ctx.fillStyle = 'rgba(255,255,255,0.6)';
  ctx.font = '11px monospace';
  ctx.textAlign = 'center';
  const numLabels = Math.min(8, segments.length);
  const step = Math.ceil(segments.length / numLabels);
  for (let i = 0; i < segments.length; i += step) {
    const s = segments[i];
    const x = xScale(s.startDistance);
    ctx.fillText((s.startDistance / 1000).toFixed(1) + 'km', x, y);
  }
}

function drawLegend(
  ctx: CanvasRenderingContext2D,
  left: number,
  y: number,
  showSpeed: boolean,
  hasSpeed: boolean,
  hasPower: boolean,
  hasPace: boolean,
  usingGroupMean: boolean,
): void {
  ctx.font = '10px monospace';
  ctx.textAlign = 'left';
  let legendX = left;
  for (const item of SLOPE_LEGEND) {
    ctx.fillStyle = item.color;
    ctx.fillRect(legendX, y - 6, 12, 4);
    ctx.fillStyle = 'rgba(255,255,255,0.7)';
    ctx.fillText(item.label, legendX + 16, y);
    legendX += ctx.measureText(item.label).width + 28;
  }
  if (showSpeed && hasSpeed) {
    ctx.fillStyle = '#34d399';
    ctx.fillText('— Speed', legendX, y);
    legendX += 70;
  } else if (hasPower) {
    ctx.fillStyle = '#60a5fa';
    const label = usingGroupMean ? '-- Group W' : '— Power';
    ctx.fillText(label, legendX, y);
    legendX += ctx.measureText(label).width + 12;
  }
  if (hasPace) {
    ctx.fillStyle = '#34d399';
    ctx.fillText('— Pace', legendX, y);
  }
}

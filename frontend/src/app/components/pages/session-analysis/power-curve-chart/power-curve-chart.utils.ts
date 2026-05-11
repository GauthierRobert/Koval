import {DURATION_LABELS} from '../../../../services/analytics.service';

export interface CurvePoint {
  duration: number;
  label: string;
  power: number;
}

export function toCurvePoints(data: Record<number, number>): CurvePoint[] {
  return Object.entries(data ?? {})
    .map(([dur, power]) => ({
      duration: Number(dur),
      power: Number(power),
      label: DURATION_LABELS[Number(dur)] ?? `${dur}s`,
    }))
    .filter((e) => Number.isFinite(e.power) && e.power > 0)
    .sort((a, b) => a.duration - b.duration);
}

/** Log-scale X position (0..1) for a duration within the point set's range. */
export function curveXRatio(duration: number, points: CurvePoint[]): number {
  const minDur = points[0].duration;
  const maxDur = points[points.length - 1].duration;
  if (maxDur === minDur) return 0.5;
  return (Math.log(duration) - Math.log(minDur)) / (Math.log(maxDur) - Math.log(minDur));
}

export function curveMargins(width: number): {mL: number; mR: number} {
  return width < 500 ? {mL: 36, mR: 24} : {mL: 48, mR: 28};
}

export const CURVE_MARGINS_Y: {mT: number; mB: number} = {mT: 14, mB: 22};

export function niceCeil(value: number): number {
  if (value <= 0) return 1;
  const pow = Math.pow(10, Math.floor(Math.log10(value)));
  const norm = value / pow;
  let nice: number;
  if (norm <= 1) nice = 1;
  else if (norm <= 2) nice = 2;
  else if (norm <= 5) nice = 5;
  else nice = 10;
  return nice * pow;
}

export function cssToRgb(css: string): [number, number, number] | null {
  if (!css) return null;
  try {
    const ctx = document.createElement('canvas').getContext('2d');
    if (!ctx) return null;
    ctx.fillStyle = css;
    const out = ctx.fillStyle;
    if (out.startsWith('#')) {
      return [
        parseInt(out.slice(1, 3), 16),
        parseInt(out.slice(3, 5), 16),
        parseInt(out.slice(5, 7), 16),
      ];
    }
    const m = out.match(/(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
    return m ? [+m[1], +m[2], +m[3]] : null;
  } catch {
    return null;
  }
}

export interface CurveTheme {
  accentRgb: [number, number, number];
  accentHex: string;
  gridColor: string;
  textColor: string;
  crosshair: string;
}

export interface CurveDrawContext {
  canvas: HTMLCanvasElement;
  points: CurvePoint[];
  theme: CurveTheme;
  hoverIdx: number | null;
}

/** Renders the mean-maximal power curve onto the canvas. Returns false if the canvas isn't sized yet. */
export function drawPowerCurve({canvas, points, theme, hoverIdx}: CurveDrawContext): boolean {
  if (points.length === 0) return false;

  const dpr = Math.max(1, window.devicePixelRatio || 1);
  const cssW = canvas.clientWidth;
  const cssH = canvas.clientHeight;
  if (cssW <= 0 || cssH <= 0) return false;
  const targetW = Math.round(cssW * dpr);
  const targetH = Math.round(cssH * dpr);
  if (canvas.width !== targetW) canvas.width = targetW;
  if (canvas.height !== targetH) canvas.height = targetH;

  const ctx = canvas.getContext('2d');
  if (!ctx) return false;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssW, cssH);

  const {mL, mR} = curveMargins(cssW);
  const {mT, mB} = CURVE_MARGINS_Y;
  const cW = Math.max(1, cssW - mL - mR);
  const cH = Math.max(1, cssH - mT - mB);

  const maxPower = Math.max(...points.map((p) => p.power));
  const yMax = niceCeil(maxPower * 1.05);

  // Y gridlines + labels
  ctx.font = '10px monospace';
  ctx.fillStyle = theme.textColor;
  ctx.strokeStyle = theme.gridColor;
  ctx.lineWidth = 1;
  const ySteps = 5;
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  for (let i = 0; i <= ySteps; i++) {
    const v = (yMax * i) / ySteps;
    const y = mT + cH * (1 - i / ySteps);
    ctx.beginPath();
    ctx.moveTo(mL, y);
    ctx.lineTo(mL + cW, y);
    ctx.stroke();
    ctx.fillText(`${Math.round(v)}W`, mL - 6, y);
  }

  // X labels (skip overlapping ones)
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  const labelPad = 6;
  let lastLabelRight = -Infinity;
  for (const p of points) {
    const x = mL + curveXRatio(p.duration, points) * cW;
    const labelW = ctx.measureText(p.label).width;
    const labelLeft = x - labelW / 2;
    if (labelLeft < lastLabelRight + labelPad) continue;
    ctx.fillText(p.label, x, mT + cH + 4);
    lastLabelRight = x + labelW / 2;
  }

  const pathPoints = points.map((p) => ({
    x: mL + curveXRatio(p.duration, points) * cW,
    y: mT + cH * (1 - p.power / yMax),
  }));

  // Filled area under curve
  ctx.beginPath();
  ctx.moveTo(pathPoints[0].x, mT + cH);
  for (const pt of pathPoints) ctx.lineTo(pt.x, pt.y);
  ctx.lineTo(pathPoints[pathPoints.length - 1].x, mT + cH);
  ctx.closePath();
  const grad = ctx.createLinearGradient(0, mT, 0, mT + cH);
  grad.addColorStop(0, `rgba(${theme.accentRgb.join(',')},0.32)`);
  grad.addColorStop(1, `rgba(${theme.accentRgb.join(',')},0.02)`);
  ctx.fillStyle = grad;
  ctx.fill();

  // Line on top
  ctx.beginPath();
  ctx.moveTo(pathPoints[0].x, pathPoints[0].y);
  for (let i = 1; i < pathPoints.length; i++) {
    ctx.lineTo(pathPoints[i].x, pathPoints[i].y);
  }
  ctx.strokeStyle = theme.accentHex;
  ctx.lineWidth = 2;
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  ctx.stroke();

  // Sample dots
  for (const pt of pathPoints) {
    ctx.beginPath();
    ctx.arc(pt.x, pt.y, 2.5, 0, Math.PI * 2);
    ctx.fillStyle = theme.accentHex;
    ctx.fill();
  }

  // Hover crosshair + highlighted dot
  if (hoverIdx !== null && hoverIdx >= 0 && hoverIdx < pathPoints.length) {
    const hp = pathPoints[hoverIdx];
    ctx.strokeStyle = theme.crosshair;
    ctx.lineWidth = 1;
    ctx.setLineDash([3, 3]);
    ctx.beginPath();
    ctx.moveTo(hp.x, mT);
    ctx.lineTo(hp.x, mT + cH);
    ctx.stroke();
    ctx.setLineDash([]);

    ctx.beginPath();
    ctx.arc(hp.x, hp.y, 5, 0, Math.PI * 2);
    ctx.fillStyle = theme.accentHex;
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.stroke();
  }
  return true;
}

export function resolveCurveTheme(): CurveTheme {
  const s = getComputedStyle(document.documentElement);
  const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
  const raw = s.getPropertyValue('--accent-color').trim();
  const rgb = cssToRgb(raw) ?? [255, 157, 0];
  return {
    accentRgb: rgb,
    accentHex: `rgb(${rgb.join(',')})`,
    gridColor: isDark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)',
    textColor: isDark ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.55)',
    crosshair: isDark ? 'rgba(255,255,255,0.30)' : 'rgba(0,0,0,0.25)',
  };
}

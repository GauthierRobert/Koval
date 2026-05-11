import {VolumeEntry} from '../../../../services/analytics.service';

export type VolumeMetric = 'time' | 'tss' | 'distance';

export const SPORT_STACK = ['SWIMMING', 'CYCLING', 'RUNNING'] as const;
export type SportKey = (typeof SPORT_STACK)[number];

export const SPORT_COLORS: Record<string, string> = {
  SWIMMING: '#00a0e9',
  CYCLING: '#34d399',
  RUNNING: '#f87171',
};

export const SPORT_LABELS: Record<string, string> = {
  SWIMMING: 'Swim',
  CYCLING: 'Bike',
  RUNNING: 'Run',
};

export interface StackSegment {
  sport: string;
  bottom: number;
  top: number;
  val: number;
}

export function getSportValue(entry: VolumeEntry, sport: string, metric: VolumeMetric): number {
  switch (metric) {
    case 'tss':
      return entry.sportTss?.[sport] ?? 0;
    case 'time':
      return (entry.sportDurationSeconds?.[sport] ?? 0) / 3600;
    case 'distance':
      return (entry.sportDistanceMeters?.[sport] ?? 0) / 1000;
  }
}

export function getTotalValue(entry: VolumeEntry, metric: VolumeMetric): number {
  switch (metric) {
    case 'tss':
      return entry.totalTss;
    case 'time':
      return entry.totalDurationSeconds / 3600;
    case 'distance':
      return entry.totalDistanceMeters / 1000;
  }
}

export function formatAxisValue(val: number, metric: VolumeMetric): string {
  switch (metric) {
    case 'tss':
      return `${Math.round(val)}`;
    case 'time': {
      const h = Math.floor(val);
      const m = Math.round((val - h) * 60);
      return h > 0 ? `${h}h${m > 0 ? String(m).padStart(2, '0') : ''}` : `${m}m`;
    }
    case 'distance':
      return `${val.toFixed(1)}km`;
  }
}

export function formatTooltipValue(val: number, metric: VolumeMetric): string {
  switch (metric) {
    case 'tss':
      return `${Math.round(val)} TSS`;
    case 'time': {
      const h = Math.floor(val);
      const m = Math.round((val - h) * 60);
      return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}m`;
    }
    case 'distance':
      return `${val.toFixed(1)} km`;
  }
}

export function formatWeekLabel(period: string): string {
  const match = period.match(/W(\d+)$/);
  return match ? `W${match[1]}` : period;
}

export function parseColor(raw: string): [number, number, number] | null {
  if (!raw) return null;
  const hex = raw.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (hex) {
    const h = hex[1].length === 3 ? hex[1].split('').map((c) => c + c).join('') : hex[1];
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
  }
  const rgb = raw.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
  if (rgb) return [parseInt(rgb[1]), parseInt(rgb[2]), parseInt(rgb[3])];
  return null;
}

export function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

/** Round up to a clean grid-friendly number just above val. */
export function niceMaxY(val: number, gridCount = 4): number {
  if (val <= 0) return 1;
  const raw = val * 1.2; // 20% headroom
  const step = raw / gridCount;
  const orderStep = Math.pow(10, Math.floor(Math.log10(step)));
  const fracStep = step / orderStep;
  let niceStep: number;
  if (fracStep <= 1) niceStep = 1;
  else if (fracStep <= 1.5) niceStep = 1.5;
  else if (fracStep <= 2) niceStep = 2;
  else if (fracStep <= 2.5) niceStep = 2.5;
  else if (fracStep <= 5) niceStep = 5;
  else niceStep = 10;
  return niceStep * orderStep * gridCount;
}

export function roundRectPath(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
): void {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
}

export function isoWeekKey(d: Date): string {
  const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  date.setUTCDate(date.getUTCDate() + 4 - (date.getUTCDay() || 7));
  const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil(((date.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  return `${date.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
}

const EMPTY_ENTRY: Omit<VolumeEntry, 'period'> = {
  totalTss: 0,
  totalDurationSeconds: 0,
  totalDistanceMeters: 0,
  sportTss: {},
  sportDurationSeconds: {},
  sportDistanceMeters: {},
};

/** Returns the last 10 ISO weeks (current + 9 prior) with gaps filled. */
export function getLast10Weeks(data: VolumeEntry[] | null | undefined): VolumeEntry[] {
  const weeks: string[] = [];
  const now = new Date();
  for (let i = 9; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(d.getDate() - i * 7);
    weeks.push(isoWeekKey(d));
  }
  const dataMap = new Map<string, VolumeEntry>();
  for (const e of data ?? []) dataMap.set(e.period, e);
  return weeks.map((w) => dataMap.get(w) ?? {...EMPTY_ENTRY, period: w});
}

export function buildStacks(entries: VolumeEntry[], metric: VolumeMetric): StackSegment[][] {
  return entries.map((e) => {
    let cum = 0;
    return SPORT_STACK.map((sport) => {
      const val = getSportValue(e, sport, metric);
      const bottom = cum;
      cum += val;
      return {sport, bottom, top: cum, val};
    });
  });
}

export type TimelineSport =
  | 'CYCLING'
  | 'RUNNING'
  | 'SWIMMING'
  | 'TRIATHLON'
  | 'OTHER'
  | string;

export type TimelinePriority = 'A' | 'B' | 'C';

export interface TimelineItem<T = unknown> {
  id: string;
  title: string;
  sport: TimelineSport;
  raceDate?: string;
  priority?: TimelinePriority;
  isPrimary?: boolean;
  /** Structured race-distance enum from the backend (e.g. TRI_OLYMPIC). When present,
   * drives the short marker label exactly; otherwise we fall back to title-text inference. */
  distanceCategory?: string | null;
  data?: T;
}

export type LaneKey = 'run' | 'tri' | 'bike';

export interface TimelineMarker<T> extends TimelineItem<T> {
  _x: number;
  _lane: LaneKey;
  _statusKey: 'A' | 'B' | 'C' | 'PASSED';
  _statusLabel: string;
  _short: string;
  _dateShort: string;
  _passed: boolean;
  _above: boolean;
}

export interface AxisTick {
  label: string;
  x: number;
}

export interface GridLine {
  x: number;
  type: 'day' | 'week' | 'month';
}

export const DAY_MS = 86_400_000;
export const MIN_SPAN_MS = 30 * DAY_MS;
export const MAX_SPAN_MS = 366 * DAY_MS;
export const WEEK_GRID_THRESHOLD = 120 * DAY_MS;
export const DAY_GRID_THRESHOLD = 45 * DAY_MS;

export function clamp01(n: number): number {
  return Math.max(0, Math.min(1, n));
}

export function parseDate(dateStr: string | undefined | null): Date | null {
  if (!dateStr) return null;
  const direct = new Date(dateStr);
  if (!isNaN(direct.getTime())) return direct;
  const padded = new Date(dateStr + 'T00:00:00');
  if (!isNaN(padded.getTime())) return padded;
  return null;
}

export function isUpcoming(d: Date): boolean {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(d);
  target.setHours(0, 0, 0, 0);
  return target.getTime() >= today.getTime();
}

export function laneOfSport(sport: TimelineSport): LaneKey {
  switch (sport) {
    case 'RUNNING':
      return 'run';
    case 'CYCLING':
      return 'bike';
    case 'TRIATHLON':
    case 'SWIMMING':
      return 'tri';
    default:
      return 'bike';
  }
}

const CATEGORY_LABELS: Record<string, string> = {
  // Triathlon
  TRI_PROMO: 'PRO',
  TRI_SUPER_SPRINT: 'SSP',
  TRI_SPRINT: 'SPR',
  TRI_OLYMPIC: 'OLY',
  TRI_HALF: '70.3',
  TRI_IRONMAN: 'IM',
  TRI_ULTRA: 'XXL',
  TRI_AQUATHLON: 'AQT',
  TRI_DUATHLON: 'DUA',
  TRI_AQUABIKE: 'AQB',
  TRI_CROSS: 'XTR',
  // Running
  RUN_5K: '5K',
  RUN_10K: '10K',
  RUN_HALF_MARATHON: '21K',
  RUN_MARATHON: 'MAR',
  RUN_ULTRA: 'ULT',
  // Cycling
  BIKE_GRAN_FONDO: 'GRF',
  BIKE_MEDIO_FONDO: 'MDF',
  BIKE_TT: 'TT',
  BIKE_ULTRA: 'ULT',
  // Swimming
  SWIM_1500M: '1.5K',
  SWIM_5K: '5K',
  SWIM_10K: '10K',
  SWIM_MARATHON: '25K',
  SWIM_ULTRA: 'ULT',
};

export function shortLabelForCategory(category: string | null | undefined): string | null {
  return category ? (CATEGORY_LABELS[category] ?? null) : null;
}

export function shortLabelFor<T>(item: TimelineItem<T>): string {
  const text = `${item.title ?? ''}`.toLowerCase();
  if (item.sport === 'RUNNING') {
    if (/marathon|42/.test(text)) return 'MAR';
    if (/semi|21|half/.test(text)) return '21K';
    if (/10\s?k|10km/.test(text)) return '10K';
    if (/5\s?k|5km/.test(text)) return '5K';
    return 'RUN';
  }
  if (item.sport === 'CYCLING') {
    if (/etape|granfondo|gravel|cyclo/.test(text)) return 'GRF';
    return 'BIKE';
  }
  if (item.sport === 'SWIMMING') return 'SWIM';
  if (item.sport === 'TRIATHLON') {
    if (/ironman|140\.6/.test(text)) return 'IM';
    if (/70\.3|half/.test(text)) return '70.3';
    if (/olympic|olympique/.test(text)) return 'OLY';
    if (/sprint/.test(text)) return 'SPR';
    return 'TRI';
  }
  return '—';
}

export function monthShort(d: Date): string {
  return d
    .toLocaleDateString('fr-FR', {month: 'short'})
    .replace('.', '')
    .toUpperCase()
    .slice(0, 3);
}

export function monthLong(d: Date): string {
  return d.toLocaleDateString('fr-FR', {month: 'long', year: 'numeric'});
}

export function formatDayMonth(d: Date): string {
  return `${String(d.getDate()).padStart(2, '0')} ${monthShort(d)}`;
}

export function formatDayMonthYear(d: Date): string {
  return `${formatDayMonth(d)} ${d.getFullYear()}`;
}

export function formatDateShort(dateStr: string | undefined | null): string {
  const d = parseDate(dateStr);
  if (!d) return 'Date à définir';
  return `${String(d.getDate()).padStart(2, '0')} ${monthShort(d)} ${d.getFullYear()}`;
}

export function formatToday(d: Date): string {
  return `${String(d.getDate()).padStart(2, '0')} ${monthShort(d)}`;
}

export function buildGridLines(start: Date, end: Date, spanMs: number): GridLine[] {
  if (spanMs <= 0) return [];
  const startMs = start.getTime();
  const endMs = end.getTime();
  const lines: GridLine[] = [];
  const placed = new Set<number>();

  const place = (t: number, type: GridLine['type']) => {
    if (t < startMs || t > endMs) return;
    if (placed.has(t)) return;
    placed.add(t);
    lines.push({x: ((t - startMs) / spanMs) * 100, type});
  };

  const monthCursor = new Date(start.getFullYear(), start.getMonth(), 1);
  if (monthCursor.getTime() < startMs) {
    monthCursor.setMonth(monthCursor.getMonth() + 1);
  }
  while (monthCursor.getTime() <= endMs) {
    place(monthCursor.getTime(), 'month');
    monthCursor.setMonth(monthCursor.getMonth() + 1);
  }

  if (spanMs <= WEEK_GRID_THRESHOLD) {
    const weekCursor = new Date(start);
    weekCursor.setHours(0, 0, 0, 0);
    const dow = weekCursor.getDay();
    const offset = ((8 - dow) % 7) || 7;
    weekCursor.setDate(weekCursor.getDate() + offset);
    if (weekCursor.getTime() < startMs) {
      weekCursor.setDate(weekCursor.getDate() + 7);
    }
    while (weekCursor.getTime() <= endMs) {
      place(weekCursor.getTime(), 'week');
      weekCursor.setDate(weekCursor.getDate() + 7);
    }
  }

  if (spanMs <= DAY_GRID_THRESHOLD) {
    const dayCursor = new Date(start);
    dayCursor.setHours(0, 0, 0, 0);
    if (dayCursor.getTime() < startMs) {
      dayCursor.setDate(dayCursor.getDate() + 1);
    }
    while (dayCursor.getTime() <= endMs) {
      place(dayCursor.getTime(), 'day');
      dayCursor.setDate(dayCursor.getDate() + 1);
    }
  }

  return lines;
}

export function buildTicks(start: Date, end: Date, spanMs: number): AxisTick[] {
  if (spanMs <= 0) return [];
  const ticks: AxisTick[] = [];

  if (spanMs >= 75 * DAY_MS) {
    const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    if (cursor.getTime() < start.getTime()) {
      cursor.setMonth(cursor.getMonth() + 1);
    }
    while (cursor.getTime() <= end.getTime()) {
      const x = ((cursor.getTime() - start.getTime()) / spanMs) * 100;
      if (x >= 0 && x <= 100) {
        ticks.push({label: monthShort(cursor), x});
      }
      cursor.setMonth(cursor.getMonth() + 1);
    }
  } else {
    for (let i = 0; ; i++) {
      const t = new Date(start.getTime() + i * 7 * DAY_MS);
      if (t.getTime() > end.getTime()) break;
      const x = ((t.getTime() - start.getTime()) / spanMs) * 100;
      ticks.push({label: formatDayMonth(t), x});
    }
  }

  return ticks;
}

export function buildMarkers<T>(
  items: TimelineItem<T>[],
  start: Date,
  end: Date,
  collisionThresholdPct: number,
): Record<LaneKey, TimelineMarker<T>[]> {
  const span = end.getTime() - start.getTime();
  const lanes: Record<LaneKey, TimelineMarker<T>[]> = {run: [], tri: [], bike: []};

  for (const item of items) {
    const d = parseDate(item.raceDate);
    if (!d) continue;
    if (d < start || d > end) continue;
    const x = ((d.getTime() - start.getTime()) / span) * 100;
    const lane = laneOfSport(item.sport);
    const passed = !isUpcoming(d);
    lanes[lane].push({
      ...item,
      _x: x,
      _lane: lane,
      _passed: passed,
      _statusKey: passed ? 'PASSED' : (item.priority ?? 'C'),
      _statusLabel: passed ? '✓' : (item.priority ?? 'C'),
      _short: shortLabelForCategory(item.distanceCategory) ?? shortLabelFor(item),
      _dateShort: formatDateShort(item.raceDate),
      _above: false,
    });
  }

  for (const key of Object.keys(lanes) as LaneKey[]) {
    lanes[key] = resolveOverlap(lanes[key], collisionThresholdPct);
  }
  return lanes;
}

function resolveOverlap<T>(
  markers: TimelineMarker<T>[],
  thresholdPct: number,
): TimelineMarker<T>[] {
  if (markers.length === 0) return markers;
  const sorted = [...markers].sort((a, b) => a._x - b._x);
  let lastBelow = -Infinity;
  let lastAbove = -Infinity;
  for (const m of sorted) {
    const distBelow = m._x - lastBelow;
    const distAbove = m._x - lastAbove;
    if (distBelow >= thresholdPct) {
      m._above = false;
      lastBelow = m._x;
    } else if (distAbove >= thresholdPct) {
      m._above = true;
      lastAbove = m._x;
    } else {
      m._above = distAbove > distBelow;
      if (m._above) lastAbove = m._x;
      else lastBelow = m._x;
    }
  }
  return sorted;
}

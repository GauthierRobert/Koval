import {SavedSession} from '../../../services/history.service';

export type WeekHeaderRow = {kind: 'week'; weekKey: string; start: Date; end: Date; count: number};
export type SessionRow = {kind: 'session'; weekKey: string; session: SavedSession};
export type InactivityRow = {kind: 'inactivity'; rowKey: string; start: Date; end: Date; weeks: number};
export type HistoryRow = WeekHeaderRow | SessionRow | InactivityRow;

const ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;

/** Monday 00:00 of the week containing `d` (local time). */
export function weekStartOf(d: Date): Date {
  const out = new Date(d);
  out.setHours(0, 0, 0, 0);
  const day = out.getDay(); // 0=Sun..6=Sat
  const offsetToMonday = day === 0 ? -6 : 1 - day;
  out.setDate(out.getDate() + offsetToMonday);
  return out;
}

/** Stable per-week key (Monday's local YYYY-MM-DD). */
export function weekKeyOf(d: Date): string {
  const monday = weekStartOf(d);
  const y = monday.getFullYear();
  const m = String(monday.getMonth() + 1).padStart(2, '0');
  const day = String(monday.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function formatWeekRange(start: Date, end: Date): string {
  const opts: Intl.DateTimeFormatOptions = {month: 'short', day: 'numeric'};
  return `${start.toLocaleDateString('en-US', opts)} – ${end.toLocaleDateString('en-US', opts)}`;
}

/**
 * Build the flat row sequence the template iterates: alternating week headers
 * and (when expanded) their sessions, with a single inactivity row between two
 * active weeks separated by ≥ 1 fully-skipped week. Sessions arrive sorted
 * newest→oldest.
 */
export function buildHistoryRows(
  sessions: SavedSession[],
  expandedWeeks: ReadonlySet<string>,
): HistoryRow[] {
  if (sessions.length === 0) return [];

  const order: string[] = [];
  const buckets = new Map<string, {start: Date; end: Date; sessions: SavedSession[]}>();
  for (const s of sessions) {
    const start = weekStartOf(new Date(s.date));
    const key = weekKeyOf(new Date(s.date));
    let bucket = buckets.get(key);
    if (!bucket) {
      const end = new Date(start);
      end.setDate(end.getDate() + 6);
      end.setHours(23, 59, 59, 999);
      bucket = {start, end, sessions: []};
      buckets.set(key, bucket);
      order.push(key);
    }
    bucket.sessions.push(s);
  }

  const rows: HistoryRow[] = [];
  let prev: {start: Date; end: Date} | null = null;

  for (const key of order) {
    const bucket = buckets.get(key)!;

    if (prev) {
      const gapWeeks = Math.round((prev.start.getTime() - bucket.end.getTime()) / ONE_WEEK_MS);
      if (gapWeeks >= 1) {
        const gapStart = new Date(bucket.end);
        gapStart.setDate(gapStart.getDate() + 1);
        gapStart.setHours(0, 0, 0, 0);
        const gapEnd = new Date(prev.start);
        gapEnd.setDate(gapEnd.getDate() - 1);
        gapEnd.setHours(23, 59, 59, 999);
        rows.push({
          kind: 'inactivity',
          rowKey: `gap-${weekKeyOf(gapStart)}-${weekKeyOf(gapEnd)}`,
          start: gapStart,
          end: gapEnd,
          weeks: gapWeeks,
        });
      }
    }

    rows.push({
      kind: 'week',
      weekKey: key,
      start: bucket.start,
      end: bucket.end,
      count: bucket.sessions.length,
    });

    if (expandedWeeks.has(key)) {
      for (const s of bucket.sessions) {
        rows.push({kind: 'session', weekKey: key, session: s});
      }
    }

    prev = {start: bucket.start, end: bucket.end};
  }

  return rows;
}

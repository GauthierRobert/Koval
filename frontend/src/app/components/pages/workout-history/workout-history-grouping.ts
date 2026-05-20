import { SavedSession } from '../../../services/history.service';

export type WeekHeaderRow = {
  kind: 'week';
  weekKey: string;
  start: Date;
  end: Date;
  count: number;
};
export type SessionRow = {
  kind: 'session';
  weekKey: string;
  session: SavedSession;
  /** When set, this row is a child of an expanded group — render indented with a connector. */
  groupedUnder?: string;
};
export type InactivityRow = {
  kind: 'inactivity';
  rowKey: string;
  start: Date;
  end: Date;
  weeks: number;
};
export type GroupRow = { kind: 'group'; weekKey: string; groupKey: string; group: SessionGroup };
export type HistoryRow = WeekHeaderRow | SessionRow | InactivityRow | GroupRow;

export type GroupKind = 'race' | 'brick';
/** How a group was formed. `manual` = the athlete linked sessions by hand; `auto` = inferred from chronological proximity. */
export type GroupSource = 'manual' | 'auto';

export interface SessionGroup {
  source: GroupSource;
  /** Stable key derived from member ids — survives list re-renders. */
  key: string;
  kind: GroupKind;
  /** Sessions in chronological order (oldest→newest within the group). */
  sessions: SavedSession[];
  /** End of the last session — used to sort groups against standalone sessions. */
  endTime: Date;
  /** Start of the first session. */
  startTime: Date;
  /** Total time including transition gaps. */
  spanSeconds: number;
  /** Sum of session durations (excludes transitions). */
  movingSeconds: number;
  /** Distinct sport types, ordered by first appearance — drives the title and icon sequence. */
  sportSequence: SavedSession['sportType'][];
}

const ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;
/** Max gap between end of one session and start of the next to consider them linked. */
const GROUP_GAP_MS = 30 * 60 * 1000;
/** ≥ this many chained sessions = race (triathlon, duathlon, aquathlon…). */
const RACE_MIN_SESSIONS = 3;

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
  const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
  return `${start.toLocaleDateString('en-US', opts)} – ${end.toLocaleDateString('en-US', opts)}`;
}

function sessionStartEnd(s: SavedSession): { start: Date; end: Date } {
  const end = new Date(s.date);
  const start = new Date(end.getTime() - (s.totalDuration ?? 0) * 1000);
  return { start, end };
}

/**
 * Walks a week's sessions in chronological order and chains together any pair
 * separated by ≤ {@link GROUP_GAP_MS}. Chains of length 1 collapse back to a
 * plain session — only multi-session chains become groups.
 */
function detectGroupsInWeek(weekSessions: SavedSession[]): {
  groups: SessionGroup[];
  ungrouped: Set<string>;
} {
  // 1) Manual groups first — sessions sharing a non-empty `groupId` are bundled
  //    regardless of timing. Server enforces same-day; we trust it client-side.
  const manualBuckets = new Map<string, SavedSession[]>();
  const remaining: SavedSession[] = [];
  for (const s of weekSessions) {
    if (s.groupId) {
      const bucket = manualBuckets.get(s.groupId) ?? [];
      bucket.push(s);
      manualBuckets.set(s.groupId, bucket);
    } else {
      remaining.push(s);
    }
  }

  const groups: SessionGroup[] = [];
  const grouped = new Set<string>();

  for (const [gid, members] of manualBuckets) {
    // A lone session with a groupId isn't a group — render it normally so the
    // user isn't confused by a "group of one".
    if (members.length < 2) {
      for (const m of members) remaining.push(m);
      continue;
    }
    members.sort((a, b) => sessionStartEnd(a).start.getTime() - sessionStartEnd(b).start.getTime());
    const sports = distinctOrdered(members.map((s) => s.sportType));
    // Race-classified sessions force the "race" kind regardless of count/sport variety —
    // a 2-leg duathlon is still a race, even though the count heuristic would call it a brick.
    const isRaceClassified = members.some((s) => s.raceRole === 'RACE');
    const kind: GroupKind =
      isRaceClassified || members.length >= RACE_MIN_SESSIONS || sports.length >= 3
        ? 'race'
        : 'brick';
    const startTime = sessionStartEnd(members[0]).start;
    const endTime = sessionStartEnd(members[members.length - 1]).end;
    const movingSeconds = members.reduce((acc, s) => acc + (s.totalDuration ?? 0), 0);
    const spanSeconds = Math.round((endTime.getTime() - startTime.getTime()) / 1000);
    groups.push({
      key: `manual-${gid}`,
      source: 'manual',
      kind,
      sessions: members,
      startTime,
      endTime,
      spanSeconds,
      movingSeconds,
      sportSequence: sports,
    });
    for (const m of members) grouped.add(m.id);
  }

  // 2) Time-proximity fallback for the rest.
  const chrono = [...remaining].sort(
    (a, b) => sessionStartEnd(a).start.getTime() - sessionStartEnd(b).start.getTime(),
  );

  let current: SavedSession[] = [];
  let currentEnd = -Infinity;

  const flush = () => {
    if (current.length >= 2) {
      const sports = distinctOrdered(current.map((s) => s.sportType));
      // Same-sport chains (e.g., 2 bike rides 20 min apart) aren't bricks — skip them.
      if (sports.length >= 2) {
        const kind: GroupKind = current.length >= RACE_MIN_SESSIONS ? 'race' : 'brick';
        const startTime = sessionStartEnd(current[0]).start;
        const endTime = sessionStartEnd(current[current.length - 1]).end;
        const movingSeconds = current.reduce((acc, s) => acc + (s.totalDuration ?? 0), 0);
        const spanSeconds = Math.round((endTime.getTime() - startTime.getTime()) / 1000);
        const key = `auto-${current.map((s) => s.id).join('-')}`;
        groups.push({
          key,
          source: 'auto',
          kind,
          sessions: current,
          startTime,
          endTime,
          spanSeconds,
          movingSeconds,
          sportSequence: sports,
        });
        for (const s of current) grouped.add(s.id);
      }
    }
    current = [];
  };

  for (const s of chrono) {
    const { start, end } = sessionStartEnd(s);
    if (current.length === 0 || start.getTime() - currentEnd <= GROUP_GAP_MS) {
      current.push(s);
    } else {
      flush();
      current = [s];
    }
    currentEnd = end.getTime();
  }
  flush();

  const ungrouped = new Set(weekSessions.filter((s) => !grouped.has(s.id)).map((s) => s.id));
  return { groups, ungrouped };
}

function distinctOrdered<T>(values: T[]): T[] {
  const seen = new Set<T>();
  const out: T[] = [];
  for (const v of values) {
    if (!seen.has(v)) {
      seen.add(v);
      out.push(v);
    }
  }
  return out;
}

/**
 * Build the flat row sequence the template iterates: alternating week headers
 * and (when expanded) their sessions/groups, with a single inactivity row
 * between two active weeks separated by ≥ 1 fully-skipped week.
 *
 * Within a week, items are emitted newest→oldest; group rows take the slot of
 * their newest member, and their internal children render chronologically
 * (swim→bike→run for a triathlon).
 *
 * Sessions arrive newest→oldest.
 */
export function buildHistoryRows(
  sessions: SavedSession[],
  expandedWeeks: ReadonlySet<string>,
  expandedGroups: ReadonlySet<string>,
): HistoryRow[] {
  if (sessions.length === 0) return [];

  const order: string[] = [];
  const buckets = new Map<string, { start: Date; end: Date; sessions: SavedSession[] }>();
  for (const s of sessions) {
    const start = weekStartOf(new Date(s.date));
    const key = weekKeyOf(new Date(s.date));
    let bucket = buckets.get(key);
    if (!bucket) {
      const end = new Date(start);
      end.setDate(end.getDate() + 6);
      end.setHours(23, 59, 59, 999);
      bucket = { start, end, sessions: [] };
      buckets.set(key, bucket);
      order.push(key);
    }
    bucket.sessions.push(s);
  }

  const rows: HistoryRow[] = [];
  let prev: { start: Date; end: Date } | null = null;

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
      const { groups, ungrouped } = detectGroupsInWeek(bucket.sessions);

      // Build a per-week display list: items keyed by their "anchor time"
      // (group endTime for groups, session date for standalone). Sort newest
      // first to match the existing visual order.
      type Item =
        | { kind: 'session'; time: number; session: SavedSession }
        | { kind: 'group'; time: number; group: SessionGroup };

      const items: Item[] = [];
      for (const s of bucket.sessions) {
        if (ungrouped.has(s.id)) {
          items.push({ kind: 'session', time: new Date(s.date).getTime(), session: s });
        }
      }
      for (const g of groups) {
        items.push({ kind: 'group', time: g.endTime.getTime(), group: g });
      }
      items.sort((a, b) => b.time - a.time);

      for (const it of items) {
        if (it.kind === 'session') {
          rows.push({ kind: 'session', weekKey: key, session: it.session });
        } else {
          rows.push({ kind: 'group', weekKey: key, groupKey: it.group.key, group: it.group });
          if (expandedGroups.has(it.group.key)) {
            for (const child of it.group.sessions) {
              rows.push({
                kind: 'session',
                weekKey: key,
                session: child,
                groupedUnder: it.group.key,
              });
            }
          }
        }
      }
    }

    prev = { start: bucket.start, end: bucket.end };
  }

  return rows;
}

/**
 * Human label for a group — "Triathlon" / "Duathlon" for race-kind, otherwise a
 * sport chain like "Bike → Run". Sport tokens are pulled from i18n at the call
 * site (the grouping module stays translation-agnostic).
 */
export function groupTitleFor(group: SessionGroup, sportLabels: Record<string, string>): string {
  if (group.kind === 'race') {
    const sports = new Set(group.sportSequence);
    if (sports.has('SWIMMING') && sports.has('CYCLING') && sports.has('RUNNING')) {
      return 'Triathlon';
    }
    return 'Multi-sport';
  }
  // Brick: arrow chain of sport labels.
  return group.sportSequence.map((s) => sportLabels[s] ?? s).join(' → ');
}

import {CalendarClubSession} from '../../../services/calendar.service';
import {ScheduledWorkout} from '../../../services/coach.service';
import {SavedSession} from '../../../services/history.service';
import {TrainingPlan} from '../../../models/plan.model';

export interface CalendarDay {
  date: Date;
  key: string;
  isToday: boolean;
}

export interface ScheduledEntry { kind: 'scheduled'; scheduled: ScheduledWorkout; }
export interface FusedEntry      { kind: 'fused'; scheduled: ScheduledWorkout; session: SavedSession; }
export interface StandaloneEntry { kind: 'standalone'; session: SavedSession; }
export interface ClubSessionEntry { kind: 'club-session'; clubSession: CalendarClubSession; linkedSession?: SavedSession; }
export type CalendarEntry = ScheduledEntry | FusedEntry | StandaloneEntry | ClubSessionEntry;

export interface ClubCalendarPreferences {
  hiddenClubIds: string[];
  hiddenGroupIds: string[];
}

export type EntriesByDay = Map<string, CalendarEntry[]>;
export type WorkoutsByDay = Map<string, ScheduledWorkout[]>;

export interface VisiblePlan {
  id: string;
  title: string;
  currentWeek: number;
  totalWeeks: number;
  weekLabel: string | null;
  sportType: string;
}

export interface PlanBannerSegment {
  planId: string;
  title: string;
  sportType: string;
  startCol: number;   // 1-7 (CSS grid 1-based)
  spanCols: number;
  row: number;        // 0-5 (which week-row in month grid)
  isStart: boolean;   // plan starts in this row → left rounded corner
  isEnd: boolean;     // plan ends in this row → right rounded corner
  weekNumber: number;
  weekLabel: string | null;
}

export type BannersByRow = Map<number, PlanBannerSegment[]>;

export const DAYS_IN_WEEK = 7;
const MS_PER_DAY = 1000 * 60 * 60 * 24;

export function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function buildWeek(baseDate: Date): CalendarDay[] {
  const monday = new Date(baseDate);
  const dayOfWeek = monday.getDay();
  const offset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  monday.setDate(baseDate.getDate() + offset);
  const todayStr = new Date().toDateString();

  return Array.from({length: DAYS_IN_WEEK}, (_, i) => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + i);
    return {date, key: toDateKey(date), isToday: date.toDateString() === todayStr};
  });
}

/** Build the 6-week (42-day) grid for the month containing baseDate. */
export function buildMonth(baseDate: Date): {days: CalendarDay[]; startOfMonth: Date; endOfMonth: Date} {
  const startOfMonth = new Date(baseDate.getFullYear(), baseDate.getMonth(), 1);
  const dayOfWeek = startOfMonth.getDay();
  const offset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const startGrid = new Date(startOfMonth);
  startGrid.setDate(startOfMonth.getDate() + offset);

  const days: CalendarDay[] = [];
  const todayStr = new Date().toDateString();
  for (let i = 0; i < 42; i++) {
    const date = new Date(startGrid);
    date.setDate(startGrid.getDate() + i);
    days.push({date, key: toDateKey(date), isToday: date.toDateString() === todayStr});
  }
  return {
    days,
    startOfMonth,
    endOfMonth: new Date(baseDate.getFullYear(), baseDate.getMonth() + 1, 0),
  };
}

export function groupByDay(schedule: ScheduledWorkout[]): WorkoutsByDay {
  const byDay: WorkoutsByDay = new Map();
  for (const w of schedule) {
    const list = byDay.get(w.scheduledDate);
    if (list) list.push(w);
    else byDay.set(w.scheduledDate, [w]);
  }
  return byDay;
}

export function buildEntriesByDay(
  scheduled: ScheduledWorkout[],
  sessions: SavedSession[],
  clubSessions: CalendarClubSession[] = [],
): EntriesByDay {
  const byDay: EntriesByDay = new Map();
  const sessionById = new Map(sessions.map(s => [s.id, s]));
  const consumed = new Set<string>();

  for (const sw of scheduled) {
    if (!byDay.has(sw.scheduledDate)) byDay.set(sw.scheduledDate, []);
    if (sw.status === 'COMPLETED' && sw.sessionId && sessionById.has(sw.sessionId)) {
      consumed.add(sw.sessionId);
      byDay.get(sw.scheduledDate)!.push({kind: 'fused', scheduled: sw, session: sessionById.get(sw.sessionId)!});
    } else {
      byDay.get(sw.scheduledDate)!.push({kind: 'scheduled', scheduled: sw});
    }
  }

  const sessionByClubSessionId = new Map<string, SavedSession>();
  for (const sess of sessions) {
    if (sess.clubSessionId) {
      sessionByClubSessionId.set(sess.clubSessionId, sess);
      consumed.add(sess.id);
    }
  }

  for (const sess of sessions) {
    if (consumed.has(sess.id)) continue;
    const key = toDateKey(new Date(sess.date));
    if (!byDay.has(key)) byDay.set(key, []);
    byDay.get(key)!.push({kind: 'standalone', session: sess});
  }

  for (const cs of clubSessions) {
    if (!cs.scheduledAt) continue;
    const key = cs.scheduledAt.split('T')[0];
    if (!byDay.has(key)) byDay.set(key, []);
    const linkedSession = sessionByClubSessionId.get(cs.id);
    byDay.get(key)!.push({kind: 'club-session', clubSession: cs, linkedSession});
  }

  return byDay;
}

export function computeBannerSegments(plans: TrainingPlan[], monthDays: CalendarDay[]): BannersByRow {
  if (!monthDays.length) return new Map();

  const result: BannersByRow = new Map();
  const eligible = plans.filter(
    p => (p.status === 'ACTIVE' || p.status === 'COMPLETED' || p.status === 'PAUSED') && p.startDate,
  );

  for (const plan of eligible) {
    const planStart = new Date(plan.startDate);
    const planEnd = new Date(planStart);
    planEnd.setDate(planStart.getDate() + plan.durationWeeks * 7 - 1);

    for (let row = 0; row < 6; row++) {
      const rowStart = monthDays[row * 7].date;
      const rowEnd = monthDays[row * 7 + 6].date;
      if (planStart > rowEnd || planEnd < rowStart) continue;

      const segStart = planStart > rowStart ? planStart : rowStart;
      const segEnd = planEnd < rowEnd ? planEnd : rowEnd;

      const startCol = Math.round((segStart.getTime() - rowStart.getTime()) / MS_PER_DAY) + 1;
      const spanCols = Math.round((segEnd.getTime() - segStart.getTime()) / MS_PER_DAY) + 1;

      const isStart = planStart >= rowStart && planStart <= rowEnd;
      const isEnd = planEnd >= rowStart && planEnd <= rowEnd;

      const daysSincePlanStart = Math.floor((segStart.getTime() - planStart.getTime()) / MS_PER_DAY);
      const weekNumber = Math.floor(daysSincePlanStart / 7) + 1;
      const week = plan.weeks?.find(w => w.weekNumber === weekNumber);

      if (!result.has(row)) result.set(row, []);
      result.get(row)!.push({
        planId: plan.id,
        title: plan.title,
        sportType: plan.sportType,
        startCol,
        spanCols,
        row,
        isStart,
        isEnd,
        weekNumber,
        weekLabel: week?.label ?? null,
      });
    }
  }
  return result;
}

export function computeVisiblePlans(
  plans: TrainingPlan[],
  viewStart: Date,
  viewEnd: Date,
): VisiblePlan[] {
  if (!viewStart || !viewEnd) return [];

  return plans
    .filter(p => p.status === 'ACTIVE' || p.status === 'COMPLETED' || p.status === 'PAUSED')
    .filter(p => {
      if (!p.startDate) return false;
      const planStart = new Date(p.startDate);
      const planEnd = new Date(planStart);
      planEnd.setDate(planEnd.getDate() + p.durationWeeks * 7 - 1);
      return planStart <= viewEnd && planEnd >= viewStart;
    })
    .map(p => {
      const planStart = new Date(p.startDate);
      const daysSinceStart = Math.floor((viewStart.getTime() - planStart.getTime()) / MS_PER_DAY);
      const currentWeek = Math.max(1, Math.min(p.durationWeeks, Math.floor(daysSinceStart / 7) + 1));
      const week = p.weeks?.find(w => w.weekNumber === currentWeek);
      return {
        id: p.id,
        title: p.title,
        currentWeek,
        totalWeeks: p.durationWeeks,
        weekLabel: week?.label ?? null,
        sportType: p.sportType,
      };
    });
}

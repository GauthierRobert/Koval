/**
 * Canonical date helpers for the calendar / scheduling feature surface.
 * Avoid re-implementing these in feature folders — extend this file instead.
 */

export const DAYS_IN_WEEK = 7;
export const MS_PER_DAY = 86_400_000;

/** Format a Date as the `yyyy-MM-dd` string used as a calendar grouping key. */
export function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Date key for `today + daysOffset` (negative for past, positive for future). */
export function dateOffsetKey(daysOffset: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysOffset);
  return toDateKey(d);
}

/** Returns the Monday (00:00:00 local) of the ISO week containing `d`. */
export function getMonday(d: Date): Date {
  const day = d.getDay();
  const offset = day === 0 ? -6 : 1 - day;
  const m = new Date(d);
  m.setDate(d.getDate() + offset);
  m.setHours(0, 0, 0, 0);
  return m;
}

/** Returns the Sunday of the ISO week containing `d` (00:00:00 local). */
export function getSunday(d: Date): Date {
  const mon = getMonday(d);
  const sun = new Date(mon);
  sun.setDate(mon.getDate() + 6);
  return sun;
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

export function isToday(day: Date): boolean {
  return isSameDay(day, new Date());
}

/** "Mon, Mar 5" — short weekday + short month + numeric day. */
export function formatDayHeader(day: Date): string {
  return day.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

/** "Mar 3 - Mar 9" given the Monday of the week. */
export function formatWeekRange(weekStart: Date): string {
  const end = new Date(weekStart.getTime() + 6 * MS_PER_DAY);
  const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
  return `${weekStart.toLocaleDateString('en-US', opts)} - ${end.toLocaleDateString('en-US', opts)}`;
}

/** "Mar 3 – Mar 9" with en-dash separator (used in coach schedule labels). */
export function formatWeekRangeDash(start: Date, end: Date): string {
  const opts: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
  return `${start.toLocaleDateString('en-US', opts)} – ${end.toLocaleDateString('en-US', opts)}`;
}

/** Add `weeks` to a week-start Date and return a new Date. */
export function shiftWeek(weekStart: Date, weeks: number): Date {
  return new Date(weekStart.getTime() + weeks * 7 * MS_PER_DAY);
}

/** Build a 7-day array starting from `weekStart` (typically a Monday). */
export function buildWeekDays(weekStart: Date): Date[] {
  const days: Date[] = [];
  for (let i = 0; i < DAYS_IN_WEEK; i++) {
    days.push(new Date(weekStart.getTime() + i * MS_PER_DAY));
  }
  return days;
}

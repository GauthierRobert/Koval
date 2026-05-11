import {ClubTrainingSession} from '../../../../../../services/club.service';

const DAY_MS = 86_400_000;

export function getMonday(d: Date): Date {
  const date = new Date(d);
  const day = date.getDay();
  const diff = date.getDate() - day + (day === 0 ? -6 : 1);
  date.setDate(diff);
  date.setHours(0, 0, 0, 0);
  return date;
}

export function buildWeekDays(weekStart: Date): Date[] {
  const days: Date[] = [];
  for (let i = 0; i < 7; i++) {
    days.push(new Date(weekStart.getTime() + i * DAY_MS));
  }
  return days;
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

export function formatDayHeader(day: Date): string {
  return day.toLocaleDateString('en-US', {weekday: 'short', month: 'short', day: 'numeric'});
}

export function formatWeekRange(weekStart: Date): string {
  const end = new Date(weekStart.getTime() + 6 * DAY_MS);
  const opts: Intl.DateTimeFormatOptions = {month: 'short', day: 'numeric'};
  return `${weekStart.toLocaleDateString('en-US', opts)} - ${end.toLocaleDateString('en-US', opts)}`;
}

export function shiftWeek(weekStart: Date, weeks: number): Date {
  return new Date(weekStart.getTime() + weeks * 7 * DAY_MS);
}

export function getSessionsForDay(
  sessions: ClubTrainingSession[],
  day: Date,
): ClubTrainingSession[] {
  return sessions
    .filter((s) => {
      if (!s.scheduledAt) return false;
      return isSameDay(new Date(s.scheduledAt), day);
    })
    .sort((a, b) => {
      const aRecurring = !!a.recurringTemplateId;
      const bRecurring = !!b.recurringTemplateId;
      if (aRecurring !== bRecurring) return aRecurring ? -1 : 1;
      return (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? '');
    });
}

import { ClubTrainingSession } from '../../../../../../services/club.service';
import { isSameDay } from '../../../../../../utils/date.utils';

export {
  getMonday,
  buildWeekDays,
  isSameDay,
  isToday,
  formatDayHeader,
  formatWeekRange,
  shiftWeek,
} from '../../../../../../utils/date.utils';

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

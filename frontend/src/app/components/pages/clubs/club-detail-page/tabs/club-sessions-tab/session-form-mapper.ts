import {
  CreateRecurringSessionData,
  CreateSessionData,
} from '../../../../../../services/club.service';

export type SessionFormPayload = Record<string, any>;

export const DAYS_OF_WEEK = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

function commonSessionFields(form: SessionFormPayload) {
  return {
    title: form['title'],
    sport: form['sport'],
    location: form['location'] || undefined,
    meetingPointLat: form['meetingPointLat'] ?? undefined,
    meetingPointLon: form['meetingPointLon'] ?? undefined,
    description: form['description'] || undefined,
    maxParticipants: form['maxParticipants'] || undefined,
    clubGroupId: form['clubGroupId'] || undefined,
    responsibleCoachId: form['responsibleCoachId'] || undefined,
    openToAll: form['openToAll'] ?? false,
    openToAllDelayValue: form['openToAll'] ? form['openToAllDelayValue'] : undefined,
    openToAllDelayUnit: form['openToAll'] ? form['openToAllDelayUnit'] : undefined,
  };
}

export function toSessionDto(
  form: SessionFormPayload,
  category: 'SCHEDULED' | 'OPEN',
): CreateSessionData {
  return {
    category,
    ...commonSessionFields(form),
    scheduledAt: form['scheduledAt'] || undefined,
    durationMinutes: form['durationMinutes'] || undefined,
  };
}

export function toRecurringSessionDto(form: SessionFormPayload): CreateRecurringSessionData {
  return {
    category: 'SCHEDULED',
    ...commonSessionFields(form),
    dayOfWeek: form['dayOfWeek'],
    timeOfDay: form['timeOfDay'],
    endDate: form['endDate'] || undefined,
  };
}

/** Recurring template DTO derived from a single edit form (uses scheduledAt to compute day/time). */
export function toRecurringSessionDtoFromEdit(form: SessionFormPayload): CreateRecurringSessionData {
  const scheduledAt: Date | null = form['scheduledAt'] ? new Date(form['scheduledAt']) : null;
  return {
    category: 'SCHEDULED',
    ...commonSessionFields(form),
    dayOfWeek: scheduledAt ? DAYS_OF_WEEK[(scheduledAt.getDay() + 6) % 7] : (undefined as any),
    timeOfDay: scheduledAt ? scheduledAt.toTimeString().slice(0, 5) : (undefined as any),
    endDate: form['endDate'] || undefined,
  };
}

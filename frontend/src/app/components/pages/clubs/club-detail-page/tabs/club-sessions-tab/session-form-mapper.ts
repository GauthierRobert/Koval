import {
  CreateRecurringSessionData,
  CreateSessionData,
} from '../../../../../../services/club.service';

/**
 * Open-ended form-record passed between the session modals and their mappers.
 * Kept as `Record<string, any>` (rather than `unknown`) so consumers can read
 * `form['title']`, `form['scheduledAt']` etc. without per-field casts. The
 * specific keys are validated structurally by `toSessionDto` / `toRecurringSessionDto`.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
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

/**
 * Recurring template DTO derived from a single edit form (uses scheduledAt to compute day/time).
 * Throws if `scheduledAt` is missing — backend requires both `dayOfWeek` and `timeOfDay`.
 */
export function toRecurringSessionDtoFromEdit(
  form: SessionFormPayload,
): CreateRecurringSessionData {
  if (!form['scheduledAt']) {
    throw new Error('toRecurringSessionDtoFromEdit requires form.scheduledAt');
  }
  const scheduledAt = new Date(form['scheduledAt']);
  return {
    category: 'SCHEDULED',
    ...commonSessionFields(form),
    dayOfWeek: DAYS_OF_WEEK[(scheduledAt.getDay() + 6) % 7],
    timeOfDay: scheduledAt.toTimeString().slice(0, 5),
    endDate: form['endDate'] || undefined,
  };
}

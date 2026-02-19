import { apiJson, apiFetch } from './api';
import type { ScheduledWorkout } from './calendarService';

export interface Athlete {
  id: string;
  displayName: string;
  profilePicture?: string;
  role: string;
  ftp?: number;
  tags: string[];
  hasCoach: boolean;
}

export interface AthleteMetrics {
  total: number;
  completed: number;
  skipped: number;
  pending: number;
  completionRate: number; // 0-100
}

/** Fetch all athletes for the logged-in coach. */
export async function fetchAthletes(): Promise<Athlete[]> {
  return apiJson<Athlete[]>('/api/coach/athletes');
}

/** Fetch one athlete's schedule for a date range. */
export async function fetchAthleteSchedule(
  athleteId: string,
  start: Date,
  end: Date
): Promise<ScheduledWorkout[]> {
  const fmt = (d: Date) => d.toISOString().split('T')[0];
  return apiJson<ScheduledWorkout[]>(
    `/api/coach/schedule/${athleteId}?start=${fmt(start)}&end=${fmt(end)}`
  );
}

/** Compute summary metrics from a list of scheduled workouts. */
export function computeMetrics(workouts: ScheduledWorkout[]): AthleteMetrics {
  const total = workouts.length;
  const completed = workouts.filter(w => w.status === 'COMPLETED').length;
  const skipped = workouts.filter(w => w.status === 'SKIPPED').length;
  const pending = workouts.filter(w => w.status === 'PENDING').length;
  const done = completed + skipped;
  const completionRate = done > 0 ? Math.round((completed / done) * 100) : 0;
  return { total, completed, skipped, pending, completionRate };
}

/** Mark an athlete's workout complete (coach side). */
export async function coachMarkCompleted(id: string): Promise<ScheduledWorkout> {
  return apiJson<ScheduledWorkout>(`/api/coach/schedule/${id}/complete`, { method: 'POST' });
}

/** Mark an athlete's workout skipped (coach side). */
export async function coachMarkSkipped(id: string): Promise<ScheduledWorkout> {
  return apiJson<ScheduledWorkout>(`/api/coach/schedule/${id}/skip`, { method: 'POST' });
}

/** Remove an athlete from the coach's roster. */
export async function removeAthlete(athleteId: string): Promise<void> {
  const res = await apiFetch(`/api/coach/athletes/${athleteId}`, { method: 'DELETE' });
  if (!res.ok && res.status !== 204) throw new Error('Failed to remove athlete');
}

import { apiJson, apiFetch } from './api';

export type ScheduleStatus = 'PENDING' | 'COMPLETED' | 'SKIPPED';
export type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';

export interface ScheduledWorkout {
  id: string;
  trainingId: string;
  athleteId: string;
  scheduledDate: string; // ISO date: YYYY-MM-DD
  status: ScheduleStatus;
  notes?: string;
  tss?: number;
  intensityFactor?: number;
  completedAt?: string;
  createdAt?: string;
  // enriched fields
  trainingTitle?: string;
  trainingType?: string;
  totalDurationSeconds?: number;
  sportType?: SportType;
}

/** Fetch scheduled workouts for a date range. */
export async function fetchSchedule(start: Date, end: Date): Promise<ScheduledWorkout[]> {
  const fmt = (d: Date) => d.toISOString().split('T')[0];
  return apiJson<ScheduledWorkout[]>(
    `/api/schedule?start=${fmt(start)}&end=${fmt(end)}`
  );
}

/** Mark a scheduled workout as completed. */
export async function markCompleted(id: string): Promise<ScheduledWorkout> {
  return apiJson<ScheduledWorkout>(`/api/schedule/${id}/complete`, { method: 'POST' });
}

/** Mark a scheduled workout as skipped. */
export async function markSkipped(id: string): Promise<ScheduledWorkout> {
  return apiJson<ScheduledWorkout>(`/api/schedule/${id}/skip`, { method: 'POST' });
}

/** Delete a scheduled workout. */
export async function deleteScheduledWorkout(id: string): Promise<void> {
  const res = await apiFetch(`/api/schedule/${id}`, { method: 'DELETE' });
  if (!res.ok && res.status !== 204) {
    throw new Error(`Delete failed: ${res.status}`);
  }
}

export type PlanSportType = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';

export type PlanStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface PlanDay {
  dayOfWeek: DayOfWeek;
  trainingId?: string;
  notes?: string;
  scheduledWorkoutId?: string;
}

export interface PlanWeek {
  weekNumber: number;
  label?: string;
  targetTss?: number;
  days: PlanDay[];
}

export interface TrainingPlan {
  id: string;
  title: string;
  description?: string;
  sportType: PlanSportType;
  createdBy: string;
  startDate: string; // YYYY-MM-DD
  durationWeeks: number;
  status: PlanStatus;
  weeks: PlanWeek[];
  goalRaceId?: string;
  targetFtp?: number;
  athleteIds: string[];
  createdAt: string;
  activatedAt?: string;
}

export interface PlanProgress {
  planId: string;
  totalWorkouts: number;
  completedWorkouts: number;
  skippedWorkouts: number;
  pendingWorkouts: number;
  completionPercent: number;
  currentWeek: number;
}

export interface PlanWeekAnalytics {
  weekNumber: number;
  label: string | null;
  targetTss: number | null;
  actualTss: number;
  workoutsCompleted: number;
  workoutsTotal: number;
  adherencePercent: number;
}

export interface PlanAnalytics {
  planId: string;
  planTitle: string;
  status: string;
  currentWeek: number;
  totalWeeks: number;
  overallCompletionPercent: number;
  overallAdherencePercent: number;
  totalTargetTss: number;
  totalActualTss: number;
  weeklyBreakdown: PlanWeekAnalytics[];
}

export interface ActivePlanSummary {
  planId: string;
  title: string;
  status: string;
  currentWeek: number;
  totalWeeks: number;
  weekLabel: string | null;
  completionPercent: number;
  weekWorkoutsRemaining: number;
  weekTargetTss: number | null;
  weekActualTss: number | null;
}

export const SPORT_BANNER_COLORS: Record<string, { bg: string; border: string; text: string }> = {
  CYCLING:  { bg: 'rgba(34, 197, 94, 0.15)', border: '#22c55e', text: '#22c55e' },
  RUNNING:  { bg: 'rgba(239, 68, 68, 0.15)', border: '#ef4444', text: '#ef4444' },
  SWIMMING: { bg: 'rgba(59, 130, 246, 0.15)', border: '#3b82f6', text: '#3b82f6' },
  BRICK:    { bg: 'rgba(255, 157, 0, 0.15)', border: '#ff9d00', text: '#ff9d00' },
};

export const PLAN_STATUS_COLORS: Record<PlanStatus, string> = {
  DRAFT: '#6b7280',
  ACTIVE: '#22c55e',
  PAUSED: '#f59e0b',
  COMPLETED: '#3b82f6',
  CANCELLED: '#ef4444',
};

export const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
};

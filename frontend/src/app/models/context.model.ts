import { Provenance } from './ai-analysis.model';

/** Section title → markdown content. */
export type ContextSections = Record<string, string>;

/** The caller's own context (athlete self-context, or coach philosophy). */
export interface MyContext {
  role: 'ATHLETE' | 'COACH';
  sections: ContextSections | null;
  provenance?: Provenance | null;
  updatedAt?: string | null;
}

/** A single stored context document. */
export interface ContextEntry {
  sections: ContextSections | null;
  provenance?: Provenance | null;
  updatedAt?: string | null;
}

/** A coach's view of an athlete: the athlete's self-context plus the coach's private notes. */
export interface CoachAthleteContext {
  athleteSelf: ContextEntry | null;
  coachContext: ContextEntry | null;
}

/** Recommended section headings, aligned with the onboarding skill templates. */
export const ATHLETE_CONTEXT_SECTIONS: readonly string[] = [
  'Identity',
  'Goals',
  'Weekly availability',
  'Workout style',
  'Body, recovery & constraints',
  'Targets & data',
  'Voice & communication',
];

export const COACH_PHILOSOPHY_SECTIONS: readonly string[] = [
  'Coaching philosophy',
  'Volume & week shape',
  'Workout structure',
  'Targets & zones',
  'Recovery & monitoring',
  'Voice & communication',
];

export const COACH_ATHLETE_CONTEXT_SECTIONS: readonly string[] = [
  'Plan for this athlete',
  'Strengths & limiters',
  'Constraints to respect',
  'Notes',
];

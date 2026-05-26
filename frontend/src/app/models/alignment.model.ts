/**
 * How a completed session aligned with the workout that was scheduled for it, as a percentage:
 * 100 = on plan, above = exceeded the scheduled effort, below = fell short. Two independent,
 * optional ratings live side by side — the athlete's self-assessment and the coach/AI assessment.
 */
export interface AlignmentScore {
  athleteScore?: number | null;
  athleteNote?: string | null;
  athleteSetAt?: string | null;
  coachScore?: number | null;
  coachNote?: string | null;
  coachSource?: 'coach' | 'ai' | null;
  coachSetAt?: string | null;
}

/** One dimension of the deterministic estimate. */
export interface AlignmentEstimateFactor {
  name: 'tss' | 'if' | 'duration' | 'blockPower';
  weight: number;
  planned: number | null;
  actual: number | null;
  ratioPercent: number;
}

/** Deterministic suggestion returned by `GET /api/sessions/{id}/alignment/estimate`. */
export interface AlignmentEstimate {
  score: number;
  factors: AlignmentEstimateFactor[];
}

/** One point on the alignment evolution chart. */
export interface AlignmentHistoryPoint {
  sessionId: string;
  date: string;
  title: string;
  sportType: string | null;
  athleteScore: number | null;
  coachScore: number | null;
  effectiveScore: number;
}

/** Score-zone classification shared by badge and chart. */
export type AlignmentZone = 'green' | 'red';

/** Outside 90–110% is the "red" (off-target) zone; inside is "green" (on-target). */
export function alignmentZone(score: number): AlignmentZone {
  return score > 110 || score < 90 ? 'red' : 'green';
}

/** Coach rating wins when present; otherwise the athlete's. Null when neither is set. */
export function effectiveAlignmentScore(a: AlignmentScore | null | undefined): number | null {
  if (!a) return null;
  return a.coachScore ?? a.athleteScore ?? null;
}

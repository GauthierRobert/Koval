/**
 * TypeScript interfaces for training session and athlete data
 * Replaces scattered 'any' types with proper type definitions
 */

export interface SessionData {
  id: string;
  sportType: string;
  duration: number;
  distance?: number;
  avgPower?: number;
  avgHeartRate?: number;
  avgCadence?: number;
  timestamp: string;
}

export interface AthleteSessions extends Array<SessionData> {}

export interface SessionSummary {
  sport: string;
  count: number;
  pct: number;
}

export interface SessionState {
  isActive: boolean;
  currentPhaseIndex: number;
  elapsedSeconds: number;
  totalDuration: number;
  currentPower: number;
  currentHeartRate: number;
}

export interface LiveMetrics {
  power: number;
  heartRate: number;
  cadence: number;
  speed: number;
  distance: number;
  time: number;
}

export interface TrainingSession {
  id: string;
  trainingId: string;
  athleteId: string;
  startTime: string;
  endTime?: string;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED';
  metrics: LiveMetrics;
}

export interface AthleteSessionData {
  id: string;
  athleteId: string;
  sessions: SessionData[];
  summary: SessionSummary[];
}

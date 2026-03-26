export type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING';

export type ZoneReferenceType =
  | 'FTP'
  | 'VO2MAX_POWER'
  | 'THRESHOLD_PACE'
  | 'VO2MAX_PACE'
  | 'CSS'
  | 'PACE_5K'
  | 'PACE_10K'
  | 'PACE_HALF_MARATHON'
  | 'PACE_MARATHON'
  | 'CUSTOM';

export interface Zone {
  label: string;
  low: number;
  high: number;
  description?: string;
}

export interface ZoneSystem {
  id?: string;
  name: string;
  coachId: string;
  sportType: SportType;
  referenceType: ZoneReferenceType;
  referenceName?: string;
  referenceUnit?: string;
  zones: Zone[];
  defaultForSport?: boolean;
  annotations?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ZoneBlock {
  zoneIndex: number;
  zoneLabel: string;
  zoneDescription: string;
  color: string;
  startIndex: number;
  endIndex: number;
  durationSeconds: number;
  distanceMeters: number;
  avgPower: number;
  maxPower: number;
  avgSpeed: number;
  maxSpeed: number;
  avgHR: number;
  avgCadence: number;
  avgPercent: number;
}
